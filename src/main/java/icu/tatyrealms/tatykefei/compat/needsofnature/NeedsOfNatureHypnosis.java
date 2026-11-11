package icu.tatyrealms.tatykefei.compat.needsofnature;

import icu.tatyrealms.tatykefei.body.BodyManager;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.LoggerFactory;

public final class NeedsOfNatureHypnosis {
    private static Object automaticMode;
    private static int automaticBit;
    private static Method config;
    private static Method allowedModes;
    private static Field requests;
    private static Method requestId;
    private static Method consentPlayers;
    private static Method acceptedPlayers;
    private static Method accept;
    private static Constructor<?> response;

    private NeedsOfNatureHypnosis() {
    }

    public static void initialize() {
        if (!FabricLoader.getInstance().isModLoaded("needsofnature")) {
            return;
        }
        try {
            Class<?> modes = Class.forName("com.nonid.NonPlayerInteractionProfiles$Mode");
            automaticMode = modes.getField("AUTO_ACCEPT").get(null);
            automaticBit = (int) modes.getMethod("bit").invoke(automaticMode);
            config = Class.forName("com.nonid.NeedsOfNature").getMethod("getConfig");
            allowedModes = config.getReturnType().getMethod("getAllowedPlayerInteractionProfilesMask");
        } catch (ReflectiveOperationException | LinkageError exception) {
            automaticMode = null;
            LoggerFactory.getLogger("TSF").warn("NeedsOfNature hypnosis request integration is unavailable", exception);
            return;
        }
        try {
            Class<?> interactions = Class.forName("com.nonid.NonPlayerInteractionSystem");
            Class<?> pending = Class.forName("com.nonid.NonPlayerInteractionSystem$PendingRequest");
            Class<?> payload = Class.forName("com.nonid.network.PlayerAnimationConsentC2SPayload");
            requests = interactions.getDeclaredField("REQUESTS");
            requests.setAccessible(true);
            requestId = pending.getDeclaredMethod("id");
            consentPlayers = pending.getDeclaredMethod("consentPlayers");
            acceptedPlayers = pending.getDeclaredMethod("acceptedPlayers");
            for (Method method : List.of(requestId, consentPlayers, acceptedPlayers)) {
                method.setAccessible(true);
            }
            accept = interactions.getDeclaredMethod("handleConsent", ServerPlayer.class, payload);
            accept.setAccessible(true);
            response = payload.getConstructor(UUID.class, boolean.class);
        } catch (ReflectiveOperationException | LinkageError exception) {
            requests = null;
            LoggerFactory.getLogger("TSF").warn("NeedsOfNature pending request integration is unavailable", exception);
        }
    }

    public static Object requestMode(ServerPlayer player, Object original) {
        BodyManager manager = BodyManager.get();
        if (automaticMode == null || manager == null || !manager.hasActiveHypnosis(player)) {
            return original;
        }
        try {
            int allowed = (int) allowedModes.invoke(config.invoke(null));
            return (allowed & automaticBit) != 0 ? automaticMode : original;
        } catch (ReflectiveOperationException | LinkageError exception) {
            automaticMode = null;
            LoggerFactory.getLogger("TSF").warn("Unable to resolve hypnotized animation request mode", exception);
            return original;
        }
    }

    public static void acceptPending(ServerPlayer player) {
        if (requests == null || requestMode(player, null) == null) {
            return;
        }
        try {
            Map<?, ?> pending = (Map<?, ?>) requests.get(null);
            for (Object request : List.copyOf(pending.values())) {
                Set<?> consent = (Set<?>) consentPlayers.invoke(request);
                Set<?> accepted = (Set<?>) acceptedPlayers.invoke(request);
                if (consent.contains(player.getUUID()) && !accepted.contains(player.getUUID())) {
                    accept.invoke(null, player, response.newInstance(requestId.invoke(request), true));
                }
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            requests = null;
            LoggerFactory.getLogger("TSF").warn("Unable to accept pending hypnotized animation requests", exception);
        }
    }
}
