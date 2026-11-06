package icu.tatyrealms.tatykefei.client;

import com.google.gson.Gson;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.serialization.JsonOps;
import icu.tatyrealms.tatykefei.TSF;
import icu.tatyrealms.tatykefei.client.render.SkinSuitRenderer;
import icu.tatyrealms.tatykefei.network.Action;
import icu.tatyrealms.tatykefei.network.ActionPayload;
import icu.tatyrealms.tatykefei.network.AnimationPayload;
import icu.tatyrealms.tatykefei.network.StatePayload;
import icu.tatyrealms.tatykefei.network.VisualState;
import icu.tatyrealms.tatykefei.network.ObserverPayload;
import icu.tatyrealms.tatykefei.network.SettingsPayload;
import icu.tatyrealms.tatykefei.state.BodyManager;
import icu.tatyrealms.tatykefei.state.Form;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;

public final class TSFClient implements ClientModInitializer {
    private static final Gson GSON = new Gson();
    private static final Map<UUID, VisualState> VISUALS = new HashMap<>();
    private static final Map<UUID, ResolvableProfile> PROFILES = new HashMap<>();
    private static final Map<UUID, Slime> SLIMES = new HashMap<>();

    @Override
    public void onInitializeClient() {
        NeedsOfNatureClientCompat.initialize();
        SpecialModelRenderers.ID_MAPPER.put(TSF.id("skin_suit"), SkinSuitRenderer.Unbaked.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(AnimationPayload.TYPE, (payload, context) -> BodyAnimations.receive(payload));
        ClientPlayNetworking.registerGlobalReceiver(ObserverPayload.TYPE, (payload, context) -> ObserverView.receive(payload.json()));
        ClientPlayNetworking.registerGlobalReceiver(SettingsPayload.TYPE, (payload, context) -> {
            if (context.client().gui.screen() instanceof TSFSettingsScreen screen) {
                screen.receive(payload);
            }
        });
        KeyMapping.Category controls = KeyMapping.Category.register(TSF.id("controls"));
        KeyMapping phone = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.tsf.phone", InputConstants.Type.KEYBOARD,
                InputConstants.KEY_R, controls));
        KeyMapping undress = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.tsf.undress", InputConstants.Type.KEYBOARD,
                InputConstants.KEY_U, controls));
        ClientPlayNetworking.registerGlobalReceiver(StatePayload.TYPE, (payload, context) -> {
            VISUALS.clear();
            PROFILES.clear();
            for (VisualState visual : GSON.fromJson(payload.json(), VisualState[].class)) {
                VISUALS.put(visual.player(), visual);
                if (visual.profile() != null) {
                    ResolvableProfile.CODEC.parse(JsonOps.INSTANCE, visual.profile()).result()
                            .ifPresent(profile -> PROFILES.put(visual.player(), profile));
                }
            }
            SLIMES.keySet().removeIf(player -> !VISUALS.containsKey(player) || VISUALS.get(player).form() != Form.SLIME);
            if (payload.openPhone()) {
                context.client().gui.setScreen(new PhoneScreen());
            } else if (context.client().gui.screen() instanceof PhoneScreen screen) {
                screen.refresh();
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            BodyAnimations.tick(client);
            ObserverView.updateInventory();
            if (client.level != null) {
                for (var player : client.level.players()) {
                    if (BodyManager.form(player) == Form.SLIME) {
                        Slime slime = slime(player);
                        slime.oSquish = slime.squish;
                        float target = player.isShiftKeyDown() || player.isCrouching() ? -0.6F : 0.0F;
                        slime.squish += (target - slime.squish) * 0.35F;
                    }
                }
            }
            while (phone.consumeClick()) {
                if (client.player != null && client.gui.screen() == null) {
                    send(Action.OPEN, null);
                }
            }
            while (undress.consumeClick()) {
                if (client.player != null && client.gui.screen() == null) {
                    send(Action.UNDRESS, null);
                }
            }
            if (client.player != null && BodyManager.form(client.player) == Form.SPIRIT) {
                client.player.noPhysics = true;
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            VISUALS.clear();
            PROFILES.clear();
            SLIMES.clear();
            BodyAnimations.clear();
            ObserverView.clear();
        });
    }

    public static void send(Action action, UUID target) {
        if (ClientPlayNetworking.canSend(ActionPayload.TYPE)) {
            ClientPlayNetworking.send(new ActionPayload(action, target == null ? "" : target.toString()));
        }
    }

    public static void sendColor(String hex) {
        if (ClientPlayNetworking.canSend(ActionPayload.TYPE)) {
            ClientPlayNetworking.send(new ActionPayload(Action.SLIME_COLOR, hex));
        }
    }

    public static VisualState visual(UUID player) {
        return VISUALS.get(player);
    }

    public static PlayerSkin skin(UUID player) {
        ResolvableProfile profile = PROFILES.get(player);
        return profile == null ? null : Minecraft.getInstance().playerSkinRenderCache().getOrDefault(profile).playerSkin();
    }

    public static Slime slime(AbstractClientPlayer player) {
        Slime slime = SLIMES.computeIfAbsent(player.getUUID(), ignored -> new Slime(EntityTypes.SLIME, player.level()));
        if (slime.level() != player.level()) {
            slime = new Slime(EntityTypes.SLIME, player.level());
            SLIMES.put(player.getUUID(), slime);
        }
        slime.setId(player.getId());
        slime.setUUID(player.getUUID());
        slime.setSize(1, false);
        slime.copyPosition(player);
        slime.setOldPosAndRot(player.oldPosition(), player.yRotO, player.xRotO);
        slime.setDeltaMovement(player.getDeltaMovement());
        slime.setOnGround(player.onGround());
        slime.tickCount = player.tickCount;
        slime.yBodyRot = player.yBodyRot;
        slime.yBodyRotO = player.yBodyRotO;
        slime.yHeadRot = player.yHeadRot;
        slime.yHeadRotO = player.yHeadRotO;
        slime.hurtTime = player.hurtTime;
        slime.deathTime = player.deathTime;
        slime.setInvisible(player.isInvisible());
        slime.setCustomName(player.getDisplayName());
        slime.setCustomNameVisible(true);
        return slime;
    }
}
