package icu.tatyrealms.tatykefei.body;

import icu.tatyrealms.tatykefei.state.EmptyBodyHolder;
import icu.tatyrealms.tatykefei.state.Form;
import icu.tatyrealms.tatykefei.config.TSFServerConfig;
import icu.tatyrealms.tatykefei.block.BodyDisplayBlock;
import icu.tatyrealms.tatykefei.block.BodyDisplayBlockEntity;
import icu.tatyrealms.tatykefei.network.BodyDragPayload;
import icu.tatyrealms.tatykefei.network.BodyDisplayPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.slf4j.LoggerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class BodyRigManager implements AutoCloseable {
    private static final int MAX_SUBMISSIONS_PER_TICK = 32;
    private static final long SNAPSHOT_BUDGET_NANOS = 1_500_000;
    private static final double SLEEP_DISPLACEMENT_SQUARED = 0.002 * 0.002;
    private static final int SLEEP_SAMPLES = 20;
    private final BodyManager manager;
    private final Map<UUID, Simulation> simulations = new HashMap<>();
    private final AsyncBodyPhysics worker = new AsyncBodyPhysics();
    private final BodyCollisionCache collisions = new BodyCollisionCache();
    private final Map<UUID, Grab> grabs = new HashMap<>();
    private final Map<UUID, DisplaySession> menus = new HashMap<>();
    private final Map<UUID, DisplaySession> editors = new HashMap<>();
    private long time;
    private boolean physicsEnabled = TSFServerConfig.get().bodyLimbPhysics;

    private record DisplaySession(ServerLevel level, BlockPos pos, UUID body) {
    }

    private static final class Simulation {
        final Entity entity;
        BodyRigPhysics physics = new BodyRigPhysics();
        BodyCollisionCache.Snapshot snapshot;
        Future<AsyncBodyPhysics.Result> pending;
        BodyRigPose input;
        Vec3 submittedPosition;
        Vec3 observedPosition;
        Grab submittedGrab;
        boolean onGround;
        boolean sleeping;
        boolean paused;
        int quietSamples;
        int interval = 1;
        long nextStep;
        long submittedAt;
        long lastSync;

        Simulation(Entity entity) {
            this.entity = entity;
            observedPosition = entity.position();
            onGround = entity.onGround();
        }

        void cancel() {
            if (pending != null) {
                pending.cancel(true);
                pending = null;
            }
            input = null;
        }

        void wake(BodyRigPose pose) {
            sleeping = false;
            quietSamples = 0;
            nextStep = 0;
            physics.reset(pose, entity.position());
        }
    }

    private record Candidate(Body body, Simulation simulation, Grab grab, Vec3 target) {
    }

    private static final class Grab {
        UUID player;
        Body body;
        Entity entity;
        int part;
        float distance;
        long updated;
        Vec3 offset;
    }

    public BodyRigManager(BodyManager manager) {
        this.manager = manager;
    }

    private static boolean allowed(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && !BodyManager.hypnotized(player)
                && BodyManager.form(player) == Form.HUMAN;
    }

    private Body displayed(ServerLevel level, BlockPos pos, Collection<Body> bodies) {
        String dimension = level.dimension().identifier().toString();
        return bodies.stream().filter(body -> body.displayMounted && body.displayPosition == pos.asLong()
                && dimension.equals(body.dimension)).findFirst().orElse(null);
    }

    private boolean canConfigure(ServerPlayer player, BlockPos pos) {
        if (!allowed(player) || player.distanceToSqr(Vec3.atCenterOf(pos)) > 36
                || !player.getAbilities().mayBuild || !player.level().mayInteract(player, pos)
                || !player.level().getBlockState(pos).is(BodyDisplayBlock.INSTANCE)) {
            return false;
        }
        var hit = player.level().clip(new ClipContext(player.getEyePosition(), Vec3.atCenterOf(pos),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }

    private BodyDisplayBlockEntity settings(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof BodyDisplayBlockEntity)) {
            level.setBlockEntity(new BodyDisplayBlockEntity(pos, level.getBlockState(pos)));
        }
        return (BodyDisplayBlockEntity) level.getBlockEntity(pos);
    }

    public void openSettings(ServerPlayer player, BlockPos pos, Collection<Body> bodies) {
        if (!canConfigure(player, pos)) {
            return;
        }
        endEditing(player);
        Body body = displayed(player.level(), pos, bodies);
        menus.put(player.getUUID(), new DisplaySession(player.level(), pos.immutable(), body == null ? null : body.id));
        sendSettings(player, pos, body, BodyDisplayPayload.OPEN);
    }

    private void sendSettings(ServerPlayer player, BlockPos pos, Body body, int operation) {
        var settings = settings(player.level(), pos);
        Entity entity = body == null || body.entityId == null ? null : player.level().getEntity(body.entityId);
        ServerPlayNetworking.send(player, new BodyDisplayPayload(pos, operation, settings.showName(),
                settings.poseable(), settings.showCape(), entity == null ? -1 : entity.getId()));
    }

    public void configure(ServerPlayer player, BodyDisplayPayload payload, Collection<Body> bodies) {
        if (payload.operation() == BodyDisplayPayload.STOP_EDIT) {
            endEditing(player);
            return;
        }
        DisplaySession session = menus.get(player.getUUID());
        if (session == null || session.level != player.level() || !session.pos.equals(payload.pos())) {
            return;
        }
        if (payload.operation() == BodyDisplayPayload.CLOSE) {
            menus.remove(player.getUUID());
            return;
        }
        if (!canConfigure(player, session.pos)) {
            menus.remove(player.getUUID());
            return;
        }
        Body body = displayed(player.level(), session.pos, bodies);
        var settings = settings(player.level(), session.pos);
        if (payload.operation() == BodyDisplayPayload.UPDATE) {
            settings.configure(payload.showName(), payload.poseable(), payload.showCape());
            if (body != null) {
                if (!settings.poseable()) {
                    endEditors(body);
                }
                Entity entity = body.entityId == null ? null : player.level().getEntity(body.entityId);
                if (entity != null) {
                    prepare(body, entity);
                }
            }
        } else if (body != null && body.id.equals(session.body)) {
            if (payload.operation() == BodyDisplayPayload.RESET && settings.poseable()) {
                endEditors(body);
                body.rigPose = BodyRigPose.create(true, "ceiling".equals(body.displayFace), body.yaw);
                Entity entity = body.entityId == null ? null : player.level().getEntity(body.entityId);
                if (entity != null) {
                    synchronizePose(body, entity);
                }
                manager.rigChanged();
            } else if (payload.operation() == BodyDisplayPayload.EDIT && settings.poseable() && player.getMainHandItem().isEmpty()) {
                endEditing(player);
                editors.put(player.getUUID(), session);
                menus.remove(player.getUUID());
                sendSettings(player, session.pos, body, BodyDisplayPayload.BEGIN_EDIT);
                return;
            }
        }
        sendSettings(player, session.pos, body, BodyDisplayPayload.SNAPSHOT);
    }

    private void endEditors(Body body) {
        for (UUID id : List.copyOf(editors.keySet())) {
            DisplaySession session = editors.get(id);
            if (body.id.equals(session.body)) {
                ServerPlayer player = session.level.getServer().getPlayerList().getPlayer(id);
                if (player != null) {
                    endEditing(player);
                } else {
                    editors.remove(id);
                    grabs.remove(id);
                }
            }
        }
        discardSimulation(body.id);
    }

    private void endEditing(ServerPlayer player) {
        DisplaySession session = editors.remove(player.getUUID());
        Grab grab = grabs.remove(player.getUUID());
        if (grab != null) {
            discardSimulation(grab.body.id);
            manager.rigChanged();
        }
        if (session != null) {
            ServerPlayNetworking.send(player, new BodyDisplayPayload(session.pos, BodyDisplayPayload.END_EDIT, false, false, true, -1));
        }
    }

    public boolean pickUpDisplayed(ServerPlayer player, Entity entity) {
        Body body = manager.bodyForEntity(entity);
        if (body == null || !body.displayMounted) {
            return false;
        }
        BlockPos pos = BlockPos.of(body.displayPosition);
        if (player.isShiftKeyDown() && canConfigure(player, pos) && player.distanceToSqr(entity) <= 25
                && manager.canSeeEmptyBody(player, body, entity)) {
            manager.pickUpBody(player, body);
        }
        return true;
    }

    public void pickUpDisplayed(ServerPlayer player, BlockPos pos, Collection<Body> bodies) {
        if (player.isShiftKeyDown() && canConfigure(player, pos)) {
            Body body = displayed(player.level(), pos, bodies);
            if (body != null) {
                manager.pickUpBody(player, body);
            }
        }
    }

    public void interactDisplay(ServerPlayer player, BlockPos pos, InteractionHand hand, Collection<Body> bodies) {
        if (!player.isAlive() || player.isSpectator() || BodyManager.hypnotized(player)
                || player.distanceToSqr(Vec3.atCenterOf(pos)) > 36
                || !player.level().mayInteract(player, pos) || !player.getAbilities().mayBuild) {
            return;
        }
        BlockState state = player.level().getBlockState(pos);
        if (!state.is(BodyDisplayBlock.INSTANCE)) {
            return;
        }
        if (hand == InteractionHand.MAIN_HAND && player.isShiftKeyDown()) {
            openSettings(player, pos, bodies);
            return;
        }
        Body occupied = displayed(player.level(), pos, bodies);
        if (occupied != null) {
            if (hand != InteractionHand.MAIN_HAND || manager.interactDisplayedBody(player, occupied)
                    || !allowed(player)) {
                return;
            }
            BodyManager.message(player, "body_display_edit");
            return;
        }
        if (!allowed(player)) {
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        Body body = manager.packedBody(stack);
        if (body == null) {
            BodyManager.message(player, "body_display_insert");
            return;
        }
        AttachFace face = state.getValue(BodyDisplayBlock.FACE);
        Direction facing = state.getValue(BodyDisplayBlock.FACING);
        Vec3 origin = Vec3.atBottomCenterOf(pos);
        if (face == AttachFace.FLOOR) {
            origin = origin.add(0, 0.13, 0);
        } else if (face == AttachFace.CEILING) {
            origin = origin.add(0, -0.6, 0);
        } else {
            origin = origin.add(facing.getStepX() * 0.15, -0.25, facing.getStepZ() * 0.15);
        }
        BodyRigPose pose = BodyRigPose.create(true, face == AttachFace.CEILING, facing.toYRot());
        for (int part = 0; part < 6; part++) {
            Vec3 center = origin.add(pose.part(part).center());
            if (!player.level().getWorldBorder().isWithinBounds(BlockPos.containing(center))
                    || !clearOfBlocks(player.level(), pos, center, part < 2 ? 0.18 : 0.09)) {
                BodyManager.message(player, "no_space");
                return;
            }
        }
        UUID token = body.packedToken;
        UUID previousEntity = body.entityId;
        String dimension = body.dimension;
        Vec3 previousPosition = new Vec3(body.x, body.y, body.z);
        float previousYaw = body.yaw;
        BodyRigPose previousPose = body.rigPose;
        body.entityId = null;
        body.displayMounted = true;
        body.displayPosition = pos.asLong();
        body.displayFace = face.getSerializedName();
        body.dimension = player.level().dimension().identifier().toString();
        body.x = origin.x;
        body.y = origin.y;
        body.z = origin.z;
        body.yaw = facing.toYRot();
        body.rigPose = pose;
        body.packedToken = null;
        manager.spawnBody(body);
        if (body.entityId == null) {
            body.displayMounted = false;
            body.packedToken = token;
            body.entityId = previousEntity;
            body.dimension = dimension;
            body.x = previousPosition.x;
            body.y = previousPosition.y;
            body.z = previousPosition.z;
            body.yaw = previousYaw;
            body.rigPose = previousPose;
            body.displayFace = null;
            return;
        }
        stack.shrink(1);
        player.setItemInHand(hand, stack.copy());
        BodyManager.message(player, "body_display_edit");
        manager.rigChanged();
    }

    private static boolean clearOfBlocks(ServerLevel level, BlockPos stand, Vec3 point, double radius) {
        var bounds = new net.minecraft.world.phys.AABB(point, point).inflate(radius);
        for (BlockPos block : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))) {
            if (!block.equals(stand) && !level.getBlockState(block).getCollisionShape(level, block).isEmpty()
                    && level.getBlockState(block).getCollisionShape(level, block).toAabbs().stream()
                    .anyMatch(box -> box.move(block).intersects(bounds))) {
                return false;
            }
        }
        return true;
    }

    public void removeDisplay(ServerLevel level, BlockPos pos, Collection<Body> bodies) {
        Body body = displayed(level, pos, bodies);
        if (body == null) {
            return;
        }
        manager.dropDisplayBody(level, body);
    }

    public void prepare(Body body, Entity entity) {
        if (!(entity instanceof EmptyBodyHolder holder)) {
            return;
        }
        if (body.skinPlaced) {
            holder.tsf$setRigPose("");
            return;
        }
        ensurePose(body);
        holder.tsf$setRigPose(body.displayMounted || TSFServerConfig.get().bodyLimbPhysics ? body.rigPose.encode() : "");
        if (body.displayMounted) {
            var options = settings((ServerLevel) entity.level(), BlockPos.of(body.displayPosition));
            holder.tsf$setDisplayOptions(options.showName(), options.poseable(), options.showCape());
        }
    }

    private static void ensurePose(Body body) {
        if (body.rigPose == null || !body.rigPose.valid() || body.rigPose.mounted != body.displayMounted) {
            body.rigPose = BodyRigPose.create(body.displayMounted, "ceiling".equals(body.displayFace), body.yaw);
        }
    }

    public void forget(Body body) {
        endEditors(body);
        discardSimulation(body.id);
        grabs.entrySet().removeIf(entry -> entry.getValue().body == body);
        body.displayMounted = false;
        body.displayFace = null;
        body.rigPose = null;
    }

    public void drag(ServerPlayer player, BodyDragPayload payload) {
        if (payload.operation() == BodyDragPayload.STOP) {
            Grab ended = grabs.remove(player.getUUID());
            if (ended != null) {
                discardSimulation(ended.body.id);
                synchronizePose(ended.body, ended.entity);
                manager.rigChanged();
            }
            return;
        }
        DisplaySession session = editors.get(player.getUUID());
        if (!allowed(player) || session == null || session.level != player.level()
                || !player.getMainHandItem().isEmpty()
                || !Float.isFinite(payload.distance())) {
            return;
        }
        Grab current = grabs.get(player.getUUID());
        if (payload.operation() == BodyDragPayload.UPDATE) {
            if (current != null && current.entity.getId() == payload.entity()) {
                current.distance = Math.clamp(payload.distance(), 1.0F, 5.0F);
                current.updated = time;
            }
            return;
        }
        if (payload.operation() != BodyDragPayload.START || current != null) {
            return;
        }
        Entity entity = player.level().getEntity(payload.entity());
        if (!(entity instanceof Mannequin) || !BodyManager.emptyBody(entity) || BodyManager.foldedSkin(entity)) {
            return;
        }
        Body body = manager.bodyForEntity(entity);
        if (body == null || !body.id.equals(session.body) || !body.displayMounted
                || !settings(player.level(), session.pos).poseable() || player.distanceToSqr(entity) > 49
                || !player.level().mayInteract(player, BlockPos.of(body.displayPosition)) || !player.getAbilities().mayBuild
                || grabs.values().stream().anyMatch(grab -> grab.body == body)) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(5));
        double nearest = Double.POSITIVE_INFINITY;
        int part = -1;
        for (int index = 0; index < 6; index++) {
            double distance = body.rigPose.part(index).hit(eye.subtract(entity.position()), end.subtract(entity.position()));
            if (distance < nearest) {
                nearest = distance;
                part = index;
            }
        }
        if (part < 0 || nearest > 5 || !visible(player, eye.add(player.getLookAngle().scale(nearest)))) {
            return;
        }
        Grab grab = new Grab();
        grab.player = player.getUUID();
        grab.body = body;
        grab.entity = entity;
        grab.part = part;
        grab.distance = (float) Math.clamp(nearest, 1, 5);
        grab.updated = time;
        grab.offset = body.rigPose.point(BodyRigPose.node(part)).subtract(eye.add(player.getLookAngle().scale(grab.distance)).subtract(entity.position()));
        grabs.put(player.getUUID(), grab);
        discardSimulation(body.id);
    }

    private static boolean visible(ServerPlayer player, Vec3 target) {
        HitResult hit = player.level().clip(new ClipContext(player.getEyePosition(), target,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(target) < 0.04;
    }

    public void blockChanged(ServerLevel level, BlockPos pos) {
        collisions.changed(level, pos);
    }

    private void discardSimulation(UUID body) {
        Simulation removed = simulations.remove(body);
        if (removed != null) {
            removed.cancel();
        }
    }

    private static void synchronizePose(Body body, Entity entity) {
        EmptyBodyHolder holder = (EmptyBodyHolder) entity;
        String encoded = body.rigPose.encode();
        if (!encoded.equals(holder.tsf$rigPose())) {
            holder.tsf$setRigPose(encoded);
            if (body.displayMounted) {
                entity.setBoundingBox(body.rigPose.bounds().move(entity.position()));
            }
        }
    }

    public void tick(MinecraftServer server, Map<UUID, Body> bodies, Map<UUID, Entity> entities) {
        time++;
        menus.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            return player == null || player.level() != entry.getValue().level || !canConfigure(player, entry.getValue().pos);
        });
        for (UUID id : List.copyOf(editors.keySet())) {
            DisplaySession session = editors.get(id);
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            Body body = bodies.get(session.body);
            if (player == null) {
                editors.remove(id);
            } else if (player.level() != session.level || !canConfigure(player, session.pos)
                    || body == null || !body.displayMounted || body.displayPosition != session.pos.asLong()
                    || !settings(session.level, session.pos).poseable()) {
                endEditing(player);
            }
        }
        boolean enabled = TSFServerConfig.get().bodyLimbPhysics;
        boolean configChanged = enabled != physicsEnabled;
        physicsEnabled = enabled;
        if (configChanged) {
            simulations.values().forEach(Simulation::cancel);
            simulations.clear();
        }
        grabs.entrySet().removeIf(entry -> {
            Grab grab = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            boolean invalid = player == null || !allowed(player) || !editors.containsKey(entry.getKey()) || !player.getMainHandItem().isEmpty()
                    || player.level() != grab.entity.level() || grab.entity.isRemoved() || !grab.body.displayMounted
                    || player.distanceToSqr(grab.entity) > 49 || time - grab.updated > 10;
            if (invalid) {
                discardSimulation(grab.body.id);
                manager.rigChanged();
            }
            return invalid;
        });
        Map<UUID, Grab> held = new HashMap<>();
        grabs.values().forEach(grab -> held.put(grab.body.id, grab));
        List<Candidate> candidates = new ArrayList<>();
        for (var entry : List.copyOf(entities.entrySet())) {
            Body body = bodies.get(entry.getKey());
            Entity entity = entry.getValue();
            if (body == null || entity.isRemoved() || body.skinPlaced || !entity.getUUID().equals(body.entityId)) {
                discardSimulation(entry.getKey());
                continue;
            }
            if (body.displayMounted) {
                BlockPos pos = BlockPos.of(body.displayPosition);
                if (!entity.level().getBlockState(pos).is(BodyDisplayBlock.INSTANCE)) {
                    removeDisplay((ServerLevel) entity.level(), pos, bodies.values());
                    continue;
                }
                // Fixed stands need no repeated position updates or bounding-box reconstruction.
                if (entity.position().distanceToSqr(new Vec3(body.x, body.y, body.z)) > 1.0E-8) {
                    entity.setPos(body.x, body.y, body.z);
                }
                if (!entity.getDeltaMovement().equals(Vec3.ZERO)) {
                    entity.setDeltaMovement(Vec3.ZERO);
                }
            }
            ensurePose(body);
            EmptyBodyHolder holder = (EmptyBodyHolder) entity;
            if (configChanged || holder.tsf$rigPose().isEmpty() && (body.displayMounted || enabled)) {
                prepare(body, entity);
            }
            Grab grab = held.get(body.id);
            if (body.displayMounted && grab == null || !body.displayMounted && !enabled) {
                discardSimulation(body.id);
                continue;
            }
            Simulation state = simulations.computeIfAbsent(body.id, ignored -> new Simulation(entity));
            if (state.entity != entity) {
                discardSimulation(body.id);
                continue;
            }
            boolean moved = state.observedPosition.distanceToSqr(entity.position()) > 0.000025
                    || state.onGround != entity.onGround();
            if (moved) {
                state.observedPosition = entity.position();
                state.onGround = entity.onGround();
                state.sleeping = false;
                state.quietSamples = 0;
                state.nextStep = Math.min(state.nextStep, time);
            }
            boolean changed = state.snapshot != null && !collisions.valid(state.snapshot, time);
            if (changed) {
                state.cancel();
                state.snapshot = null;
                state.wake(body.rigPose);
            }
            int interval = grab == null ? interval(server, entity) : 1;
            if (interval == 0) {
                state.cancel();
                state.paused = true;
                continue;
            }
            if (state.paused) {
                state.paused = false;
                state.wake(body.rigPose);
            }
            if (interval < state.interval) {
                state.nextStep = Math.min(state.nextStep, time);
            }
            state.interval = interval;
            collect(body, state, grab);
            if (!state.sleeping && state.pending == null && time >= state.nextStep) {
                Vec3 target = Vec3.ZERO;
                if (grab != null) {
                    ServerPlayer player = server.getPlayerList().getPlayer(grab.player);
                    Vec3 worldTarget = player.getEyePosition().add(player.getLookAngle().scale(grab.distance));
                    if (!visible(player, worldTarget)) {
                        state.cancel();
                        continue;
                    }
                    target = worldTarget.subtract(entity.position()).add(grab.offset);
                    if (target.length() > 3.5) {
                        target = target.normalize().scale(3.5);
                    }
                }
                candidates.add(new Candidate(body, state, grab, target));
            }
        }
        // The oldest due body goes first, so a large crowd cannot starve later entries.
        candidates.sort(Comparator.<Candidate>comparingInt(candidate -> candidate.grab == null ? 1 : 0)
                .thenComparingLong(candidate -> candidate.simulation.nextStep));
        long deadline = System.nanoTime() + SNAPSHOT_BUDGET_NANOS;
        int submitted = 0;
        int attempted = 0;
        int pending = (int) simulations.values().stream().filter(state -> state.pending != null).count();
        for (Candidate candidate : candidates) {
            if (submitted >= MAX_SUBMISSIONS_PER_TICK || pending >= AsyncBodyPhysics.MAX_PENDING
                    || attempted > 0 && System.nanoTime() >= deadline) {
                break;
            }
            attempted++;
            Body body = candidate.body;
            Simulation state = candidate.simulation;
            Entity entity = state.entity;
            Vec3 pelvis = entity.position().add(body.rigPose.point(0));
            if (state.snapshot == null || !state.snapshot.covers(pelvis)) {
                state.snapshot = collisions.capture((ServerLevel) entity.level(), pelvis,
                        body.displayMounted ? BlockPos.of(body.displayPosition) : null, time);
            }
            if (state.snapshot == null) {
                state.nextStep = time + 20;
                continue;
            }
            state.pending = worker.submit(body.rigPose, state.physics, entity.position(), state.snapshot.boxes,
                    candidate.grab == null ? -1 : candidate.grab.part, candidate.target, !body.displayMounted);
            if (state.pending == null) {
                break;
            }
            state.input = body.rigPose;
            state.submittedGrab = candidate.grab;
            state.submittedPosition = entity.position();
            state.submittedAt = time;
            state.nextStep = time + state.interval;
            submitted++;
            pending++;
        }
        simulations.entrySet().removeIf(entry -> {
            if (!entities.containsKey(entry.getKey())) {
                entry.getValue().cancel();
                return true;
            }
            return false;
        });
    }

    private static int interval(MinecraftServer server, Entity entity) {
        double nearest = Double.POSITIVE_INFINITY;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level() == entity.level() && !player.isSpectator()) {
                nearest = Math.min(nearest, player.distanceToSqr(entity));
            }
        }
        return nearest <= 32 * 32 ? 1 : nearest <= 64 * 64 ? 4 : nearest <= 96 * 96 ? 10 : 0;
    }

    private void collect(Body body, Simulation state, Grab grab) {
        if (state.pending == null) {
            return;
        }
        if (time - state.submittedAt > 10 || state.input != body.rigPose || state.submittedGrab != grab
                || state.submittedPosition.distanceToSqr(state.entity.position()) > 0.25 * 0.25) {
            state.cancel();
            state.wake(body.rigPose);
            return;
        }
        if (!state.pending.isDone()) {
            return; // Never wait for the worker on the game thread.
        }
        try {
            AsyncBodyPhysics.Result result = state.pending.get();
            if (!result.pose().valid()) {
                state.wake(body.rigPose);
                return;
            }
            body.rigPose = result.pose();
            state.physics = result.physics();
            if (grab == null && state.entity.onGround() && result.displacement() < SLEEP_DISPLACEMENT_SQUARED) {
                state.quietSamples++;
                state.sleeping = state.quietSamples >= SLEEP_SAMPLES;
            } else {
                state.quietSamples = 0;
            }
            if (grab != null || state.sleeping || time - state.lastSync >= 2) {
                synchronizePose(body, state.entity);
                state.lastSync = time;
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException exception) {
            LoggerFactory.getLogger("TSF").error("Empty body physics failed", exception.getCause());
            state.wake(body.rigPose);
            state.nextStep = time + 20;
        } catch (CancellationException exception) {
            state.wake(body.rigPose);
        } finally {
            state.pending = null;
            state.input = null;
        }
    }

    @Override
    public void close() {
        simulations.values().forEach(Simulation::cancel);
        simulations.clear();
        grabs.clear();
        menus.clear();
        editors.clear();
        collisions.clear();
        worker.close();
    }
}
