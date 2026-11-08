package icu.tatyrealms.tatykefei.client;

import icu.tatyrealms.tatykefei.compat.NeedsOfNatureCompat;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.phys.Vec3;

public final class NeedsOfNatureObserver {
    private static Method active;
    private static Method simplified;
    private static Method latestInstance;
    private static Method resolvePose;
    private static Method poseInstance;
    private static Method posePosition;
    private static Method usablePosition;
    private static Method resolveCollision;

    private NeedsOfNatureObserver() {
    }

    public static void initialize() {
        try {
            Class<?> runtime = Class.forName("com.nonid.internal.animation.client.runtime.ClientAnimationRuntime");
            active = runtime.getMethod("isActorPendingOrActive", UUID.class);
            simplified = runtime.getMethod("isActorUsingSimplifiedPresentation", UUID.class);
            latestInstance = runtime.getMethod("findLatestActiveInstanceContaining", UUID.class);
            Class<?> tracker = Class.forName("com.nonid.internal.animation.client.camera.AnimatedCameraPoseTracker");
            resolvePose = tracker.getMethod("resolve", UUID.class, long.class);
            Class<?> sample = resolvePose.getReturnType();
            poseInstance = sample.getMethod("instanceId");
            posePosition = sample.getMethod("worldPos");
            usablePosition = Class.forName("com.nonid.internal.animation.client.camera.CameraPositionSafety")
                    .getMethod("isUsableNear", Vec3.class, Vec3.class);
            resolveCollision = Class.forName("com.nonid.internal.animation.client.camera.FirstPersonCameraCollision")
                    .getMethod("resolve", CollisionGetter.class, Vec3.class);
            Constructor<?> override = Class.forName("com.nonid.api.client.NonAnimationRenderEvents$RenderOverride")
                    .getConstructor(Identifier.class, Identifier.class, List.class, Map.class, Map.class, Map.class);
            Object hiddenHead = override.newInstance(null, null, List.of(), Map.of(), Map.of(), Map.of("head", false));
            NeedsOfNatureCompat.registerListener("com.nonid.api.client.NonAnimationRenderEvents", "RESOLVE_RENDER",
                    "com.nonid.api.client.NonAnimationRenderEvents$RenderResolver", arguments -> {
                        AbstractClientPlayer player = ObserverView.target();
                        return player != null && arguments[0] == player && rendersBody(player)
                                && Minecraft.getInstance().options.getCameraType().isFirstPerson() ? hiddenHead : null;
                    });
        } catch (ReflectiveOperationException | LinkageError exception) {
            disable(exception);
        }
    }

    public static boolean isAnimating(AbstractClientPlayer player) {
        if (active != null && player != null) {
            try {
                return Boolean.TRUE.equals(active.invoke(null, player.getUUID()));
            } catch (ReflectiveOperationException | LinkageError exception) {
                disable(exception);
            }
        }
        return false;
    }

    public static boolean rendersBody(AbstractClientPlayer player) {
        if (isAnimating(player)) {
            try {
                return !Boolean.TRUE.equals(simplified.invoke(null, player.getUUID()));
            } catch (ReflectiveOperationException | LinkageError exception) {
                disable(exception);
            }
        }
        return false;
    }

    public static Vec3 cameraPosition(AbstractClientPlayer player, float partialTick) {
        if (rendersBody(player)) {
            try {
                UUID instance = (UUID) latestInstance.invoke(null, player.getUUID());
                Object pose = resolvePose.invoke(null, player.getUUID(), player.level().getGameTime());
                if (instance == null || pose == null || !Objects.equals(instance, poseInstance.invoke(pose))) {
                    return null;
                }
                Vec3 position = (Vec3) posePosition.invoke(pose);
                if (!Boolean.TRUE.equals(usablePosition.invoke(null, position, player.getEyePosition(partialTick)))) {
                    return null;
                }
                Vec3 look = Vec3.directionFromRotation(player.getViewXRot(partialTick), player.getViewYRot(partialTick));
                return (Vec3) resolveCollision.invoke(null, player.level(), position.add(look.scale(0.2)));
            } catch (ReflectiveOperationException | LinkageError exception) {
                disable(exception);
            }
        }
        return null;
    }

    private static void disable(Throwable exception) {
        active = null;
        NeedsOfNatureCompat.warn(exception);
    }
}
