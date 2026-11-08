package icu.tatyrealms.tatykefei.state;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import icu.tatyrealms.tatykefei.TSF;
import icu.tatyrealms.tatykefei.TSFServerConfig;
import icu.tatyrealms.tatykefei.compat.TrinketsCompat;
import icu.tatyrealms.tatykefei.compat.NeedsOfNatureCompat;
import icu.tatyrealms.tatykefei.compat.NeedsOfNatureHypnosis;
import icu.tatyrealms.tatykefei.compat.NeedsOfNatureTanks;
import icu.tatyrealms.tatykefei.compat.NeedsOfNatureBodies;
import icu.tatyrealms.tatykefei.compat.NeedsOfNatureSkins;
import icu.tatyrealms.tatykefei.item.TSFItems;
import net.minecraft.world.item.Items;
import net.minecraft.core.particles.ParticleTypes;
import icu.tatyrealms.tatykefei.item.ProtectionTotems;
import icu.tatyrealms.tatykefei.mixin.MannequinAccessor;
import icu.tatyrealms.tatykefei.network.Action;
import icu.tatyrealms.tatykefei.network.ActionPayload;
import icu.tatyrealms.tatykefei.network.AnimationPayload;
import icu.tatyrealms.tatykefei.network.BodyAnimation;
import icu.tatyrealms.tatykefei.network.StatePayload;
import icu.tatyrealms.tatykefei.network.VisualState;
import icu.tatyrealms.tatykefei.network.ObserverInventory;
import icu.tatyrealms.tatykefei.network.ObserverPayload;
import icu.tatyrealms.tatykefei.network.SkinLayersPayload;
import icu.tatyrealms.tatykefei.network.HypnosisProgressPayload;
import icu.tatyrealms.tatykefei.network.HypnosisSpeechPayload;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class BodyManager {
    public static final String BODY_TAG = "tsf_empty_body";
    public static final String SKIN_TAG = "tsf_folded_skin";
    public static final EntityDimensions BODY_DIMENSIONS = EntityDimensions.fixed(1.0F, 0.275F).withEyeHeight(0.1375F);
    public static final EntityDimensions SKIN_DIMENSIONS = EntityDimensions.fixed(1.0F, 0.375F).withEyeHeight(0.1875F);
    public static final EntityDimensions SLIME_DIMENSIONS = EntityDimensions.fixed(0.52F, 0.52F).withEyeHeight(0.36F);
    public static final EntityDimensions CROUCHING_SLIME_DIMENSIONS = EntityDimensions.fixed(0.86F, 0.312F).withEyeHeight(0.215F);
    private static final Gson GSON = new Gson();
    private static BodyManager instance;
    private final MinecraftServer server;
    private final Path path;
    private final StateStore store;
    private final AsyncStateWriter stateWriter;
    private boolean saveRequested;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, HypnosisSession> hypnosisSessions = new HashMap<>();
    private final Map<UUID, ItemEntity> slimeBottleDrops = new HashMap<>();
    private final Map<UUID, List<UUID>> lastSkinLayers = new HashMap<>();
    private final Map<UUID, UUID> natureBodies = new HashMap<>();
    private final Set<UUID> pendingNatureRestores = new HashSet<>();
    private final NeedsOfNatureSkins natureSkins = new NeedsOfNatureSkins();
    private final Set<UUID> pendingProjections = new HashSet<>();
    private final Map<UUID, ItemEntity> skinDrops = new HashMap<>();
    private final Map<UUID, Entity> bodyEntities = new HashMap<>();
    private final Map<UUID, Integer> lastSkinRecoverySeconds = new HashMap<>();
    private final Map<UUID, ServerPlayer> observerTargets = new HashMap<>();
    private final Map<UUID, ObserverSync> observerSnapshots = new HashMap<>();
    private boolean visualsDirty;
    private int ticks;

    public BodyManager(MinecraftServer server) {
        this.server = server;
        this.path = server.getWorldPath(LevelResource.ROOT).resolve("tsf/state.json");
        try {
            this.store = StateStore.load(path);
        } catch (IOException exception) {
            throw new IllegalStateException("TSF 存档读取失败，请检查 " + path, exception);
        }
        stateWriter = new AsyncStateWriter(path);
        instance = this;
    }

    public static BodyManager get() {
        return instance;
    }

    public static void clear() {
        if (instance != null) {
            BodyManager manager = instance;
            try (AsyncStateWriter writer = manager.stateWriter) {
                manager.save(true);
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to finish saving TSF state: " + manager.path, exception);
            } finally {
                instance = null;
            }
        }
    }

    public static Form form(Player player) {
        return ((FormHolder) player).tsf$getForm();
    }

    public static boolean hypnotized(Player player) {
        return ((FormHolder) player).tsf$isHypnotized();
    }

    public static boolean restricted(Player player, int restriction) {
        return hypnotized(player) && (((FormHolder) player).tsf$hypnosisRestrictions() & restriction) != 0;
    }

    public static boolean spiritSpear(ItemStack stack) {
        return stack.is(TSFItems.POSSESSION_SPEAR) || stack.is(TSFItems.SNATCHING_SPEAR);
    }

    public static Player aimedPlayer(Player player, double distance) {
        Vec3 start = player.getEyePosition();
        Vec3 end = player.pick(distance, 1.0F, false).getLocation();
        var hit = ProjectileUtil.getEntityHitResult(player, start, end,
                player.getBoundingBox().expandTowards(end.subtract(start)).inflate(1),
                entity -> entity.isPickable() && !entity.isSpectator(), start.distanceToSqr(end));
        return hit != null && hit.getEntity() instanceof Player target ? target : null;
    }

    public static boolean emptyBody(Entity entity) {
        return entity instanceof EmptyBodyHolder holder && holder.tsf$isEmptyBody();
    }

    public static boolean foldedSkin(Entity entity) {
        return entity instanceof EmptyBodyHolder holder && holder.tsf$isFoldedSkin();
    }

    public static void message(ServerPlayer player, String key, Object... arguments) {
        player.sendSystemMessage(Component.translatable("message.tsf." + key, arguments));
    }

    private PlayerState state(ServerPlayer player) {
        return store.players.computeIfAbsent(player.getUUID(), ignored -> {
            PlayerState state = new PlayerState();
            state.body = player.getUUID();
            return state;
        });
    }

    private Body nativeBody(ServerPlayer player) {
        PlayerState state = state(player);
        if (state.homeBody == null) {
            state.homeBody = player.getUUID();
        }
        return store.bodies.computeIfAbsent(state.homeBody, id -> {
            Body body = new Body();
            body.id = id;
            body.owner = player.getUUID();
            body.name = player.getGameProfile().name();
            body.profile = ResolvableProfile.CODEC.encodeStart(JsonOps.INSTANCE, player.getProfile()).getOrThrow();
            if (state.form == Form.HUMAN && id.equals(state.body)) {
                body.occupant = player.getUUID();
            }
            locate(body, player);
            return body;
        });
    }

    public void joined(ServerPlayer player) {
        PlayerState state = state(player);
        nativeBody(player);
        if (state.slimeBottleToken == null && state.slimeBottleDimension != null) {
            finishSlimeRelease(player);
        }
        if (state.form == Form.HUMAN) {
            Body body = store.bodies.get(state.body);
            if (body == null || !player.getUUID().equals(body.occupant)) {
                state.form = Form.SLIME;
                state.body = null;
                message(player, "body_unavailable");
            }
        }
        if (state.form == Form.HUMAN && state.zipperReturnForm == Form.HUMAN) {
            storeZipperBody(player, state.zipperReturnBody);
        }
        Body current = store.bodies.get(state.body);
        if (state.form == Form.HUMAN && current != null) {
            if (current.natureTank == null) {
                captureTank(current, player);
            } else {
                NeedsOfNatureTanks.restore(player, current.natureTank);
            }
        } else {
            Body observed = store.bodies.get(state.observedBody);
            if (state.form == Form.OBSERVER && observed != null && observed.natureTank == null) {
                captureTank(observed, player);
            }
            NeedsOfNatureTanks.restore(player, null);
        }
        clearHypnosis(player);
        cancelHypnosis(player);
        if (state.spiritHost != null) {
            releaseSpiritAttachment(player);
        }
        applyForm(player);
        Body initialAppearance = state.form == Form.HUMAN ? appearance(player) : store.bodies.get(state.observedBody);
        if (initialAppearance != null && initialAppearance.natureAppearance == null
                && (state.form == Form.HUMAN || player.getUUID().equals(initialAppearance.owner))) {
            initialAppearance.natureAppearance = NeedsOfNatureBodies.capture(player);
        }
        updateNatureBody(player);
        natureSkins.capture(nativeBody(player));
        natureSkins.capture(initialAppearance);
        pendingNatureRestores.add(player.getUUID());
        lastSkinLayers.put(player.getUUID(), layers(player).stream().map(layer -> layer.body.id).toList());
        visualsDirty = true;
        save();
    }

    public void left(ServerPlayer player) {
        PlayerState state = state(player);
        natureSkins.capture(nativeBody(player));
        natureSkins.capture(appearance(player));
        ProtectionTotems.clear(player.getUUID());
        cancelHypnosis(player);
        NeedsOfNatureCompat.endSession(player);
        if (state.spiritHost != null) {
            releaseSpiritAttachment(player);
            returnToBody(player);
        }
        placeWornSkins(player);
        updateNatureBody(player);
        Body occupied = store.bodies.get(state.body);
        if (player.isAlive() && state.form == Form.HUMAN && occupied != null
                && (!player.getUUID().equals(occupied.owner) || state.zipperReturnForm != null)) {
            if (state.zipperReturnForm != null) {
                leaveZipper(player, false);
            }
            occupied = store.bodies.get(state.body);
            if (occupied != null && !player.getUUID().equals(occupied.owner)) {
                detach(player, Form.SLIME);
                Body home = nativeBody(player);
                if (home.occupant == null && home.zipperCarrier == null && !home.skinned && home.packedToken == null) {
                    Vec3 destination = safePosition(level(home.dimension), new Vec3(home.x, home.y, home.z));
                    if (destination != null) {
                        enter(player, home, destination);
                    }
                }
            }
        }
        if (state.body != null && store.bodies.containsKey(state.body)) {
            locate(store.bodies.get(state.body), player);
        }
        clearHypnosis(player);
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(state(other).spiritHost)) {
                releaseSpiritAttachment(other);
            }
            if (player.getUUID().equals(state(other).hypnotist)) {
                clearHypnosis(other);
            }
        }
        cooldowns.remove(player.getUUID());
        pendingProjections.remove(player.getUUID());
        lastSkinLayers.remove(player.getUUID());
        captureNatureBody(player);
        natureBodies.remove(player.getUUID());
        pendingNatureRestores.remove(player.getUUID());
        natureSkins.forget(player);
        lastSkinRecoverySeconds.remove(player.getUUID());
        observerTargets.remove(player.getUUID());
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other != player) {
                updateObserver(other);
            }
        }
        visualsDirty = true;
        save();
    }

    private void placeWornSkins(ServerPlayer player) {
        List<SkinLayer> worn = List.copyOf(layers(player));
        for (SkinLayer layer : worn) {
            layer.remove.run();
            Body skin = layer.body;
            skin.skinToken = UUID.randomUUID();
            skin.skinDropEntity = null;
            skin.skinPlaced = true;
            skin.skinRecoveryAt = server.overworld().getGameTime() + 600;
            locate(skin, player);
            spawnBody(skin);
        }
        if (!worn.isEmpty()) {
            Body body = store.bodies.get(state(player).body);
            if (body != null) {
                body.skinOrder.clear();
            }
            refreshInventory(player);
        }
    }

    public void openPhone(ServerPlayer player) {
        if (hypnotized(player)) {
            message(player, "hypnotized");
            return;
        }
        if (!hasPhoneAccess(player)) {
            message(player, "need_phone");
            return;
        }
        synchronize(player, true);
    }

    private boolean hasPhoneAccess(ServerPlayer player) {
        PlayerState state = state(player);
        Body body = store.bodies.get(state.body);
        return state.form != Form.HUMAN || state.zipperReturnForm != null || body != null && !player.getUUID().equals(body.owner)
                || !layers(player).isEmpty()
                || player.getInventory().contains(stack -> stack.is(TSFItems.PHONE));
    }

    public void handle(ServerPlayer player, ActionPayload payload) {
        if (!player.isAlive()) {
            return;
        }
        if (state(player).slimeBottleToken != null) {
            message(player, "slime_bottled");
            return;
        }
        if (payload.action() == Action.OPEN) {
            if (ready(player)) {
                openPhone(player);
            }
            return;
        }
        if (payload.action() == Action.OPEN_SKINS) {
            if (!hypnotized(player) && state(player).form == Form.HUMAN) {
                synchronizeSkins(player);
            }
            return;
        }
        boolean hypnosisControl = switch (payload.action()) {
            case HYPNOSIS_MOVEMENT, HYPNOSIS_INTERACTION, HYPNOSIS_LOOK, HYPNOSIS_SPEECH -> true;
            default -> false;
        };
        if ((!hypnosisControl && !ready(player)) || hypnotized(player)) {
            return;
        }
        if (payload.action() == Action.REPLACE_SKIN) {
            if (state(player).form == Form.HUMAN) {
                replaceOuterSkin(player, payload.target());
                synchronizeSkins(player);
            }
            return;
        }
        if (state(player).form == Form.OBSERVER && payload.action() != Action.RETURN
                && payload.action() != Action.RESTORE_SKIN && payload.action() != Action.EXIT) {
            message(player, "observing");
            return;
        }
        if (!hasPhoneAccess(player)) {
            message(player, "need_phone");
            return;
        }
        ServerPlayer target = target(payload.target());
        switch (payload.action()) {
            case SLIME_COLOR -> colorSlime(player, payload.target());
            case RESTORE_SKIN -> restoreOwnSkin(player);
            case HYPNOTIZE -> message(player, "hold_phone");
            case RELEASE -> release(player, target);
            case HYPNOSIS_MOVEMENT -> toggleHypnosis(player, target, HypnosisRestrictions.MOVEMENT);
            case HYPNOSIS_INTERACTION -> toggleHypnosis(player, target, HypnosisRestrictions.INTERACTION);
            case HYPNOSIS_LOOK -> toggleHypnosis(player, target, HypnosisRestrictions.LOOK);
            case HYPNOSIS_SPEECH -> toggleHypnosis(player, target, HypnosisRestrictions.SPEECH);
            case OBSERVER_CHAT -> {
                if (state(player).form == Form.HUMAN && !controlledObservers(player).isEmpty()) {
                    state(player).observerChatPrivate = !state(player).observerChatPrivate;
                    message(player, state(player).observerChatPrivate ? "observer_chat_private" : "observer_chat_public");
                    for (ServerPlayer observer : controlledObservers(player)) {
                        message(observer, state(player).observerChatPrivate ? "observer_chat_private_received" : "observer_chat_public_received");
                    }
                    changed();
                }
            }
            case PROJECT -> message(player, "use_projection_potion");
            case EXPEL_SELF -> message(player, "use_expulsion_potion");
            case LEAVE_ZIPPER -> {
                if (state(player).form == Form.HUMAN && state(player).zipperReturnForm != null) {
                    depart(player, Form.SLIME);
                }
            }
            case EXPEL_TARGET -> {
                if (near(player, target, 16) && player.getUUID().equals(state(target).hypnotist)
                        && NeedsOfNatureCompat.canChangeBody(target)) {
                    detach(target, Form.SLIME);
                    message(target, "expelled");
                    changed();
                } else {
                    message(player, "hypnotize_first");
                }
            }
            case POSSESS -> possess(player, target);
            case RETURN -> returnToBody(player);
            case UNDRESS -> undress(player);
            case EXIT -> exit(player);
            default -> {
            }
        }
        if (payload.action() == Action.UNDRESS) {
            synchronizeSkins(player);
        }
    }

    private boolean ready(ServerPlayer player) {
        if (ProtectionTotems.coolingDown(player)) {
            return false;
        }
        long now = server.overworld().getGameTime();
        Long previous = cooldowns.get(player.getUUID());
        if (previous != null && now - previous < 8) {
            return false;
        }
        cooldowns.put(player.getUUID(), now);
        return true;
    }

    private ServerPlayer target(String uuid) {
        try {
            return uuid.isEmpty() ? null : server.getPlayerList().getPlayer(UUID.fromString(uuid));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean near(ServerPlayer actor, ServerPlayer target, double distance) {
        return target != null && actor != target && target.isAlive() && actor.level() == target.level()
                && actor.distanceToSqr(target) <= distance * distance && actor.hasLineOfSight(target);
    }

    public boolean beginHypnosis(ServerPlayer player, InteractionHand hand) {
        Player aimed = aimedPlayer(player, 16);
        if (!(aimed instanceof ServerPlayer target) || state(player).form != Form.HUMAN
                || hypnotized(player) || !player.isAlive() || player.isSpectator()
                || !player.getItemInHand(hand).is(TSFItems.PHONE)) {
            return false;
        }
        if (state(target).form != Form.HUMAN || target.isSpectator()) {
            message(player, "invalid_target");
            return true;
        }
        if (ProtectionTotems.coolingDown(player)) {
            return true;
        }
        hypnosisSessions.put(player.getUUID(), new HypnosisSession(target.getUUID(), server.overworld().getGameTime()));
        player.startUsingItem(hand);
        hypnosisProgress(player, target.getDisplayName().getString(), 0, HypnosisProgressPayload.DURATION_TICKS);
        return true;
    }

    public void cancelHypnosis(ServerPlayer player) {
        if (hypnosisSessions.remove(player.getUUID()) != null) {
            hypnosisProgress(player, "", 0, 0);
        }
    }

    private void hypnosisProgress(ServerPlayer player, String name, int elapsed, int duration) {
        if (ServerPlayNetworking.canSend(player, HypnosisProgressPayload.TYPE)) {
            ServerPlayNetworking.send(player, new HypnosisProgressPayload(name, elapsed, duration));
        }
    }

    public void tickHypnosis(ServerPlayer player) {
        HypnosisSession session = hypnosisSessions.get(player.getUUID());
        if (session == null) {
            player.stopUsingItem();
            return;
        }
        ServerPlayer target = server.getPlayerList().getPlayer(session.target());
        if (!player.isUsingItem() || !player.getUseItem().is(TSFItems.PHONE) || hypnotized(player)
                || state(player).form != Form.HUMAN || !player.isAlive() || player.isSpectator()
                || !near(player, target, 16) || state(target).form != Form.HUMAN || target.isSpectator()
                || aimedPlayer(player, 16) != target) {
            cancelHypnosis(player);
            player.stopUsingItem();
            player.sendSystemMessage(Component.translatable("message.tsf.hypnosis_cancelled"), true);
            return;
        }
        long elapsed = server.overworld().getGameTime() - session.started();
        if (elapsed >= HypnosisProgressPayload.DURATION_TICKS) {
            cancelHypnosis(player);
            player.stopUsingItem();
            hypnotize(player, target);
        } else if (elapsed % 2 == 0) {
            hypnosisProgress(player, target.getDisplayName().getString(), (int) elapsed, HypnosisProgressPayload.DURATION_TICKS);
        }
    }

    private record HypnosisSession(UUID target, long started) {
    }

    public boolean hasActiveHypnosis(ServerPlayer player) {
        PlayerState state = state(player);
        return player.isAlive() && !player.isSpectator() && state.form == Form.HUMAN && state.hypnotist != null
                && state.hypnosisUntil > server.overworld().getGameTime()
                && server.getPlayerList().getPlayer(state.hypnotist) != null && hypnotized(player);
    }

    public void speakAsHypnotized(ServerPlayer actor, HypnosisSpeechPayload payload) {
        if (!actor.isAlive() || actor.isSpectator() || hypnotized(actor) || state(actor).form != Form.HUMAN
                || !hasPhoneAccess(actor) || !ready(actor)) {
            return;
        }
        ServerPlayer target = server.getPlayerList().getPlayer(payload.target());
        if (!near(actor, target, 16) || !hasActiveHypnosis(target) || !actor.getUUID().equals(state(target).hypnotist)) {
            message(actor, "hypnotize_first");
            return;
        }
        String text = payload.message().strip();
        if (text.isEmpty() || text.length() > HypnosisSpeechPayload.MAX_LENGTH
                || text.codePoints().anyMatch(character -> !StringUtil.isAllowedChatCharacter(character))) {
            message(actor, "hypnosis_speech_invalid");
            return;
        }
        Component decorated = server.getChatDecorator().decorate(target, Component.literal(text));
        server.getPlayerList().broadcastChatMessage(PlayerChatMessage.system(text).withUnsignedContent(decorated),
                actor.createCommandSourceStack(), ChatType.bind(ChatType.CHAT, target));
    }

    private void hypnotize(ServerPlayer player, ServerPlayer target) {
        if (state(player).form != Form.HUMAN || !near(player, target, 16) || state(target).form != Form.HUMAN
                || target.isSpectator()) {
            message(player, "invalid_target");
            return;
        }
        PlayerState state = state(target);
        if (state.hypnotist != null && !player.getUUID().equals(state.hypnotist)) {
            message(player, "already_hypnotized");
            return;
        }
        if (ProtectionTotems.blocks(player, target, ProtectionTotems.Kind.HYPNOSIS)) {
            return;
        }
        target.closeContainer();
        target.stopRiding();
        target.stopUsingItem();
        state.hypnotist = player.getUUID();
        state.hypnosisRestrictions = HypnosisRestrictions.ALL;
        state.hypnosisUntil = server.overworld().getGameTime() + 1200;
        state.anchorDimension = target.level().dimension().identifier().toString();
        state.anchorX = target.getX();
        state.anchorY = target.getY();
        state.anchorZ = target.getZ();
        state.anchorYaw = target.getYRot();
        state.anchorPitch = target.getXRot();
        ((FormHolder) target).tsf$setHypnotized(true);
        ((FormHolder) target).tsf$setHypnosisRestrictions(state.hypnosisRestrictions);
        message(player, "hypnosis_success", target.getDisplayName());
        message(target, "hypnosis_received", player.getDisplayName());
        changed();
    }

    private void release(ServerPlayer player, ServerPlayer target) {
        if (target != null && player.getUUID().equals(state(target).hypnotist)) {
            clearHypnosis(target);
            message(target, "hypnosis_released");
            changed();
        } else {
            message(player, "invalid_target");
        }
    }

    private void clearHypnosis(ServerPlayer player) {
        PlayerState state = state(player);
        state.hypnotist = null;
        state.hypnosisUntil = 0;
        ((FormHolder) player).tsf$setHypnotized(false);
        ((FormHolder) player).tsf$setHypnosisRestrictions(0);
    }

    private void toggleHypnosis(ServerPlayer actor, ServerPlayer target, int restriction) {
        if (target == null || !actor.getUUID().equals(state(target).hypnotist)
                || state(target).hypnosisUntil <= server.overworld().getGameTime()
                || !near(actor, target, 16) || state(target).form != Form.HUMAN) {
            message(actor, "hypnotize_first");
            return;
        }
        PlayerState state = state(target);
        state.hypnosisRestrictions ^= restriction;
        if ((state.hypnosisRestrictions & restriction) != 0) {
            if (restriction == HypnosisRestrictions.MOVEMENT) {
                state.anchorDimension = target.level().dimension().identifier().toString();
                state.anchorX = target.getX();
                state.anchorY = target.getY();
                state.anchorZ = target.getZ();
            }
            if (restriction == HypnosisRestrictions.LOOK) {
                state.anchorYaw = target.getYRot();
                state.anchorPitch = target.getXRot();
            }
            if (restriction == HypnosisRestrictions.INTERACTION) {
                target.closeContainer();
                target.stopUsingItem();
            }
        }
        ((FormHolder) target).tsf$setHypnosisRestrictions(state.hypnosisRestrictions);
        message(target, "hypnosis_rules_changed");
        visualsDirty = true;
    }

    public boolean canProject(ServerPlayer player) {
        return canDrinkBodyPotion(player, false);
    }

    public boolean canDrinkBodyPotion(ServerPlayer player, boolean expulsion) {
        if (!player.isAlive() || hypnotized(player) || player.isSpectator() || state(player).form != Form.HUMAN) {
            message(player, expulsion ? "expulsion_human_only" : "projection_human_only");
            return false;
        }
        if (state(player).expulsionAt != 0) {
            message(player, "expulsion_pending");
            return false;
        }
        return NeedsOfNatureCompat.canChangeBody(player);
    }

    public void queueProjection(ServerPlayer player) {
        pendingProjections.add(player.getUUID());
    }

    public void applyBodyPotion(ServerPlayer player, boolean expulsion) {
        if (!player.isAlive() || player.isSpectator() || hypnotized(player) || state(player).form != Form.HUMAN
                || state(player).expulsionAt != 0 || NeedsOfNatureCompat.isAnimating(player)) {
            return;
        }
        if (expulsion) {
            queueExpulsion(player);
        } else {
            queueProjection(player);
        }
    }

    public void queueExpulsion(ServerPlayer player) {
        PlayerState state = state(player);
        state.expulsionAt = server.overworld().getGameTime() + TSFServerConfig.get().expulsionDelaySeconds * 20L;
        state.expulsionBody = state.body;
        message(player, "expulsion_scheduled", TSFServerConfig.get().expulsionDelaySeconds);
        save();
    }

    public void colorSlime(ServerPlayer player, String value) {
        if (state(player).form != Form.SLIME || player.isSpectator() || !player.isAlive() || hypnotized(player)) {
            message(player, "slime_color_only");
            return;
        }
        String hex = value.startsWith("#") ? value.substring(1) : value;
        if (value.equalsIgnoreCase("reset")) {
            state(player).slimeColor = null;
            message(player, "slime_color_reset");
        } else if (hex.matches("[0-9a-fA-F]{6}")) {
            state(player).slimeColor = Integer.parseInt(hex, 16);
            message(player, "slime_colored", "#" + hex.toUpperCase(java.util.Locale.ROOT));
        } else {
            message(player, "slime_color_invalid");
            return;
        }
        changed();
    }

    public boolean dyeSlime(ServerPlayer player, ServerPlayer target, ItemStack stack) {
        DyeColor dye = stack.get(DataComponents.DYE);
        if (dye == null || state(target).form != Form.SLIME) {
            return false;
        }
        if (stack.isEmpty() || player.isSpectator() || state(player).form == Form.SPIRIT
                || state(player).form == Form.OBSERVER || hypnotized(player) || hypnotized(target)
                || !target.isAlive() || player.level() != target.level() || player.distanceToSqr(target) > 25
                || !player.hasLineOfSight(target)) {
            return true;
        }
        int color = dye.getTextureDiffuseColor() & 0xFFFFFF;
        if (!java.util.Objects.equals(state(target).slimeColor, color)) {
            state(target).slimeColor = color;
            if (!player.hasInfiniteMaterials()) {
                stack.shrink(1);
            }
            player.level().playSound(null, target, SoundEvents.DYE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
            refreshInventory(player);
            message(target, "slime_colored", String.format(java.util.Locale.ROOT, "#%06X", color));
            changed();
        }
        return true;
    }

    public boolean bottleSlime(ServerPlayer actor, ServerPlayer target, InteractionHand hand) {
        ItemStack empty = actor.getItemInHand(hand);
        if (!empty.is(Items.GLASS_BOTTLE) || actor.isSpectator() || !actor.isAlive() || hypnotized(actor)
                || state(actor).form != Form.HUMAN && state(actor).form != Form.SLIME
                || !near(actor, target, 5) || state(target).form != Form.SLIME || !ready(actor)
                || !NeedsOfNatureCompat.canChangeBody(target)) {
            return false;
        }
        PlayerState captive = state(target);
        captive.slimeBottleToken = UUID.randomUUID();
        locateSlimeBottle(captive, actor);
        ItemStack bottle = new ItemStack(TSFItems.BOTTLED_SLIME);
        CompoundTag data = new CompoundTag();
        data.putString("tsf_slime", target.getUUID().toString());
        data.putString("tsf_slime_token", captive.slimeBottleToken.toString());
        bottle.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        bottle.set(DataComponents.CUSTOM_NAME, Component.translatable("item.tsf.named_bottled_slime", target.getGameProfile().name()));
        bottle.set(DataComponents.POTION_CONTENTS, new net.minecraft.world.item.alchemy.PotionContents(
                java.util.Optional.empty(), java.util.Optional.of(captive.slimeColor == null ? 0x76C853 : captive.slimeColor),
                List.of(), java.util.Optional.empty()));
        empty.shrink(1);
        if (empty.isEmpty()) {
            actor.setItemInHand(hand, bottle);
        } else if (!actor.getInventory().add(bottle)) {
            actor.drop(bottle, false, Prediction.SERVER_ONLY);
        }
        target.closeContainer();
        target.stopUsingItem();
        target.stopRiding();
        captive.form = Form.OBSERVER;
        captive.observedBody = null;
        captive.observerFallback = null;
        clearHypnosis(target);
        applyForm(target);
        updateSlimeBottleObserver(target);
        refreshInventory(actor);
        message(actor, "slime_captured", target.getGameProfile().name());
        message(target, "slime_bottled");
        changed();
        return true;
    }

    private PlayerState bottledSlime(ItemStack stack) {
        if (!stack.is(TSFItems.BOTTLED_SLIME) || stack.isEmpty()) {
            return null;
        }
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        try {
            PlayerState captive = store.players.get(UUID.fromString(data.getStringOr("tsf_slime", "")));
            return captive != null && captive.slimeBottleToken != null
                    && captive.slimeBottleToken.toString().equals(data.getStringOr("tsf_slime_token", "")) ? captive : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public boolean releaseSlimeBottle(ServerPlayer actor, InteractionHand hand, Vec3 origin) {
        ItemStack stack = actor.getItemInHand(hand);
        PlayerState captive = bottledSlime(stack);
        if (actor.isSpectator() || !actor.isAlive() || hypnotized(actor) || state(actor).form == Form.SPIRIT
                || captive == null || actor.distanceToSqr(origin) > 36) {
            message(actor, "invalid_slime_bottle");
            return false;
        }
        Vec3 destination = safePosition(actor.level(), origin);
        if (destination == null || !actor.level().mayInteract(actor, BlockPos.containing(destination))) {
            message(actor, "no_space");
            return false;
        }
        releaseSlime(captive, actor.level(), destination);
        stack.shrink(1);
        ItemStack empty = new ItemStack(Items.GLASS_BOTTLE);
        if (stack.isEmpty()) {
            actor.setItemInHand(hand, empty);
        } else if (!actor.getInventory().add(empty)) {
            actor.drop(empty, false, Prediction.SERVER_ONLY);
        }
        refreshInventory(actor);
        changed();
        return true;
    }

    public void breakSlimeBottle(ItemEntity item) {
        PlayerState captive = bottledSlime(item.getItem());
        if (captive != null) {
            Vec3 destination = safePosition((ServerLevel) item.level(), item.position());
            releaseSlime(captive, (ServerLevel) item.level(), destination == null ? item.position() : destination);
            changed();
        }
    }

    private void releaseSlime(PlayerState captive, ServerLevel level, Vec3 destination) {
        slimeBottleDrops.remove(captive.slimeBottleToken);
        captive.slimeBottleToken = null;
        captive.form = Form.SLIME;
        captive.slimeBottleDimension = level.dimension().identifier().toString();
        captive.slimeBottleX = destination.x;
        captive.slimeBottleY = destination.y;
        captive.slimeBottleZ = destination.z;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (state(player) == captive) {
                finishSlimeRelease(player);
                break;
            }
        }
    }

    private void finishSlimeRelease(ServerPlayer player) {
        PlayerState captive = state(player);
        player.setCamera(player);
        player.setGameMode(GameType.byName(captive.savedGameMode, GameType.SURVIVAL));
        restoreAbilities(player, captive);
        captive.form = Form.SLIME;
        applyForm(player);
        player.teleportTo(level(captive.slimeBottleDimension), captive.slimeBottleX, captive.slimeBottleY,
                captive.slimeBottleZ, Set.of(), player.getYRot(), player.getXRot(), false);
        captive.slimeBottleDimension = null;
        message(player, "slime_released");
    }

    private void locateSlimeBottle(PlayerState captive, Entity location) {
        captive.slimeBottleDimension = location.level().dimension().identifier().toString();
        captive.slimeBottleX = location.getX();
        captive.slimeBottleY = location.getY();
        captive.slimeBottleZ = location.getZ();
    }

    private void updateSlimeBottleObserver(ServerPlayer player) {
        PlayerState captive = state(player);
        Entity camera = null;
        for (ServerPlayer carrier : server.getPlayerList().getPlayers()) {
            if (carrier != player && !carrier.isSpectator()
                    && carrier.getInventory().contains(stack -> bottledSlime(stack) == captive)) {
                camera = carrier;
                break;
            }
        }
        ItemEntity drop = slimeBottleDrops.get(captive.slimeBottleToken);
        if (camera == null && drop != null && !drop.isRemoved() && bottledSlime(drop.getItem()) == captive) {
            camera = drop;
        }
        if (camera != null) {
            locateSlimeBottle(captive, camera);
        }
        ServerLevel destination = level(captive.slimeBottleDimension);
        if (player.level() != destination || camera == null
                && player.distanceToSqr(captive.slimeBottleX, captive.slimeBottleY, captive.slimeBottleZ) > 1) {
            player.setCamera(player);
            player.teleportTo(destination, captive.slimeBottleX, captive.slimeBottleY, captive.slimeBottleZ,
                    Set.of(), player.getYRot(), player.getXRot(), false);
        }
        Entity view = camera == null ? player : camera;
        if (player.getCamera() != view) {
            player.setCamera(view);
        }
    }

    public boolean equipZipper(ServerPlayer player, ItemStack stack) {
        if (!stack.is(TSFItems.ZIPPER) || stack.isEmpty() || hypnotized(player)
                || player.isSpectator() || state(player).form != Form.HUMAN) {
            message(player, "zipper_human_only");
            return false;
        }
        if (!NeedsOfNatureCompat.canChangeBody(player)) {
            return false;
        }
        if (!TrinketsCompat.equipNecklace(player, stack)) {
            message(player, "zipper_no_slot");
            return false;
        }
        stack.shrink(1);
        refreshInventory(player);
        message(player, "zipper_equipped");
        changed();
        return true;
    }

    public boolean wearingZipper(ServerPlayer player) {
        return player != null && state(player).form == Form.HUMAN && TrinketsCompat.slots(player).stream()
                .anyMatch(slot -> slot.name().startsWith("chest/necklace/") && slot.stack().is(TSFItems.ZIPPER));
    }

    private boolean wearingZipper(Body body) {
        return body != null && body.accessories.entrySet().stream().anyMatch(entry ->
                entry.getKey().startsWith("chest/necklace/") && decodeItem(entry.getValue()).is(TSFItems.ZIPPER));
    }

    public boolean equipBodyZipper(Entity entity, ItemStack stack) {
        Body body = bodyForEntity(entity);
        if (body == null || !emptyBody(entity) || !stack.is(TSFItems.ZIPPER) || stack.isEmpty()
                || !TrinketsCompat.available() || wearingZipper(body)
                || body.accessories.containsKey("chest/necklace/0")) {
            return false;
        }
        body.accessories.put("chest/necklace/0", encodeItem(stack.copyWithCount(1)));
        stack.shrink(1);
        changed();
        return true;
    }

    private void enterEmptyZipper(ServerPlayer player, Body body, Vec3 destination) {
        PlayerState state = state(player);
        UUID returnBody = state.zipperReturnForm == null ? state.body : state.zipperReturnBody;
        Form returnForm = state.zipperReturnForm == null ? state.form : state.zipperReturnForm;
        if (state.form == Form.HUMAN) {
            detach(player, Form.SPIRIT);
        }
        if (returnForm == Form.HUMAN) {
            storeZipperBody(player, returnBody);
        }
        enter(player, body, destination);
        state.zipperReturnBody = returnBody;
        state.zipperReturnForm = returnForm;
        message(player, "zipper_entered", body.name);
        changed();
        playAnimation(player, BodyAnimation.WEAR_SKIN);
    }

    public boolean enterZipper(ServerPlayer player, ServerPlayer target) {
        if (!wearingZipper(target)) {
            return false;
        }
        if (hypnotized(player) || !ready(player) || player.isSpectator() || state(player).form == Form.OBSERVER) {
            return true;
        }
        if (!near(player, target, 5) || target.isSpectator() || state(target).form != Form.HUMAN) {
            message(player, "invalid_target");
            return true;
        }
        if (!NeedsOfNatureCompat.canChangeBody(player) || !NeedsOfNatureCompat.canChangeBody(target)) {
            return true;
        }
        Body body = store.bodies.get(state(target).body);
        Vec3 destination = body == null ? null : safePosition(target.level(), target.position());
        if (destination == null) {
            message(player, "no_space");
            return true;
        }
        PlayerState state = state(player);
        UUID returnBody = state.zipperReturnForm == null ? state.body : state.zipperReturnBody;
        Form returnForm = state.zipperReturnForm == null ? state.form : state.zipperReturnForm;
        if (state.form == Form.HUMAN) {
            detach(player, Form.SPIRIT);
        }
        if (returnForm == Form.HUMAN) {
            storeZipperBody(player, returnBody);
        }
        transferControl(player, target, body, destination);
        state(player).zipperReturnBody = returnBody;
        state(player).zipperReturnForm = returnForm;
        message(player, "zipper_entered", body.name);
        changed();
        playAnimation(player, BodyAnimation.WEAR_SKIN);
        return true;
    }

    private void leaveZipper(ServerPlayer player) {
        leaveZipper(player, true);
    }

    private void leaveZipper(ServerPlayer player, boolean animate) {
        Vec3 exit = safePosition(player.level(), player.position(), true);
        if (exit == null) {
            message(player, "no_space");
            return;
        }
        UUID returnBody = state(player).zipperReturnBody;
        Form returnForm = state(player).zipperReturnForm;
        detach(player, returnForm == Form.SPIRIT ? Form.SPIRIT : Form.SLIME);
        player.teleportTo(player.level(), exit.x, exit.y, exit.z, Set.of(), player.getYRot(), player.getXRot(), false);
        boolean returned = returnFromZipper(player, returnBody, returnForm);
        changed();
        if (returned && animate) {
            playAnimation(player, BodyAnimation.POSSESS);
        }
    }

    private void storeZipperBody(ServerPlayer player, UUID returnBody) {
        Body previous = store.bodies.get(returnBody);
        if (previous != null && previous.occupant == null && !previous.skinned && previous.packedToken == null
                && (previous.zipperCarrier == null || player.getUUID().equals(previous.zipperCarrier))) {
            removeBodyEntity(previous);
            previous.zipperCarrier = player.getUUID();
        }
    }

    private void releaseZipperBody(ServerPlayer player) {
        Body previous = store.bodies.get(state(player).zipperReturnBody);
        if (previous != null && player.getUUID().equals(previous.zipperCarrier)) {
            previous.zipperCarrier = null;
            locate(previous, player);
            Vec3 destination = safePosition(player.level(), player.position(), true);
            if (destination != null) {
                previous.x = destination.x;
                previous.y = destination.y;
                previous.z = destination.z;
            }
            spawnBody(previous);
        }
    }

    private boolean returnFromZipper(ServerPlayer player, UUID returnBody, Form returnForm) {
        Body previous = store.bodies.get(returnBody);
        Vec3 destination = previous != null && previous.occupant == null && previous.zipperCarrier == null
                && !previous.skinned && previous.packedToken == null
                ? safePosition(level(previous.dimension), new Vec3(previous.x, previous.y, previous.z)) : null;
        if (destination != null) {
            enter(player, previous, destination);
            message(player, "zipper_returned");
        } else {
            message(player, returnForm == Form.SPIRIT ? "projected" : "expelled");
        }
        return destination != null;
    }

    public void depart(ServerPlayer player, Form form) {
        if (hypnotized(player) || state(player).form == Form.OBSERVER || !NeedsOfNatureCompat.canChangeBody(player)) {
            return;
        }
        if (form == Form.SLIME && state(player).form == Form.HUMAN && state(player).zipperReturnForm != null) {
            leaveZipper(player);
            return;
        }
        if (state(player).form == form) {
            message(player, "already_form");
            return;
        }
        if (state(player).form == Form.HUMAN
                && BodyPhysics.findPosition(player.level(), player.position(), BODY_DIMENSIONS) == null) {
            message(player, "no_space");
            return;
        }
        detach(player, form);
        message(player, form == Form.SPIRIT ? "projected" : "expelled");
        changed();
    }

    private void detach(ServerPlayer player, Form form) {
        PlayerState state = state(player);
        releaseZipperBody(player);
        state.zipperReturnBody = null;
        state.zipperReturnForm = null;
        state.expulsionAt = 0;
        state.expulsionBody = null;
        player.closeContainer();
        player.stopRiding();
        player.stopUsingItem();
        if (state.form == Form.HUMAN) {
            captureNatureBody(player);
            saveAbilities(player, state);
            Body body = store.bodies.get(state.body);
            if (body == null) {
                body = nativeBody(player);
            }
            captureTank(body, player);
            NeedsOfNatureTanks.restore(player, null);
            body.skinnableHypnotized = hypnotized(player);
            body.skinnableDowned = NeedsOfNatureCompat.isDowned(player);
            layers(player);
            for (TrinketsCompat.Slot slot : TrinketsCompat.slots(player)) {
                if (skinBody(slot.stack()) != null) {
                    body.skinLayers.add(encodeItem(slot.stack()));
                    slot.clear();
                } else if (slot.name().startsWith("chest/necklace/") && slot.stack().is(TSFItems.ZIPPER)) {
                    body.accessories.put(slot.name(), encodeItem(slot.stack()));
                    slot.clear();
                }
            }
            captureInventory(body, player);
            body.health = Math.max(1, player.getHealth());
            body.food = player.getFoodData().getFoodLevel();
            body.saturation = player.getFoodData().getSaturationLevel();
            locate(body, player);
            body.occupant = null;
            player.getInventory().clearContent();
            spawnBody(body);
            player.setHealth(Math.min(8, player.getMaxHealth()));
            player.getFoodData().setFoodLevel(20);
            refreshInventory(player);
        }
        restoreAbilities(player, state);
        state.body = null;
        state.form = form;
        state.spiritHost = null;
        state.observedBody = null;
        state.observerFallback = null;
        lastSkinLayers.put(player.getUUID(), List.of());
        clearHypnosis(player);
        cancelHypnosis(player);
        applyForm(player);
        updateNatureBody(player);
        player.teleportTo(player.level(), player.getX(), player.getY() + 0.15, player.getZ(), Set.of(),
                player.getYRot(), player.getXRot(), false);
    }

    private void saveAbilities(ServerPlayer player, PlayerState state) {
        state.savedGameMode = player.gameMode().getName();
        Abilities abilities = player.getAbilities();
        state.savedMayFly = abilities.mayfly;
        state.savedFlying = abilities.flying;
        state.savedInvulnerable = abilities.invulnerable;
        state.savedNoGravity = player.isNoGravity();
    }

    private void restoreAbilities(ServerPlayer player, PlayerState state) {
        Abilities abilities = player.getAbilities();
        abilities.mayfly = state.savedMayFly;
        abilities.flying = state.savedFlying;
        abilities.invulnerable = state.savedInvulnerable;
        player.setNoGravity(state.savedNoGravity);
        player.noPhysics = player.isSpectator();
        player.onUpdateAbilities();
    }

    private void applyForm(ServerPlayer player) {
        PlayerState state = state(player);
        ((FormHolder) player).tsf$setForm(state.form);
        if (state.form == Form.OBSERVER && !player.isSpectator()) {
            player.setGameMode(GameType.SPECTATOR);
        }
        if (state.form == Form.SPIRIT) {
            player.getAbilities().mayfly = true;
            player.getAbilities().flying = true;
            player.getAbilities().invulnerable = true;
            player.setNoGravity(true);
            player.noPhysics = false;
            player.onUpdateAbilities();
        }
        updateSpiritSpears(player);
        player.fallDistance = 0;
    }

    private void updateSpiritSpears(ServerPlayer player) {
        boolean spirit = state(player).form == Form.SPIRIT;
        boolean possession = false;
        boolean snatching = false;
        boolean changed = false;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!spiritSpear(stack)) {
                continue;
            }
            boolean duplicate = stack.is(TSFItems.POSSESSION_SPEAR) ? possession : snatching;
            if (!spirit || duplicate) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
                changed = true;
            } else {
                possession |= stack.is(TSFItems.POSSESSION_SPEAR);
                snatching |= stack.is(TSFItems.SNATCHING_SPEAR);
                if (stack.getCount() != 1) {
                    stack.setCount(1);
                    changed = true;
                }
            }
        }
        if (spirit && !possession) {
            changed |= player.getInventory().add(new ItemStack(TSFItems.POSSESSION_SPEAR));
        }
        if (spirit && !snatching) {
            changed |= player.getInventory().add(new ItemStack(TSFItems.SNATCHING_SPEAR));
        }
        if (changed) {
            refreshInventory(player);
        }
    }

    public void useSpiritSpear(ServerPlayer player, ServerPlayer target, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isAlive() || hypnotized(player) || !spiritSpear(stack) || !ready(player)) {
            return;
        }
        if (state(player).form != Form.SPIRIT || player.isSpectator()) {
            message(player, "spear_spirit_only");
            return;
        }
        if (stack.is(TSFItems.POSSESSION_SPEAR)) {
            attachSpirit(player, target);
        } else {
            takeOver(player, target);
        }
    }

    private void attachSpirit(ServerPlayer player, ServerPlayer target) {
        if (!near(player, target, 5) || state(target).form != Form.HUMAN || target.isSpectator()) {
            message(player, "invalid_target");
            return;
        }
        if (!NeedsOfNatureCompat.canChangeBody(player) || !NeedsOfNatureCompat.canChangeBody(target)) {
            return;
        }
        if (ProtectionTotems.blocks(player, target, ProtectionTotems.Kind.POSSESSION)) {
            return;
        }
        PlayerState state = state(player);
        state.spiritHost = target.getUUID();
        state.observedBody = state(target).body;
        state.observerFallback = target.getUUID();
        state.form = Form.OBSERVER;
        applyForm(player);
        message(player, "spirit_attached", target.getDisplayName());
        message(target, "spirit_host", player.getGameProfile().name());
        changed();
    }

    private void releaseSpiritAttachment(ServerPlayer player) {
        PlayerState state = state(player);
        ServerPlayer host = server.getPlayerList().getPlayer(state.spiritHost);
        Entity position = host == null ? player : host;
        Vec3 destination = position.position();
        ServerLevel level = (ServerLevel) position.level();
        player.setCamera(player);
        player.setGameMode(GameType.byName(state.savedGameMode, GameType.SURVIVAL));
        restoreAbilities(player, state);
        state.form = Form.SPIRIT;
        state.spiritHost = null;
        state.observedBody = null;
        state.observerFallback = null;
        applyForm(player);
        player.teleportTo(level, destination.x, destination.y, destination.z, Set.of(),
                player.getYRot(), player.getXRot(), false);
        player.setDeltaMovement(Vec3.ZERO);
        message(player, "spirit_detached");
        visualsDirty = true;
    }

    private void exit(ServerPlayer player) {
        if (state(player).spiritHost != null) {
            releaseSpiritAttachment(player);
            changed();
            return;
        }
        Body body = store.bodies.get(state(player).body);
        if (state(player).form != Form.HUMAN || state(player).zipperReturnForm != null
                || body != null && !player.getUUID().equals(body.owner)) {
            returnToBody(player);
        } else {
            undress(player);
        }
    }

    public void possess(ServerPlayer player, ServerPlayer target) {
        if (state(player).form == Form.SPIRIT && !player.getMainHandItem().is(TSFItems.SNATCHING_SPEAR)) {
            message(player, "use_spirit_spear");
            return;
        }
        takeOver(player, target);
    }

    private void takeOver(ServerPlayer player, ServerPlayer target) {
        if ((state(player).form != Form.SPIRIT && state(player).form != Form.SLIME) || player.isSpectator()) {
            message(player, "leave_first");
            return;
        }
        if (!near(player, target, 5) || state(target).form != Form.HUMAN || target.isSpectator()) {
            message(player, "invalid_target");
            return;
        }
        if (!NeedsOfNatureCompat.canChangeBody(player) || !NeedsOfNatureCompat.canChangeBody(target)) {
            return;
        }
        Body body = store.bodies.get(state(target).body);
        Vec3 destination = body == null ? null : safePosition(target.level(), target.position());
        if (destination == null) {
            message(player, "no_space");
            return;
        }
        BodyAnimation animation = BodyAnimation.TAKEOVER;
        if (ProtectionTotems.blocks(player, target, ProtectionTotems.Kind.POSSESSION)) {
            return;
        }
        transferControl(player, target, body, destination);
        changed();
        playAnimation(player, animation);
    }

    private void transferControl(ServerPlayer player, ServerPlayer target, Body body, Vec3 destination) {
        UUID returnBody = state(target).zipperReturnBody;
        Form returnForm = state(target).zipperReturnForm;
        detach(target, returnForm == null ? Form.OBSERVER : returnForm == Form.SPIRIT ? Form.SPIRIT : Form.SLIME);
        enter(player, body, destination);
        if (returnForm == null) {
            state(target).observedBody = body.id;
            state(target).observerFallback = player.getUUID();
            message(target, "body_stolen", player.getDisplayName());
        } else {
            returnFromZipper(target, returnBody, returnForm);
        }
        message(player, "possessed", body.name);
    }

    public boolean interactBody(ServerPlayer player, Entity entity) {
        if (!emptyBody(entity)) {
            return false;
        }
        if (hypnotized(player) || state(player).form == Form.OBSERVER || !ready(player)
                || !NeedsOfNatureCompat.canChangeBody(player)) {
            return true;
        }
        Body body = foldedSkin(entity) ? placedSkinForEntity(entity) : bodyForEntity(entity);
        if (body == null || entity.level() != player.level() || player.distanceToSqr(entity) > 25
                || !player.hasLineOfSight(entity)) {
            return true;
        }
        if (body.skinPlaced) {
            if (player.getMainHandItem().is(TSFItems.SKIN_KNIFE)) {
                restorePlacedSkin(player, body);
            } else if (state(player).form == Form.HUMAN || state(player).form == Form.SLIME) {
                removeBodyEntity(body);
                body.skinPlaced = false;
                body.skinRecoveryAt = 0;
                giveSkin(player, body);
                changed();
            }
        } else if (player.getMainHandItem().is(TSFItems.ZIPPER)) {
            if (equipBodyZipper(entity, player.getMainHandItem())) {
                refreshInventory(player);
                message(player, "zipper_equipped");
            } else {
                message(player, "zipper_no_slot");
            }
        } else if (!player.isShiftKeyDown() && state(player).form == Form.HUMAN
                && player.getMainHandItem().isEmpty() && wearingZipper(body)) {
            Vec3 destination = safePosition(player.level(), entity.position());
            if (destination == null) {
                message(player, "no_space");
            } else {
                enterEmptyZipper(player, body, destination);
            }
        } else if (player.isShiftKeyDown()) {
            pickUpBody(player, body);
        } else if (player.getMainHandItem().is(TSFItems.SKIN_KNIFE)) {
            skinBody(player, body);
        } else if (state(player).form == Form.HUMAN) {
            message(player, "leave_first");
        } else {
            Vec3 destination = safePosition(player.level(), entity.position());
            if (destination == null) {
                message(player, "no_space");
            } else if (!protectBody(player, body, ProtectionTotems.Kind.POSSESSION)) {
                BodyAnimation animation = state(player).form == Form.SLIME ? BodyAnimation.ENTER_BODY : BodyAnimation.POSSESS;
                enter(player, body, destination);
                message(player, "entered", body.name);
                changed();
                playAnimation(player, animation);
            }
        }
        return true;
    }

    public Body bodyForEntity(Entity entity) {
        for (Body body : store.bodies.values()) {
            if (entity.getUUID().equals(body.entityId) && body.occupant == null && !body.skinned
                    && body.packedToken == null && body.zipperCarrier == null) {
                return body;
            }
        }
        return null;
    }

    private Body placedSkinForEntity(Entity entity) {
        return store.bodies.values().stream().filter(body -> body.skinPlaced && body.skinned
                && entity.getUUID().equals(body.entityId)).findFirst().orElse(null);
    }

    public boolean placeSkin(ServerPlayer player, UseOnContext context) {
        if (hypnotized(player) || player.isSpectator() || state(player).form != Form.HUMAN || !ready(player)
                || !NeedsOfNatureCompat.canChangeBody(player)) {
            return false;
        }
        Body body = skinBody(context.getItemInHand());
        if (body == null || !body.skinned || controller(body, new HashSet<>()) != player
                || layers(player).stream().anyMatch(layer -> layer.body == body)) {
            message(player, "invalid_skin");
            return false;
        }
        BlockPos position = new BlockPlaceContext(context).getClickedPos();
        Vec3 destination = Vec3.atBottomCenterOf(position);
        AABB bounds = SKIN_DIMENSIONS.makeBoundingBox(destination);
        if (player.distanceToSqr(destination) > 36 || !player.level().mayInteract(player, position)
                || !player.mayUseItemAt(position, context.getClickedFace(), context.getItemInHand())
                || !player.level().isInWorldBounds(position) || !player.level().getWorldBorder().isWithinBounds(bounds)
                || !player.level().noBlockCollision(null, bounds)) {
            message(player, "no_space");
            return false;
        }
        locate(body, player);
        body.x = destination.x;
        body.y = destination.y;
        body.z = destination.z;
        body.skinPlaced = true;
        body.skinDropEntity = null;
        body.skinRecoveryAt = server.overworld().getGameTime() + 600;
        consumePlacedItem(player, context);
        spawnBody(body);
        refreshInventory(player);
        message(player, "skin_placed", body.name);
        changed();
        return true;
    }

    private void restorePlacedSkin(ServerPlayer player, Body body) {
        if (state(player).form != Form.HUMAN) {
            return;
        }
        Vec3 destination = safePosition(level(body.dimension), new Vec3(body.x, body.y, body.z));
        if (destination == null) {
            message(player, "no_space");
            return;
        }
        removeBodyEntity(body);
        body.skinPlaced = false;
        body.skinned = false;
        body.skinRecoveryAt = 0;
        body.x = destination.x;
        body.y = destination.y;
        body.z = destination.z;
        spawnBody(body);
        message(player, "skin_unfolded", body.name);
        changed();
    }

    public boolean hitSkin(ServerPlayer player, Entity entity) {
        if (!foldedSkin(entity)) {
            return false;
        }
        if (hypnotized(player) || player.isSpectator() || state(player).form == Form.SPIRIT || !ready(player)
                || player.distanceToSqr(entity) > 25 || !player.hasLineOfSight(entity)
                || !NeedsOfNatureCompat.canChangeBody(player)) {
            return true;
        }
        Body body = placedSkinForEntity(entity);
        if (body != null) {
            removeBodyEntity(body);
            body.skinPlaced = false;
            ItemEntity drop = new ItemEntity(level(body.dimension), body.x, body.y + 0.1, body.z, createSkin(body));
            drop.setDefaultPickUpDelay();
            level(body.dimension).addFreshEntity(drop);
            changed();
        }
        return true;
    }

    private int skinRecoverySeconds(ServerPlayer player) {
        PlayerState state = state(player);
        Body body = state.form == Form.OBSERVER ? store.bodies.get(state.observedBody) : null;
        if (body == null || !body.skinned || body.skinRecoveryAt == 0
                || !body.skinPlaced && body.skinDropEntity == null || controller(body, new HashSet<>()) != null) {
            return -1;
        }
        return (int) Math.max(0, (body.skinRecoveryAt - server.overworld().getGameTime() + 19) / 20);
    }

    private void restoreOwnSkin(ServerPlayer player) {
        if (skinRecoverySeconds(player) != 0) {
            message(player, "skin_recovery_unavailable");
            return;
        }
        Body body = store.bodies.get(state(player).observedBody);
        Vec3 destination = safePosition(level(body.dimension), new Vec3(body.x, body.y, body.z));
        if (destination == null) {
            message(player, "no_space");
            return;
        }
        removeBodyEntity(body);
        if (body.skinDropEntity != null) {
            Entity drop = level(body.dimension).getEntity(body.skinDropEntity);
            if (drop instanceof ItemEntity item && skinBody(item.getItem()) == body) {
                drop.discard();
            }
        }
        skinDrops.remove(body.id);
        body.skinDropEntity = null;
        body.skinRecoveryAt = 0;
        body.skinPlaced = false;
        body.skinned = false;
        body.skinToken = UUID.randomUUID();
        body.x = destination.x;
        body.y = destination.y;
        body.z = destination.z;
        spawnBody(body);
        changed();
    }

    private void pickUpBody(ServerPlayer player, Body body) {
        if ((state(player).form != Form.HUMAN && state(player).form != Form.SLIME) || player.isSpectator()) {
            message(player, "cannot_carry_body");
            return;
        }
        removeBodyEntity(body);
        body.packedToken = UUID.randomUUID();
        ItemStack stack = new ItemStack(TSFItems.EMPTY_BODY);
        CompoundTag data = new CompoundTag();
        data.putString("tsf_body", body.id.toString());
        data.putString("tsf_body_token", body.packedToken.toString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        stack.set(DataComponents.PROFILE, profile(body));
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable("item.tsf.named_body", body.name));
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false, Prediction.SERVER_ONLY);
        }
        refreshInventory(player);
        message(player, "body_picked_up", body.name);
        changed();
    }

    public boolean placeBody(ServerPlayer player, UseOnContext context) {
        if (hypnotized(player) || player.isSpectator() || state(player).form == Form.SPIRIT || !ready(player)) {
            return false;
        }
        ItemStack stack = context.getItemInHand();
        Body body = linkedBody(stack);
        String token = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getStringOr("tsf_body_token", "");
        if (!stack.is(TSFItems.EMPTY_BODY) || body == null || body.packedToken == null || body.skinned
                || body.occupant != null || !body.packedToken.toString().equals(token)) {
            message(player, "invalid_body_item");
            return false;
        }
        BlockPos position = new BlockPlaceContext(context).getClickedPos();
        Vec3 destination = Vec3.atBottomCenterOf(position);
        double radius = BODY_DIMENSIONS.width() / 2.0;
        AABB bounds = new AABB(destination.x - radius, destination.y, destination.z - radius,
                destination.x + radius, destination.y + BODY_DIMENSIONS.height(), destination.z + radius);
        if (player.distanceToSqr(destination) > 36 || !player.level().mayInteract(player, position)
                || !player.mayUseItemAt(position, context.getClickedFace(), stack)
                || !player.level().isInWorldBounds(position) || !player.level().getWorldBorder().isWithinBounds(bounds)
                || !player.level().noBlockCollision(null, bounds)) {
            message(player, "no_space");
            return false;
        }
        locate(body, player);
        body.x = destination.x;
        body.y = destination.y;
        body.z = destination.z;
        body.packedToken = null;
        spawnBody(body);
        consumePlacedItem(player, context);
        refreshInventory(player);
        message(player, "body_placed", body.name);
        changed();
        return true;
    }

    private void consumePlacedItem(ServerPlayer player, UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        stack.shrink(1);
        player.setItemInHand(context.getHand(), stack.copy());
    }

    public void regenerateBody(ServerPlayer player, ItemStack totem) {
        regenerateBody(player, player, totem);
    }

    public void regenerateBody(ServerPlayer actor, ServerPlayer player, ItemStack totem) {
        if (actor != player && (!near(actor, player, 5) || !actor.isAlive() || actor.isSpectator()
                || hypnotized(actor) || state(actor).form == Form.SPIRIT || state(actor).form == Form.OBSERVER)) {
            return;
        }
        if (!NeedsOfNatureCompat.canChangeBody(player)) {
            return;
        }
        if (!totem.is(TSFItems.BODY_REGENERATION_TOTEM) || totem.isEmpty() || !player.isAlive()
                || hypnotized(player) || player.isSpectator() || state(player).form != Form.SLIME) {
            message(actor, "regeneration_slime_only");
            return;
        }
        if (!ready(actor)) {
            return;
        }
        Vec3 destination = safePosition(player.level(), player.position());
        if (destination == null) {
            message(actor, "no_space");
            return;
        }
        Body body = new Body();
        body.id = UUID.randomUUID();
        body.owner = player.getUUID();
        body.name = player.getGameProfile().name();
        body.profile = ResolvableProfile.CODEC.encodeStart(JsonOps.INSTANCE, player.getProfile()).getOrThrow();
        body.natureAppearance = freshNatureBody(player);
        locate(body, player);
        store.bodies.put(body.id, body);
        state(player).homeBody = body.id;
        totem.shrink(1);
        enter(player, body, destination);
        message(player, "body_regenerated");
        if (actor != player) {
            refreshInventory(actor);
            message(actor, "body_regenerated_other", player.getDisplayName());
        }
        changed();
    }

    private void enter(ServerPlayer player, Body body, Vec3 destination) {
        player.closeContainer();
        List<ItemStack> carried = new ArrayList<>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && !spiritSpear(stack)) {
                carried.add(stack.copy());
            }
        }
        removeBodyEntity(body);
        PlayerState state = state(player);
        if (state.form == Form.OBSERVER) {
            player.setCamera(player);
            player.setGameMode(GameType.byName(state.savedGameMode, GameType.SURVIVAL));
        }
        restoreAbilities(player, state);
        state.form = Form.HUMAN;
        state.body = body.id;
        state.observedBody = null;
        state.observerFallback = null;
        state.spiritHost = null;
        state.zipperReturnBody = null;
        state.zipperReturnForm = null;
        body.zipperCarrier = null;
        body.occupant = player.getUUID();
        player.getInventory().clearContent();
        for (int slot = 0; slot < Math.min(body.inventory.size(), player.getInventory().getContainerSize()); slot++) {
            player.getInventory().setItem(slot, decodeItem(body.inventory.get(slot)));
        }
        body.inventory.clear();
        player.getInventory().setSelectedSlot(body.selectedSlot);
        applyForm(player);
        player.teleportTo(level(body.dimension), destination.x, destination.y, destination.z, Set.of(), body.yaw, 0, false);
        player.setHealth(Math.min(body.health, player.getMaxHealth()));
        player.getFoodData().setFoodLevel(body.food);
        player.getFoodData().setSaturation(body.saturation);
        for (var entry : body.accessories.entrySet()) {
            ItemStack accessory = decodeItem(entry.getValue());
            TrinketsCompat.Slot slot = TrinketsCompat.slots(player).stream()
                    .filter(candidate -> candidate.name().equals(entry.getKey()) && candidate.stack().isEmpty()
                            && candidate.inventory().canPlaceItem(candidate.index(), accessory)).findFirst().orElse(null);
            if (slot != null) {
                slot.inventory().setItem(slot.index(), accessory);
                slot.inventory().setChanged();
            } else if (!player.getInventory().add(accessory)) {
                player.drop(accessory, false, Prediction.SERVER_ONLY);
            }
        }
        body.accessories.clear();
        for (ItemStack stack : carried) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false, Prediction.SERVER_ONLY);
            }
        }
        NeedsOfNatureTanks.restore(player, body.natureTank);
        updateNatureBody(player);
        refreshInventory(player);
        lastSkinLayers.put(player.getUUID(), layers(player).stream().map(layer -> layer.body.id).toList());
    }

    public void returnToBody(ServerPlayer player) {
        if (state(player).slimeBottleToken != null || hypnotized(player) || !NeedsOfNatureCompat.canChangeBody(player)) {
            return;
        }
        if (state(player).spiritHost != null) {
            releaseSpiritAttachment(player);
        }
        if (state(player).form == Form.HUMAN && state(player).zipperReturnForm != null) {
            leaveZipper(player);
            return;
        }
        Body body = nativeBody(player);
        if (player.getUUID().equals(body.occupant)) {
            message(player, "already_home");
            return;
        }
        if (body.occupant != null || body.zipperCarrier != null || body.skinned) {
            message(player, body.skinned ? "body_is_skin" : "body_occupied");
            return;
        }
        if (body.packedToken != null) {
            message(player, "body_packed");
            return;
        }
        Vec3 destination = safePosition(level(body.dimension), new Vec3(body.x, body.y, body.z));
        if (destination == null) {
            message(player, "no_space");
            return;
        }
        BodyAnimation animation = state(player).form == Form.SLIME ? BodyAnimation.ENTER_BODY : BodyAnimation.POSSESS;
        if (state(player).form == Form.HUMAN) {
            detach(player, Form.SLIME);
        }
        enter(player, body, destination);
        message(player, "returned");
        changed();
        playAnimation(player, animation);
    }

    public void skinPlayer(ServerPlayer player, ServerPlayer target) {
        if (hypnotized(player) || !ready(player)) {
            return;
        }
        if (state(player).form != Form.HUMAN || !player.getMainHandItem().is(TSFItems.SKIN_KNIFE)
                || !near(player, target, 5) || state(target).form != Form.HUMAN || target.isSpectator()) {
            message(player, "invalid_target");
            return;
        }
        boolean downed = NeedsOfNatureCompat.isDowned(target);
        Body body = store.bodies.get(state(target).body);
        if (body == null || !skinRules(player, hypnotized(target), downed)
                || !NeedsOfNatureCompat.canChangeBody(player)
                || !downed && !NeedsOfNatureCompat.canChangeBody(target)) {
            return;
        }
        if (ProtectionTotems.blocks(player, target, ProtectionTotems.Kind.SKIN)) {
            return;
        }
        if (!NeedsOfNatureCompat.finishDowned(target)) {
            return;
        }
        detach(target, Form.OBSERVER);
        body.skinnableDowned = downed;
        state(target).observedBody = body.id;
        state(target).observerFallback = player.getUUID();
        skinBody(player, body);
        message(target, "skinned");
    }

    private void skinBody(ServerPlayer player, Body body) {
        if (body == null || body.occupant != null || body.skinned || state(player).form != Form.HUMAN) {
            return;
        }
        if (!skinKnifeEnabled(player)) {
            return;
        }
        if (protectBody(player, body, ProtectionTotems.Kind.SKIN)) {
            return;
        }
        removeBodyEntity(body);
        body.skinned = true;
        body.skinToken = UUID.randomUUID();
        body.skinPlaced = true;
        body.skinDropEntity = null;
        body.skinRecoveryAt = server.overworld().getGameTime() + 600;
        spawnBody(body);
        message(player, "skin_created", body.name);
        changed();
    }

    private boolean protectBody(ServerPlayer actor, Body body, ProtectionTotems.Kind kind) {
        if (actor.getUUID().equals(body.owner)) {
            return false;
        }
        for (var item : ProtectionTotems.items(kind)) {
            for (int slot = 0; slot < body.inventory.size(); slot++) {
                ItemStack stack = decodeItem(body.inventory.get(slot));
                if (stack.is(item)) {
                    ProtectionTotems.consume(actor, body.owner, body.name, stack, kind);
                    body.inventory.set(slot, encodeItem(stack));
                    save();
                    return true;
                }
            }
            for (var entry : body.accessories.entrySet()) {
                ItemStack stack = decodeItem(entry.getValue());
                if (stack.is(item)) {
                    ProtectionTotems.consume(actor, body.owner, body.name, stack, kind);
                    entry.setValue(encodeItem(stack));
                    save();
                    return true;
                }
            }
        }
        return false;
    }

    private ItemStack createSkin(Body body) {
        body.skinToken = UUID.randomUUID();
        ItemStack skin = new ItemStack(TSFItems.SKIN_SUIT);
        CompoundTag data = new CompoundTag();
        data.putString("tsf_body", body.id.toString());
        data.putString("tsf_skin_token", body.skinToken.toString());
        skin.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        skin.set(DataComponents.PROFILE, profile(body));
        skin.set(DataComponents.CUSTOM_NAME, Component.translatable("item.tsf.named_skin", body.name));
        return skin;
    }

    private void giveSkin(ServerPlayer player, Body body) {
        ItemStack skin = createSkin(body);
        if (!player.getInventory().add(skin)) {
            player.drop(skin, false, Prediction.SERVER_ONLY);
        }
        refreshInventory(player);
    }

    private boolean skinRules(ServerPlayer player, boolean hypnotized, boolean downed) {
        TSFServerConfig config = TSFServerConfig.get();
        if (!skinKnifeEnabled(player)) {
            return false;
        }
        if (config.skinKnifeRequiresHypnosis && !hypnotized) {
            message(player, "skin_requires_hypnosis");
        } else if (config.skinKnifeRequiresDowned && !NeedsOfNatureCompat.supportsDowned()) {
            message(player, "skin_requires_non");
        } else if (config.skinKnifeRequiresDowned && !downed) {
            message(player, "skin_requires_downed");
        } else {
            return true;
        }
        return false;
    }

    private boolean skinKnifeEnabled(ServerPlayer player) {
        if (!TSFServerConfig.get().skinKnifeEnabled) {
            message(player, "skin_knife_disabled");
            return false;
        }
        return true;
    }

    private Body skinBody(ItemStack stack) {
        if (!stack.is(TSFItems.SKIN_SUIT)) {
            return null;
        }
        Body body = linkedBody(stack);
        String token = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getStringOr("tsf_skin_token", "");
        return body != null && !body.skinPlaced && (body.skinToken == null || body.skinToken.toString().equals(token)) ? body : null;
    }

    private Body linkedBody(ItemStack stack) {
        String id = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getStringOr("tsf_body", "");
        try {
            return store.bodies.get(UUID.fromString(id));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public boolean canWearSkin(ServerPlayer player, ItemStack stack) {
        return canWearSkin(player, stack, false);
    }

    private boolean canWearSkin(ServerPlayer player, ItemStack stack, boolean replacing) {
        if (!NeedsOfNatureCompat.canChangeBody(player)) {
            return false;
        }
        Body body = skinBody(stack);
        if (hypnotized(player) || player.isSpectator() || state(player).form != Form.HUMAN || body == null || !body.skinned
                || layers(player).stream().anyMatch(layer -> layer.body.id.equals(body.id))) {
            message(player, "invalid_skin");
            return false;
        }
        if (layers(player).size() - (replacing ? 1 : 0) >= TSFServerConfig.get().maxSkinLayers) {
            message(player, "skin_layer_limit", TSFServerConfig.get().maxSkinLayers);
            return false;
        }
        return true;
    }

    public void synchronizeSkins(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, SkinLayersPayload.TYPE)) {
            return;
        }
        List<SkinLayer> worn = layers(player);
        List<SkinLayersPayload.Entry> current = worn.stream()
                .map(layer -> new SkinLayersPayload.Entry(layer.body.id, layer.body.name, -1)).toList();
        List<SkinLayersPayload.Entry> available = new ArrayList<>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            Body skin = skinBody(player.getInventory().getItem(slot));
            if (skin != null && skin.skinned && worn.stream().noneMatch(layer -> layer.body == skin)) {
                available.add(new SkinLayersPayload.Entry(skin.id, skin.name, slot));
            }
        }
        ServerPlayNetworking.send(player, new SkinLayersPayload(GSON.toJson(
                new SkinLayersPayload.Snapshot(current, available, TSFServerConfig.get().maxSkinLayers))));
    }

    private void replaceOuterSkin(ServerPlayer player, String requestedBody) {
        UUID requested;
        try {
            requested = UUID.fromString(requestedBody);
        } catch (IllegalArgumentException exception) {
            return;
        }
        int slot = -1;
        for (int index = 0; index < player.getInventory().getContainerSize(); index++) {
            Body candidate = skinBody(player.getInventory().getItem(index));
            if (candidate != null && requested.equals(candidate.id)) {
                slot = index;
                break;
            }
        }
        if (slot < 0) {
            return;
        }
        List<SkinLayer> worn = layers(player);
        ItemStack replacement = player.getInventory().getItem(slot);
        if (worn.isEmpty() || !canWearSkin(player, replacement, true)) {
            return;
        }
        SkinLayer outer = worn.getLast();
        ItemStack removed = outer.stack.copy();
        ItemStack incoming = replacement.copyWithCount(1);
        outer.remove.run();
        replacement.shrink(1);
        Body body = store.bodies.get(state(player).body);
        body.skinOrder.remove(outer.body.id);
        if (!TrinketsCompat.equipSkin(player, incoming)) {
            body.skinLayers.add(encodeItem(incoming));
        }
        if (!player.getInventory().add(removed)) {
            player.drop(removed, false, Prediction.SERVER_ONLY);
        }
        refreshInventory(player);
        message(player, "skin_replaced", outer.body.name, skinBody(incoming).name);
        changed();
    }

    public boolean wearSkin(ServerPlayer player, ItemStack stack) {
        if (!ready(player) || !canWearSkin(player, stack)) {
            return false;
        }
        Body body = store.bodies.get(state(player).body);
        if (body == null) {
            return false;
        }
        if (!TrinketsCompat.equipSkin(player, stack)) {
            body.skinLayers.add(encodeItem(stack.copyWithCount(1)));
        }
        stack.shrink(1);
        refreshInventory(player);
        message(player, "skin_worn");
        changed();
        return true;
    }

    public void unfoldSkin(ServerPlayer player, ItemStack stack) {
        if (hypnotized(player) || state(player).form != Form.HUMAN || !ready(player)
                || !NeedsOfNatureCompat.canChangeBody(player)) {
            return;
        }
        Body body = skinBody(stack);
        Vec3 destination = safePosition(player.level(), player.position().add(player.getLookAngle().multiply(2, 0, 2)));
        if (body == null || !body.skinned || destination == null) {
            message(player, "invalid_skin");
            return;
        }
        locate(body, player);
        body.x = destination.x;
        body.y = destination.y;
        body.z = destination.z;
        body.skinned = false;
        spawnBody(body);
        stack.shrink(1);
        refreshInventory(player);
        message(player, "skin_unfolded", body.name);
        changed();
    }

    private void undress(ServerPlayer player) {
        if (!NeedsOfNatureCompat.canChangeBody(player)) {
            return;
        }
        List<SkinLayer> layers = layers(player);
        if (!layers.isEmpty() && !hypnotized(player)) {
            SkinLayer outermost = layers.getLast();
            outermost.remove.run();
            if (!player.getInventory().add(outermost.stack)) {
                player.drop(outermost.stack, false, Prediction.SERVER_ONLY);
            }
            refreshInventory(player);
            message(player, "skin_removed", outermost.body.name);
            changed();
        } else {
            message(player, "no_skin_worn");
        }
    }

    private List<SkinLayer> layers(ServerPlayer player) {
        List<SkinLayer> result = new ArrayList<>();
        Body body = state(player).form == Form.HUMAN ? store.bodies.get(state(player).body) : null;
        if (body == null) {
            return result;
        }
        boolean migrated = false;
        var iterator = body.skinLayers.iterator();
        while (iterator.hasNext()) {
            ItemStack stack = decodeItem(iterator.next());
            Body skin = skinBody(stack);
            if (skin != null && skin.skinned && TrinketsCompat.equipSkin(player, stack)) {
                iterator.remove();
                migrated = true;
                visualsDirty = true;
            }
        }
        for (JsonElement json : body.skinLayers) {
            addLayer(result, decodeItem(json), () -> body.skinLayers.remove(json));
        }
        addLayer(result, player.getItemBySlot(EquipmentSlot.CHEST),
                () -> player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY));
        for (TrinketsCompat.Slot slot : TrinketsCompat.slots(player)) {
            addLayer(result, slot.stack(), slot::clear);
        }
        Set<UUID> present = new HashSet<>();
        result.removeIf(layer -> !present.add(layer.body.id));
        body.skinOrder.removeIf(owner -> !present.contains(owner));
        List<UUID> discovered = result.stream().map(layer -> layer.body.id)
                .filter(id -> !body.skinOrder.contains(id)).toList();
        body.skinOrder.addAll(migrated ? 0 : body.skinOrder.size(), discovered);
        result.sort(java.util.Comparator.comparingInt(layer -> body.skinOrder.indexOf(layer.body.id)));
        return result;
    }

    private void addLayer(List<SkinLayer> layers, ItemStack stack, Runnable remove) {
        Body body = skinBody(stack);
        if (body != null && body.skinned) {
            layers.add(new SkinLayer(body, stack, remove));
        }
    }

    private record SkinLayer(Body body, ItemStack stack, Runnable remove) {
    }

    public Body appearance(ServerPlayer player) {
        if (state(player).form != Form.HUMAN) {
            return null;
        }
        List<SkinLayer> layers = layers(player);
        return layers.isEmpty() ? store.bodies.get(state(player).body) : layers.getLast().body;
    }

    public Component assumedName(ServerPlayer player) {
        Body appearance = appearance(player);
        return appearance == null || appearance.owner.equals(player.getUUID()) ? null : Component.literal(appearance.name);
    }

    private ServerPlayer controller(Body body, Set<UUID> visited) {
        if (body == null || !visited.add(body.id)) {
            return null;
        }
        if (body.occupant != null) {
            ServerPlayer occupant = server.getPlayerList().getPlayer(body.occupant);
            return occupant != null && state(occupant).form == Form.HUMAN ? occupant : null;
        }
        if (body.zipperCarrier != null) {
            ServerPlayer carrier = server.getPlayerList().getPlayer(body.zipperCarrier);
            return carrier != null && state(carrier).form == Form.HUMAN ? carrier : null;
        }
        for (ServerPlayer candidate : server.getPlayerList().getPlayers()) {
            if (layers(candidate).stream().anyMatch(layer -> layer.body == body)) {
                return candidate;
            }
        }
        for (Body parent : store.bodies.values()) {
            if (parent.skinLayers.stream().anyMatch(json -> skinBody(decodeItem(json)) == body)
                    || parent.inventory.stream().anyMatch(json -> skinBody(decodeItem(json)) == body)) {
                ServerPlayer wearer = controller(parent, visited);
                if (wearer != null) {
                    return wearer;
                }
            }
        }
        for (ServerPlayer candidate : server.getPlayerList().getPlayers()) {
            if (state(candidate).form == Form.HUMAN
                    && candidate.getInventory().contains(stack -> skinBody(stack) == body)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean updateObserver(ServerPlayer player) {
        PlayerState state = state(player);
        if (state.form != Form.OBSERVER) {
            return false;
        }
        if (state.slimeBottleToken != null) {
            updateSlimeBottleObserver(player);
            return false;
        }
        Body body = store.bodies.get(state.observedBody);
        if (state.spiritHost == null && body != null && !body.skinned && body.packedToken == null && body.occupant == null
                && body.zipperCarrier == null) {
            Vec3 destination = safePosition(level(body.dimension), new Vec3(body.x, body.y, body.z));
            if (destination != null) {
                enter(player, body, destination);
                message(player, "spectating_ended");
                return true;
            }
        }
        ServerPlayer target = state.spiritHost == null ? controller(body, new HashSet<>())
                : server.getPlayerList().getPlayer(state.spiritHost);
        if (state.spiritHost != null && (target == null || !target.isAlive() || state(target).form != Form.HUMAN)) {
            releaseSpiritAttachment(player);
            return true;
        }
        if (form(player) != state.form || !player.isSpectator()) {
            applyForm(player);
        }
        if (target != null && !target.getUUID().equals(state.observerFallback)) {
            state.observerFallback = target.getUUID();
            visualsDirty = true;
        }
        if (target == null && body != null && body.skinned && !body.skinPlaced && body.skinDropEntity == null) {
            target = server.getPlayerList().getPlayer(state.observerFallback);
        }
        if (target == player || target != null && (!target.isAlive() || state(target).form != Form.HUMAN)) {
            target = null;
        }
        Entity camera = target == null ? player : target;
        if (state.spiritHost == null && body != null
                && (body.skinPlaced && body.entityId != null || body.skinDropEntity != null)) {
            Entity skin = level(body.dimension).getEntity(body.skinPlaced ? body.entityId : body.skinDropEntity);
            if (skin != null) {
                camera = skin;
            }
        }
        if (camera.level() != player.level()) {
            player.teleportTo((ServerLevel) camera.level(), camera.getX(), camera.getY(), camera.getZ(),
                    Set.of(), camera.getYRot(), camera.getXRot(), false);
            player.setCamera(player);
            return false;
        }
        if (player.getCamera() != camera) {
            player.setCamera(camera);
        }
        return false;
    }

    private ServerPlayer speechController(ServerPlayer observer) {
        PlayerState state = state(observer);
        if (state.form != Form.OBSERVER || state.spiritHost != null || state.observedBody == null) {
            return null;
        }
        ServerPlayer controller = controller(store.bodies.get(state.observedBody), new HashSet<>());
        return controller == observer ? null : controller;
    }

    private List<ServerPlayer> controlledObservers(ServerPlayer player) {
        return server.getPlayerList().getPlayers().stream().filter(observer -> speechController(observer) == player).toList();
    }

    public boolean allowChat(ServerPlayer player, PlayerChatMessage message) {
        ServerPlayer controller = speechController(player);
        return ObserverChat.allowChat(player, message, controller,
                controller != null && state(controller).observerChatPrivate,
                message.signedContent().startsWith("@!") ? controlledObservers(player) : List.of());
    }

    public boolean allowObserverCommand(ServerPlayer player, String command) {
        ServerPlayer controller = speechController(player);
        return ObserverChat.allowCommand(player, command, controller,
                controller != null && state(controller).observerChatPrivate);
    }

    public boolean allowCommandMessage(ServerPlayer player) {
        ServerPlayer controller = speechController(player);
        if (controller != null && state(controller).observerChatPrivate) {
            message(player, "observer_chat_private_only");
            return false;
        }
        return true;
    }

    private JsonElement encodeItem(ItemStack stack) {
        return ItemStack.OPTIONAL_CODEC.encodeStart(server.registryAccess().createSerializationContext(JsonOps.INSTANCE), stack).getOrThrow();
    }

    private void captureInventory(Body body, ServerPlayer player) {
        body.inventory.clear();
        var operations = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            body.inventory.add(ItemStack.OPTIONAL_CODEC.encodeStart(operations, spiritSpear(stack) ? ItemStack.EMPTY : stack).getOrThrow());
        }
        body.selectedSlot = player.getInventory().getSelectedSlot();
    }

    private ItemStack decodeItem(JsonElement json) {
        return ItemStack.OPTIONAL_CODEC.parse(server.registryAccess().createSerializationContext(JsonOps.INSTANCE), json).getOrThrow();
    }

    private ResolvableProfile profile(Body body) {
        return ResolvableProfile.CODEC.parse(JsonOps.INSTANCE, body.profile).getOrThrow();
    }

    private void locate(Body body, Entity entity) {
        body.dimension = entity.level().dimension().identifier().toString();
        body.x = entity.getX();
        body.y = entity.getY();
        body.z = entity.getZ();
        body.yaw = entity.getYRot();
    }

    private ServerLevel level(String dimension) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimension)));
        if (level == null) {
            throw new IllegalStateException("TSF body dimension is missing: " + dimension);
        }
        return level;
    }

    private Vec3 safePosition(ServerLevel level, Vec3 origin) {
        return safePosition(level, origin, false);
    }

    private Vec3 safePosition(ServerLevel level, Vec3 origin, boolean beside) {
        level.getChunk(BlockPos.containing(origin));
        int[] offsets = {0, 1, -1};
        for (int rise = 0; rise <= 2; rise++) {
            for (int offsetX : offsets) {
                for (int offsetZ : offsets) {
                    if (beside && offsetX == 0 && offsetZ == 0) {
                        continue;
                    }
                    Vec3 candidate = origin.add(offsetX, rise, offsetZ);
                    AABB bounds = new AABB(candidate.x - 0.3, candidate.y, candidate.z - 0.3,
                            candidate.x + 0.3, candidate.y + 1.8, candidate.z + 0.3);
                    if (level.getWorldBorder().isWithinBounds(BlockPos.containing(candidate))
                            && level.noBlockCollision(null, bounds)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private void spawnBody(Body body) {
        if (body.packedToken != null || body.zipperCarrier != null || body.skinned && !body.skinPlaced || body.occupant != null) {
            return;
        }
        ServerLevel level = level(body.dimension);
        level.getChunk(BlockPos.containing(body.x, body.y, body.z));
        if (body.entityId != null && level.getEntity(body.entityId) != null) {
            return;
        }
        Vec3 destination = BodyPhysics.findPosition(level, new Vec3(body.x, body.y, body.z),
                body.skinPlaced ? SKIN_DIMENSIONS : BODY_DIMENSIONS);
        if (destination == null) {
            return;
        }
        Mannequin mannequin = EntityTypes.MANNEQUIN.create(level, EntitySpawnReason.TRIGGERED);
        if (mannequin == null) {
            throw new IllegalStateException("Unable to create TSF body");
        }
        mannequin.setPos(body.x, body.y, body.z);
        mannequin.setYRot(body.yaw);
        mannequin.setYBodyRot(body.yaw);
        mannequin.setYHeadRot(body.yaw);
        mannequin.setPose(Pose.SLEEPING);
        mannequin.setNoGravity(false);
        mannequin.setPermanentlyInvulnerable(true);
        mannequin.setCustomName(Component.translatable(body.skinPlaced ? "item.tsf.named_skin" : "entity.tsf.empty_body", body.name));
        mannequin.setCustomNameVisible(false);
        mannequin.addTag(BODY_TAG);
        ((EmptyBodyHolder) mannequin).tsf$setEmptyBody(true);
        if (body.skinPlaced) {
            mannequin.addTag(SKIN_TAG);
            ((EmptyBodyHolder) mannequin).tsf$setFoldedSkin(true);
        }
        MannequinAccessor accessor = (MannequinAccessor) mannequin;
        accessor.tsf$setProfile(profile(body));
        accessor.tsf$setImmovable(false);
        accessor.tsf$setHideDescription(true);
        mannequin.noPhysics = false;
        mannequin.setPos(destination);
        locate(body, mannequin);
        for (var entry : Inventory.EQUIPMENT_SLOT_MAPPING.int2ObjectEntrySet()) {
            if (body.skinPlaced) {
                break;
            }
            if (entry.getIntKey() < body.inventory.size()) {
                mannequin.setItemSlot(entry.getValue(), decodeItem(body.inventory.get(entry.getIntKey())));
            }
        }
        if (!body.skinPlaced && body.selectedSlot < body.inventory.size()) {
            mannequin.setItemSlot(EquipmentSlot.MAINHAND, decodeItem(body.inventory.get(body.selectedSlot)));
        }
        body.entityId = mannequin.getUUID();
        level.addFreshEntity(mannequin);
    }

    private void removeBodyEntity(Body body) {
        Entity loaded = bodyEntities.remove(body.id);
        if (loaded != null && !loaded.isRemoved()) {
            locate(body, loaded);
        }
        if (body.entityId != null) {
            ServerLevel level = level(body.dimension);
            level.getChunk(BlockPos.containing(body.x, body.y, body.z));
            Entity entity = level.getEntity(body.entityId);
            if (entity != null) {
                if (entity instanceof net.minecraft.world.entity.Leashable leashable && leashable.isLeashed()) {
                    leashable.dropLeash();
                }
                entity.discard();
            }
            body.entityId = null;
        }
    }

    public void entityLoaded(Entity entity) {
        if (entity instanceof ItemEntity item) {
            PlayerState captive = bottledSlime(item.getItem());
            if (captive != null) {
                item.setUnlimitedLifetime();
                slimeBottleDrops.put(captive.slimeBottleToken, item);
            }
            if (item.getItem().is(TSFItems.SKIN_SUIT) || item.getItem().is(TSFItems.EMPTY_BODY)) {
                item.setCustomName(item.getItem().get(DataComponents.CUSTOM_NAME));
                item.setCustomNameVisible(false);
            }
            Body skin = skinBody(item.getItem());
            if (skin != null && skin.skinned) {
                skinDrops.put(skin.id, item);
                skin.skinDropEntity = item.getUUID();
                if (skin.skinRecoveryAt == 0) {
                    skin.skinRecoveryAt = server.overworld().getGameTime() + 600;
                }
                visualsDirty = true;
            }
        }
        if (entity.entityTags().contains(BODY_TAG)) {
            Body body = bodyForEntity(entity);
            if (body == null) {
                body = placedSkinForEntity(entity);
            }
            if (body == null) {
                entity.discard();
            } else if (entity instanceof EmptyBodyHolder holder) {
                holder.tsf$setEmptyBody(true);
                holder.tsf$setFoldedSkin(body.skinPlaced);
                entity.setCustomNameVisible(false);
                entity.setNoGravity(false);
                ((MannequinAccessor) entity).tsf$setImmovable(false);
                bodyEntities.put(body.id, entity);
                locate(body, entity);
            }
        }
    }

    public void beforeDeath(ServerPlayer player) {
        captureNatureBody(player);
    }

    public void died(ServerPlayer player) {
        PlayerState state = state(player);
        natureBodies.remove(player.getUUID());
        pendingNatureRestores.remove(player.getUUID());
        releaseZipperBody(player);
        state.zipperReturnBody = null;
        state.zipperReturnForm = null;
        state.expulsionAt = 0;
        state.expulsionBody = null;
        clearHypnosis(player);
        if (state.form == Form.OBSERVER) {
            changed();
            return;
        }
        state.normalRespawn = !TSFServerConfig.get().deathLeavesBody;
        if (state.form == Form.HUMAN) {
            saveAbilities(player, state);
            Body body = store.bodies.get(state.body);
            if (body != null) {
                captureTank(body, player);
                for (JsonElement skin : body.skinLayers) {
                    ItemStack stack = decodeItem(skin);
                    if (!player.level().getGameRules().get(GameRules.KEEP_INVENTORY) || !player.getInventory().add(stack)) {
                        player.drop(stack, false, Prediction.SERVER_ONLY);
                    }
                }
                body.skinLayers.clear();
                body.skinOrder.clear();
                locate(body, player);
                body.occupant = state.normalRespawn ? player.getUUID() : null;
                body.inventory.clear();
                body.health = 20;
                body.food = 20;
                if (!state.normalRespawn) {
                    spawnBody(body);
                }
            }
        }
        if (!state.normalRespawn) {
            state.form = Form.SLIME;
            state.body = null;
        }
        changed();
    }

    public void respawned(ServerPlayer player) {
        PlayerState state = state(player);
        if (state.normalRespawn) {
            state.normalRespawn = false;
            Body body = state.form == Form.HUMAN ? store.bodies.get(state.body) : null;
            if (body == null) {
                body = new Body();
                body.id = UUID.randomUUID();
                body.owner = player.getUUID();
                body.name = player.getGameProfile().name();
                body.profile = ResolvableProfile.CODEC.encodeStart(JsonOps.INSTANCE, player.getProfile()).getOrThrow();
                store.bodies.put(body.id, body);
                state.homeBody = body.id;
            }
            state.body = body.id;
            state.form = Form.HUMAN;
            state.observedBody = null;
            state.observerFallback = null;
            body.occupant = player.getUUID();
            restoreAbilities(player, state);
            locate(body, player);
        }
        if (state(player).form != Form.HUMAN && state(player).form != Form.OBSERVER) {
            restoreAbilities(player, state(player));
        }
        applyForm(player);
        if (state(player).form == Form.SLIME) {
            message(player, "respawned");
        }
        changed();
        pendingNatureRestores.add(player.getUUID());
    }

    public void recover(ServerPlayer player) {
        PlayerState captive = state(player);
        if (captive.slimeBottleToken != null) {
            releaseSlime(captive, player.level(), player.position());
        }
        clearHypnosis(player);
        Body body = nativeBody(player);
        UUID controller = body.occupant == null ? body.zipperCarrier : body.occupant;
        if (controller != null && !controller.equals(player.getUUID())) {
            ServerPlayer occupant = server.getPlayerList().getPlayer(controller);
            if (occupant == null) {
                message(player, "occupant_offline");
                return;
            }
            detach(occupant, Form.SLIME);
        }
        if (body.skinned) {
            ServerPlayer wearer = controller(body, new HashSet<>());
            if (wearer != null) {
                captureNatureBody(wearer);
            }
            removeBodyEntity(body);
            body.skinPlaced = false;
            body.skinned = false;
            locate(body, player);
            spawnBody(body);
        }
        if (body.packedToken != null) {
            body.packedToken = null;
            locate(body, player);
            spawnBody(body);
        }
        returnToBody(player);
    }

    public void tick() {
        ticks++;
        boolean recoveryChanged = false;
        updateBodyPositions();
        updateSkinDrops();
        for (UUID id : List.copyOf(pendingProjections)) {
            pendingProjections.remove(id);
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null && canProject(player)) {
                depart(player, Form.SPIRIT);
            }
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerState state = state(player);
            if (pendingNatureRestores.remove(player.getUUID())) {
                natureBodies.remove(player.getUUID());
                updateNatureBody(player);
            }
            if (!player.isUsingItem() || !player.getUseItem().is(TSFItems.PHONE)) {
                cancelHypnosis(player);
            }
            updateSpiritSpears(player);
            if (state.expulsionAt != 0 && server.overworld().getGameTime() >= state.expulsionAt) {
                if (!player.isAlive() || state.form != Form.HUMAN || !java.util.Objects.equals(state.body, state.expulsionBody)) {
                    state.expulsionAt = 0;
                    state.expulsionBody = null;
                    visualsDirty = true;
                } else if (!NeedsOfNatureCompat.isAnimating(player)) {
                    depart(player, Form.SLIME);
                }
            }
            if (state.hypnotist != null) {
                boolean animating = NeedsOfNatureCompat.isAnimating(player);
                if (animating) {
                    state.anchorDimension = player.level().dimension().identifier().toString();
                    state.anchorX = player.getX();
                    state.anchorY = player.getY();
                    state.anchorZ = player.getZ();
                    state.anchorYaw = player.getYRot();
                    state.anchorPitch = player.getXRot();
                }
                if (server.overworld().getGameTime() >= state.hypnosisUntil
                        || server.getPlayerList().getPlayer(state.hypnotist) == null || !player.isAlive()) {
                    clearHypnosis(player);
                    message(player, "hypnosis_released");
                    visualsDirty = true;
                } else if (!animating && restricted(player, HypnosisRestrictions.MOVEMENT)
                        && (player.distanceToSqr(state.anchorX, state.anchorY, state.anchorZ) > 0.01
                        || !player.level().dimension().identifier().toString().equals(state.anchorDimension))) {
                    player.teleportTo(level(state.anchorDimension), state.anchorX, state.anchorY, state.anchorZ,
                            Set.of(), player.getYRot(), player.getXRot(), false);
                    player.setDeltaMovement(Vec3.ZERO);
                }
                if (!animating && restricted(player, HypnosisRestrictions.LOOK)
                        && (Math.abs(net.minecraft.util.Mth.wrapDegrees(player.getYRot() - state.anchorYaw)) > 0.01F
                        || Math.abs(player.getXRot() - state.anchorPitch) > 0.01F)) {
                    player.teleportTo(player.level(), player.getX(), player.getY(), player.getZ(), Set.of(),
                            state.anchorYaw, state.anchorPitch, false);
                }
                NeedsOfNatureHypnosis.acceptPending(player);
                if (ticks % 40 == 0 && hasActiveHypnosis(player)) {
                    player.level().sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + player.getBbHeight() + 0.2,
                            player.getZ(), 3, 0.2, 0.15, 0.2, 0.02);
                }
            }
            if (state.form == Form.SPIRIT) {
                player.noPhysics = false;
                player.fallDistance = 0;
                if (!player.getAbilities().flying || !player.getAbilities().mayfly) {
                    applyForm(player);
                }
            }
            if (ticks % 10 == 0) {
                if (updateSkinLayers(player)) {
                    visualsDirty = true;
                }
                updateNatureBody(player);
                if (natureSkins.capture(nativeBody(player)) | natureSkins.capture(appearance(player))) {
                    visualsDirty = true;
                }
            }
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (updateObserver(player)) {
                visualsDirty = true;
            }
            int remaining = skinRecoverySeconds(player);
            Integer previous = lastSkinRecoverySeconds.put(player.getUUID(), remaining);
            if (previous == null || previous != remaining) {
                recoveryChanged = true;
                if (remaining == 0) {
                    message(player, "skin_recovery_ready");
                }
            }
        }
        if (ticks % 5 == 0) {
            synchronizeObservers();
        }
        if (visualsDirty || recoveryChanged) {
            if (visualsDirty) {
                save();
            }
            visualsDirty = false;
            broadcast();
        }
        if (ticks % 200 == 0) {
            for (Body body : store.bodies.values()) {
                if (body.occupant == null && body.zipperCarrier == null && (!body.skinned || body.skinPlaced)
                        && body.packedToken == null) {
                    ServerLevel level = level(body.dimension);
                    if (level.hasChunkAt(BlockPos.containing(body.x, body.y, body.z))) {
                        spawnBody(body);
                    }
                }
            }
        }
        if (saveRequested) {
            submitSave();
        }
    }

    private List<VisualState> visuals() {
        List<VisualState> visuals = new ArrayList<>();
        Set<UUID> controllers = new HashSet<>();
        for (ServerPlayer observer : server.getPlayerList().getPlayers()) {
            ServerPlayer controller = speechController(observer);
            if (controller != null) {
                controllers.add(controller.getUUID());
            }
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerState state = state(player);
            List<SkinLayer> skins = layers(player);
            boolean wearing = !skins.isEmpty();
            Body appearance = appearance(player);
            visuals.add(new VisualState(player.getUUID(), state.form, state.body,
                    appearance == null ? player.getGameProfile().name() : appearance.name,
                    appearance == null ? null : appearance.profile, state.hypnotist != null, wearing, skins.size(),
                    state.zipperReturnForm != null, skinRecoverySeconds(player), state.slimeColor,
                    state.hypnotist, state.hypnotist == null ? 0 : state.hypnosisRestrictions,
                    appearance == null ? null : appearance.owner,
                    appearance == null || appearance.natureSkin == null ? null : appearance.natureSkin.id,
                    state.observerChatPrivate, controllers.contains(player.getUUID())));
        }
        return visuals;
    }

    public void synchronize(ServerPlayer player, boolean openPhone) {
        if (ServerPlayNetworking.canSend(player, StatePayload.TYPE)) {
            synchronizeNatureSkins(player, natureAppearances());
            ServerPlayNetworking.send(player, new StatePayload(GSON.toJson(visuals()), openPhone));
        }
    }

    private void broadcast() {
        if (!server.getPlayerList().getPlayers().isEmpty()) {
            server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(
                    EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME), server.getPlayerList().getPlayers()));
        }
        String json = GSON.toJson(visuals());
        List<Body> appearances = natureAppearances();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ServerPlayNetworking.canSend(player, StatePayload.TYPE)) {
                synchronizeNatureSkins(player, appearances);
                ServerPlayNetworking.send(player, new StatePayload(json, false));
            }
        }
    }

    private List<Body> natureAppearances() {
        return server.getPlayerList().getPlayers().stream().map(this::appearance)
                .filter(java.util.Objects::nonNull).toList();
    }

    private void synchronizeNatureSkins(ServerPlayer viewer, List<Body> appearances) {
        for (Body body : appearances) {
            natureSkins.synchronize(viewer, body);
        }
    }

    private void refreshInventory(ServerPlayer player) {
        player.getInventory().setChanged();
        player.inventoryMenu.sendAllDataToRemote();
        player.connection.send(new ClientboundSetHeldSlotPacket(player.getInventory().getSelectedSlot()));
    }

    private void synchronizeObservers() {
        Set<UUID> updated = new HashSet<>();
        var operations = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!ServerPlayNetworking.canSend(player, ObserverPayload.TYPE)) {
                observerTargets.remove(player.getUUID());
                continue;
            }
            ServerPlayer target = state(player).form == Form.OBSERVER && player.getCamera() instanceof ServerPlayer camera
                    && camera != player && state(camera).form == Form.HUMAN ? camera : null;
            if (target == null) {
                if (observerTargets.remove(player.getUUID()) != null) {
                    ServerPlayNetworking.send(player, new ObserverPayload("null"));
                }
                continue;
            }
            ObserverSync sync = observerSnapshots.computeIfAbsent(target.getUUID(), ObserverSync::new);
            if (updated.add(target.getUUID())) {
                ObserverInventory.Status status = new ObserverInventory.Status(target.getInventory().getSelectedSlot(),
                        target.getHealth(), target.getAbsorptionAmount(), target.getFoodData().getFoodLevel(),
                        target.getFoodData().getSaturationLevel(), target.experienceProgress, target.experienceLevel,
                        target.totalExperience, target.isUsingItem() ? target.getUsedItemHand() : null,
                        target.getUseItemRemainingTicks());
                sync.update(target.getInventory(), slot -> target.inventoryMenu.getSlot(slot).getItem(), status,
                        stack -> ItemStack.OPTIONAL_CODEC.encodeStart(operations, stack).getOrThrow());
            }
            String json = observerTargets.get(player.getUUID()) == target ? sync.deltaJson() : sync.fullJson();
            if (json != null) {
                ServerPlayNetworking.send(player, new ObserverPayload(json));
            }
            observerTargets.put(player.getUUID(), target);
        }
        observerSnapshots.keySet().retainAll(updated);
    }

    private void updateSkinDrops() {
        for (var entry : List.copyOf(skinDrops.entrySet())) {
            Body body = store.bodies.get(entry.getKey());
            ItemEntity item = entry.getValue();
            if (body == null || !body.skinned || skinBody(item.getItem()) != body || body.skinPlaced) {
                skinDrops.remove(entry.getKey());
                if (body != null && !body.skinPlaced && item.getUUID().equals(body.skinDropEntity)) {
                    body.skinDropEntity = null;
                    body.skinRecoveryAt = 0;
                    visualsDirty = true;
                }
            } else if (item.isRemoved()) {
                skinDrops.remove(entry.getKey());
                if (item.getRemovalReason() != Entity.RemovalReason.UNLOADED_TO_CHUNK) {
                    body.skinDropEntity = null;
                    body.skinRecoveryAt = 0;
                    visualsDirty = true;
                }
            } else {
                body.dimension = item.level().dimension().identifier().toString();
                body.x = item.getX();
                body.y = item.getY();
                body.z = item.getZ();
            }
        }
        for (Body body : store.bodies.values()) {
            if (body.skinned && body.skinPlaced && body.skinRecoveryAt == 0) {
                body.skinRecoveryAt = server.overworld().getGameTime() + 600;
                visualsDirty = true;
            }
            if (body.skinned && !body.skinPlaced && body.skinRecoveryAt != 0 && controller(body, new HashSet<>()) != null) {
                skinDrops.remove(body.id);
                body.skinDropEntity = null;
                body.skinRecoveryAt = 0;
                visualsDirty = true;
            }
        }
    }

    private void changed() {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            updateSkinLayers(player);
            updateNatureBody(player);
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            updateObserver(player);
        }
        visualsDirty = true;
        save();
    }

    private boolean updateSkinLayers(ServerPlayer player) {
        List<SkinLayer> worn = layers(player);
        int limit = TSFServerConfig.get().maxSkinLayers;
        if (worn.size() > limit) {
            for (int index = worn.size() - 1; index >= limit; index--) {
                SkinLayer excess = worn.get(index);
                ItemStack removed = excess.stack.copy();
                excess.remove.run();
                if (!player.getInventory().add(removed)) {
                    player.drop(removed, false, Prediction.SERVER_ONLY);
                }
            }
            refreshInventory(player);
            message(player, "skin_layer_limit", limit);
        }
        List<UUID> skins = layers(player).stream().map(layer -> layer.body.id).toList();
        List<UUID> previous = lastSkinLayers.put(player.getUUID(), skins);
        if (skins.equals(previous)) {
            return false;
        }
        if (previous != null && !skins.isEmpty() && skins.size() >= previous.size()) {
            playAnimation(player, BodyAnimation.WEAR_SKIN);
        }
        return true;
    }

    private void playAnimation(ServerPlayer player, BodyAnimation animation) {
        NeedsOfNatureCompat.play(player, animation);
        AnimationPayload payload = new AnimationPayload(player.getUUID(), animation);
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (viewer.level() == player.level() && ServerPlayNetworking.canSend(viewer, AnimationPayload.TYPE)) {
                ServerPlayNetworking.send(viewer, payload);
            }
        }
    }

    private void captureTank(Body body, ServerPlayer player) {
        NatureTank tank = NeedsOfNatureTanks.capture(player);
        if (tank != null) {
            body.natureTank = tank;
        }
    }

    private NatureAppearance freshNatureBody(ServerPlayer player) {
        if (!NeedsOfNatureBodies.available()) {
            return null;
        }
        Body home = nativeBody(player);
        NatureAppearance fresh = new NatureAppearance();
        if (home.natureAppearance != null) {
            fresh.gender = home.natureAppearance.gender;
        }
        return fresh;
    }

    private void captureNatureBody(ServerPlayer player) {
        Body body = store.bodies.get(natureBodies.get(player.getUUID()));
        if (body != null) {
            if (natureSkins.capture(body)) {
                visualsDirty = true;
            }
            NatureAppearance appearance = NeedsOfNatureBodies.capture(player);
            if (appearance != null) {
                body.natureAppearance = appearance;
            }
        }
    }

    private void updateNatureBody(ServerPlayer player) {
        if (!NeedsOfNatureBodies.available() || !player.isAlive()) {
            return;
        }
        Body body = appearance(player);
        UUID next = body == null ? null : body.id;
        UUID previous = natureBodies.get(player.getUUID());
        if (natureBodies.containsKey(player.getUUID()) && java.util.Objects.equals(previous, next)) {
            return;
        }
        captureNatureBody(player);
        if (body != null && body.natureAppearance == null) {
            body.natureAppearance = new NatureAppearance();
            Body original = store.bodies.get(body.owner);
            if (original != null && original.natureAppearance != null) {
                body.natureAppearance.gender = original.natureAppearance.gender;
            }
        }
        NeedsOfNatureBodies.restore(player, body == null ? null : body.natureAppearance);
        if (natureSkins.capture(body)) {
            visualsDirty = true;
        }
        natureBodies.put(player.getUUID(), next);
    }

    private void updateBodyPositions() {
        for (var entry : List.copyOf(bodyEntities.entrySet())) {
            Body body = store.bodies.get(entry.getKey());
            Entity entity = entry.getValue();
            if (body == null || !entity.getUUID().equals(body.entityId)) {
                bodyEntities.remove(entry.getKey());
            } else {
                locate(body, entity);
                if (entity.isRemoved()) {
                    bodyEntities.remove(entry.getKey());
                }
            }
        }
    }

    public void save() {
        saveRequested = true;
    }

    public void save(boolean flush) {
        submitSave();
        if (flush) {
            try {
                stateWriter.flush();
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to save TSF state: " + path, exception);
            }
        }
    }

    private void submitSave() {
        updateBodyPositions();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            captureNatureBody(player);
            PlayerState state = state(player);
            Body body = store.bodies.get(state.body);
            if (state.form == Form.HUMAN && body != null && player.getUUID().equals(body.occupant)) {
                captureTank(body, player);
            }
        }
        stateWriter.submit(store.snapshot());
        saveRequested = false;
    }
}
