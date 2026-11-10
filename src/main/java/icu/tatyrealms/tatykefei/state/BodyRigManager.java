package icu.tatyrealms.tatykefei.state;

import icu.tatyrealms.tatykefei.TSFServerConfig;
import icu.tatyrealms.tatykefei.block.BodyDisplayBlock;
import icu.tatyrealms.tatykefei.mixin.MannequinAccessor;
import icu.tatyrealms.tatykefei.network.BodyDragPayload;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

public final class BodyRigManager {
    private final BodyManager manager;
    private final Map<UUID, BodyRigPhysics> simulations = new HashMap<>();
    private final Map<UUID, Grab> grabs = new HashMap<>();
    private long time;
    private boolean physicsEnabled = TSFServerConfig.get().bodyLimbPhysics;

    private static final class Grab {
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

    public void interactDisplay(ServerPlayer player, BlockPos pos, InteractionHand hand, Collection<Body> bodies) {
        if (!allowed(player) || player.distanceToSqr(Vec3.atCenterOf(pos)) > 36
                || !player.level().mayInteract(player, pos) || !player.getAbilities().mayBuild) {
            return;
        }
        BlockState state = player.level().getBlockState(pos);
        if (!state.is(BodyDisplayBlock.INSTANCE)) {
            return;
        }
        Body occupied = displayed(player.level(), pos, bodies);
        if (occupied != null) {
            if (player.isShiftKeyDown()) {
                manager.pickUpBody(player, occupied);
            } else {
                BodyManager.message(player, "body_display_edit");
            }
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
    }

    private static void ensurePose(Body body) {
        if (body.rigPose == null || !body.rigPose.valid() || body.rigPose.mounted != body.displayMounted) {
            body.rigPose = BodyRigPose.create(body.displayMounted, "ceiling".equals(body.displayFace), body.yaw);
        }
    }

    public void forget(Body body) {
        simulations.remove(body.id);
        grabs.entrySet().removeIf(entry -> entry.getValue().body == body);
        body.displayMounted = false;
        body.displayFace = null;
        body.rigPose = null;
    }

    public void drag(ServerPlayer player, BodyDragPayload payload) {
        if (payload.operation() == BodyDragPayload.STOP) {
            if (grabs.remove(player.getUUID()) != null) {
                manager.rigChanged();
            }
            return;
        }
        if (!allowed(player) || !player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()
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
        if (body == null || !body.displayMounted || player.distanceToSqr(entity) > 49
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
        grab.body = body;
        grab.entity = entity;
        grab.part = part;
        grab.distance = (float) Math.clamp(nearest, 1, 5);
        grab.updated = time;
        grab.offset = body.rigPose.point(BodyRigPose.node(part)).subtract(eye.add(player.getLookAngle().scale(grab.distance)).subtract(entity.position()));
        grabs.put(player.getUUID(), grab);
        simulations.computeIfAbsent(body.id, ignored -> new BodyRigPhysics()).reset(body.rigPose, entity.position());
    }

    private static boolean visible(ServerPlayer player, Vec3 target) {
        HitResult hit = player.level().clip(new ClipContext(player.getEyePosition(), target,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(target) < 0.04;
    }

    public void tick(MinecraftServer server, Collection<Body> bodies, Map<UUID, Entity> entities) {
        time++;
        boolean enabled = TSFServerConfig.get().bodyLimbPhysics;
        boolean configChanged = enabled != physicsEnabled;
        physicsEnabled = enabled;
        int activeGrabs = grabs.size();
        grabs.entrySet().removeIf(entry -> {
            Grab grab = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            return player == null || !allowed(player) || !player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()
                    || player.level() != grab.entity.level() || grab.entity.isRemoved() || !grab.body.displayMounted
                    || player.distanceToSqr(grab.entity) > 49 || time - grab.updated > 10;
        });
        if (activeGrabs != grabs.size()) {
            manager.rigChanged();
        }
        for (Body body : List.copyOf(bodies)) {
            Entity entity = entities.get(body.id);
            if (entity == null || entity.isRemoved() || body.skinPlaced) {
                continue;
            }
            if (body.displayMounted) {
                BlockPos pos = BlockPos.of(body.displayPosition);
                if (!entity.level().getBlockState(pos).is(BodyDisplayBlock.INSTANCE)) {
                    removeDisplay((ServerLevel) entity.level(), pos, bodies);
                    continue;
                }
                entity.setNoGravity(true);
                entity.setDeltaMovement(Vec3.ZERO);
                entity.setPos(body.x, body.y, body.z);
            }
            ensurePose(body);
            EmptyBodyHolder holder = (EmptyBodyHolder) entity;
            if (configChanged || holder.tsf$rigPose().isEmpty() && (body.displayMounted || enabled)) {
                prepare(body, entity);
            }
            Grab grab = grabs.values().stream().filter(value -> value.body == body).findFirst().orElse(null);
            boolean simulate = TSFServerConfig.get().bodyLimbPhysics && !body.displayMounted;
            if (simulate || grab != null) {
                Vec3 target = Vec3.ZERO;
                if (grab != null) {
                    ServerPlayer player = server.getPlayerList().getPlayer(grabs.entrySet().stream()
                            .filter(entry -> entry.getValue() == grab).findFirst().orElseThrow().getKey());
                    Vec3 worldTarget = player.getEyePosition().add(player.getLookAngle().scale(grab.distance));
                    if (!visible(player, worldTarget)) {
                        continue;
                    }
                    target = worldTarget.subtract(entity.position()).add(grab.offset);
                    if (target.length() > 3.5) {
                        target = target.normalize().scale(3.5);
                    }
                }
                simulations.computeIfAbsent(body.id, ignored -> new BodyRigPhysics()).step(entity, body.rigPose,
                        grab == null ? -1 : grab.part, target, simulate, body.displayMounted ? BlockPos.of(body.displayPosition) : null);
                if (time % 2 == 0 || grab != null) {
                    holder.tsf$setRigPose(body.rigPose.encode());
                    if (body.displayMounted) {
                        entity.setBoundingBox(body.rigPose.bounds().move(entity.position()));
                    }
                }
            } else {
                simulations.remove(body.id);
            }
        }
        simulations.keySet().removeIf(id -> !entities.containsKey(id));
    }
}
