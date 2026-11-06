package icu.tatyrealms.tatykefei.compat;

import icu.tatyrealms.tatykefei.network.BodyAnimation;
import icu.tatyrealms.tatykefei.state.BodyManager;
import icu.tatyrealms.tatykefei.state.Form;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.LoggerFactory;

public final class NeedsOfNatureCompat {
    private static Method active;
    private static Method start;
    private static Method defaults;
    private static Method findInstance;
    private static Method defeated;
    private static Method stop;
    private static Method clearQueue;

    private NeedsOfNatureCompat() {
    }

    public static void initialize() {
        if (!FabricLoader.getInstance().isModLoaded("needsofnature")) {
            return;
        }
        try {
            Class<?> api = Class.forName("com.nonid.api.animation.NonAnimationApi");
            Class<?> options = Class.forName("com.nonid.api.animation.NonAnimationApi$StartOptions");
            Class<?> context = Class.forName("com.nonid.api.animation.NonAnimationEvents$BeforeStartContext");
            Method world = context.getMethod("world");
            Method actors = context.getMethod("actorUuids");
            registerListener("com.nonid.api.animation.NonAnimationEvents", "BEFORE_START",
                    "com.nonid.api.animation.NonAnimationEvents$BeforeStart", arguments -> {
                        try {
                            ServerLevel level = (ServerLevel) world.invoke(arguments[0]);
                            for (Object actor : (List<?>) actors.invoke(arguments[0])) {
                                Entity entity = level.getEntity((UUID) actor);
                                if (BodyManager.emptyBody(entity)
                                        || entity instanceof Player player && BodyManager.form(player) != Form.HUMAN) {
                                    return false;
                                }
                            }
                        } catch (ReflectiveOperationException exception) {
                            warn(exception);
                        }
                        return true;
                    });
            active = api.getMethod("isActorPendingOrActive", ServerLevel.class, UUID.class);
            start = api.getMethod("start", ServerLevel.class, Identifier.class, List.class, List.class, options);
            defaults = options.getMethod("defaults");
            Class<?> needs = Class.forName("com.nonid.NeedsOfNature");
            findInstance = needs.getMethod("findActiveInstanceContainingActor", ServerLevel.class, UUID.class);
            defeated = needs.getMethod("isDefeatedInstance", ServerLevel.class, UUID.class);
            stop = api.getMethod("stop", ServerLevel.class, UUID.class);
            clearQueue = api.getMethod("clearQueue", ServerLevel.class, ServerPlayer.class);
            LoggerFactory.getLogger("TSF").info("Enabled NeedsOfNature animation integration");
        } catch (ReflectiveOperationException | LinkageError exception) {
            active = null;
            start = null;
            warn(exception);
        }
    }

    public static boolean canChangeBody(ServerPlayer player) {
        if (isAnimating(player)) {
            BodyManager.message(player, "animation_busy");
            return false;
        }
        return true;
    }

    public static boolean isAnimating(ServerPlayer player) {
        if (active != null) {
            try {
                return Boolean.TRUE.equals(active.invoke(null, player.level(), player.getUUID()));
            } catch (ReflectiveOperationException | LinkageError exception) {
                active = null;
                warn(exception);
            }
        }
        return false;
    }

    public static boolean supportsDowned() {
        return findInstance != null && defeated != null && stop != null;
    }

    public static boolean isDowned(ServerPlayer player) {
        if (supportsDowned()) {
            try {
                UUID instance = (UUID) findInstance.invoke(null, player.level(), player.getUUID());
                return instance != null && Boolean.TRUE.equals(defeated.invoke(null, player.level(), instance));
            } catch (ReflectiveOperationException | LinkageError exception) {
                warn(exception);
            }
        }
        return false;
    }

    public static boolean finishDowned(ServerPlayer player) {
        if (!isDowned(player)) {
            return canChangeBody(player);
        }
        try {
            UUID instance = (UUID) findInstance.invoke(null, player.level(), player.getUUID());
            return Boolean.TRUE.equals(stop.invoke(null, player.level(), instance)) && canChangeBody(player);
        } catch (ReflectiveOperationException | LinkageError exception) {
            warn(exception);
            return false;
        }
    }

    public static void endSession(ServerPlayer player) {
        if (findInstance != null && stop != null) {
            try {
                if (clearQueue != null) {
                    clearQueue.invoke(null, player.level(), player);
                }
                UUID instance = (UUID) findInstance.invoke(null, player.level(), player.getUUID());
                if (instance != null) {
                    stop.invoke(null, player.level(), instance);
                }
            } catch (ReflectiveOperationException | LinkageError exception) {
                warn(exception);
            }
        }
    }

    public static void play(ServerPlayer player, BodyAnimation animation) {
        if (start != null) {
            try {
                start.invoke(null, player.level(), animation.id(), List.of(player), List.of("player"), defaults.invoke(null));
            } catch (ReflectiveOperationException | LinkageError exception) {
                start = null;
                warn(exception);
            }
        }
    }

    public static void registerListener(String owner, String field, String listener, Function<Object[], Object> callback)
            throws ReflectiveOperationException {
        Class<?> listenerType = Class.forName(listener);
        Object proxy = Proxy.newProxyInstance(listenerType.getClassLoader(), new Class<?>[]{listenerType},
                (instance, method, arguments) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "hashCode" -> System.identityHashCode(instance);
                            case "equals" -> instance == arguments[0];
                            default -> "TSF NeedsOfNature bridge";
                        };
                    }
                    return callback.apply(arguments);
                });
        Event.class.getMethod("register", Object.class).invoke(Class.forName(owner).getField(field).get(null), proxy);
    }

    public static void warn(Throwable exception) {
        LoggerFactory.getLogger("TSF").warn("NeedsOfNature integration unavailable; TSF built-in animations remain available", exception);
    }
}
