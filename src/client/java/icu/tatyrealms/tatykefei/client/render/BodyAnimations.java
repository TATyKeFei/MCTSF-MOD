package icu.tatyrealms.tatykefei.client.render;

import icu.tatyrealms.tatykefei.network.AnimationPayload;
import icu.tatyrealms.tatykefei.network.BodyAnimation;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;

public final class BodyAnimations {
    private static final Map<UUID, Playback> PLAYING = new HashMap<>();

    private BodyAnimations() {
    }

    public static void receive(AnimationPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null) {
            PLAYING.put(payload.player(), new Playback(payload.animation(), client.level.getGameTime()));
        }
    }

    public static BodyAnimationPose.Frame frame(UUID player, float partialTick) {
        Playback playback = PLAYING.get(player);
        Minecraft client = Minecraft.getInstance();
        if (playback == null || client.level == null) {
            return null;
        }
        float progress = (client.level.getGameTime() - playback.started() + partialTick) / playback.animation().duration();
        return progress < 0 || progress >= 1 ? null : new BodyAnimationPose.Frame(playback.animation(), progress);
    }

    public static void tick(Minecraft client) {
        if (client.level == null) {
            clear();
            return;
        }
        PLAYING.entrySet().removeIf(entry -> client.level.getGameTime() - entry.getValue().started() >= entry.getValue().animation().duration());
        for (var entry : PLAYING.entrySet()) {
            var player = client.level.getPlayerByUUID(entry.getKey());
            if (player == null) {
                continue;
            }
            Playback playback = entry.getValue();
            double progress = (client.level.getGameTime() - playback.started()) / (double) playback.animation().duration();
            double angle = progress * Math.PI * 6;
            ParticleOptions particle = switch (playback.animation()) {
                case POSSESS -> ParticleTypes.SOUL;
                case TAKEOVER -> ParticleTypes.PORTAL;
                case WEAR_SKIN -> ParticleTypes.ENCHANT;
                case ENTER_BODY -> ParticleTypes.ITEM_SLIME;
            };
            for (int side = 0; side < 2; side++) {
                double rotation = angle + side * Math.PI;
                client.level.addParticle(particle, player.getX() + Math.cos(rotation) * 0.45,
                        player.getY() + 0.2 + progress * 1.5, player.getZ() + Math.sin(rotation) * 0.45, 0, 0.015, 0);
            }
        }
    }

    public static void clear() {
        PLAYING.clear();
    }

    private record Playback(BodyAnimation animation, long started) {
    }
}
