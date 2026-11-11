package icu.tatyrealms.tatykefei.compat.carbonchat;

import icu.tatyrealms.tatykefei.body.BodyManager;
import icu.tatyrealms.tatykefei.network.VisualState;
import icu.tatyrealms.tatykefei.state.Form;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.LoggerFactory;

public final class CarbonChatCompat {
    private static volatile Map<UUID, TextComponent> names = Map.of();
    private static volatile MinecraftServer server;
    private static Object chatHandler;
    private static Method handleChat;
    private static Method playerId;
    private static Class<?> consolePlayer;
    private static boolean warned;
    private static final ThreadLocal<UUID> FORCED_SPEAKER = new ThreadLocal<>();

    private CarbonChatCompat() {
    }

    public static void initialize() {
        if (!FabricLoader.getInstance().isModLoaded("carbonchat")) {
            return;
        }
        ServerLifecycleEvents.SERVER_STARTING.register(starting -> server = starting);
        ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> {
            server = null;
            names = Map.of();
        });
    }

    public static void registerChatHandler(Object handler) {
        try {
            handleChat = handler.getClass().getMethod("allowChatMessage", PlayerChatMessage.class,
                    ServerPlayer.class, ChatType.Bound.class);
            playerId = Class.forName("net.draycia.carbon.api.users.CarbonPlayer").getMethod("uuid");
            consolePlayer = Class.forName("net.draycia.carbon.common.users.ConsoleCarbonPlayer");
            chatHandler = handler;
            LoggerFactory.getLogger("TSF").info("Enabled CarbonChat integration");
        } catch (ReflectiveOperationException | LinkageError exception) {
            warn(exception);
        }
    }

    public static void updateIdentities(List<VisualState> visuals) {
        if (!FabricLoader.getInstance().isModLoaded("carbonchat")) {
            return;
        }
        Map<UUID, TextComponent> next = new HashMap<>();
        for (VisualState visual : visuals) {
            if (visual.form() == Form.HUMAN && visual.appearanceOwner() != null
                    && !visual.appearanceOwner().equals(visual.player())) {
                next.put(visual.player(), Component.text(visual.bodyName()));
            }
        }
        // Carbon may render messages off-thread; publish only detached, immutable identities.
        names = Map.copyOf(next);
    }

    public static Component assumedName(UUID player) {
        return names.get(player);
    }

    public static String assumedUsername(Object sender, String original) {
        try {
            TextComponent name = names.get((UUID) playerId.invoke(sender));
            return name == null ? original : name.content();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warn(exception);
            return original;
        }
    }

    public static void deliverConsoleMessage(Audience recipient, Component message) {
        if (isConsole(recipient)) {
            // Fabric's handler renders this wrapper, but only sends to raw command sources and players.
            recipient.sendMessage(message);
        }
    }

    private static boolean isConsole(Audience recipient) {
        return consolePlayer != null && consolePlayer.isInstance(recipient);
    }

    public static boolean speakAsHypnotized(ServerPlayer actor, ServerPlayer target, String text) {
        if (!FabricLoader.getInstance().isModLoaded("carbonchat")) {
            return false;
        }
        UUID previous = FORCED_SPEAKER.get();
        try {
            if (chatHandler == null || handleChat == null) {
                throw new IllegalStateException("CarbonChat chat handler is unavailable");
            }
            FORCED_SPEAKER.set(target.getUUID());
            handleChat.invoke(chatHandler, PlayerChatMessage.unsigned(target.getUUID(), text), target,
                    ChatType.bind(ChatType.CHAT, target));
        } catch (ReflectiveOperationException | LinkageError | IllegalStateException exception) {
            warn(exception);
            BodyManager.message(actor, "carbon_chat_unavailable");
        } finally {
            if (previous == null) {
                FORCED_SPEAKER.remove();
            } else {
                FORCED_SPEAKER.set(previous);
            }
        }
        // Never fall back to a vanilla broadcast that bypasses Carbon's channel and mute rules.
        return true;
    }

    public static boolean allowPrivateMessage(Object sender, Object recipient) {
        MinecraftServer current = server;
        if (current == null || current.isStopped()) {
            return false;
        }
        try {
            UUID from = (UUID) playerId.invoke(sender);
            UUID to = (UUID) playerId.invoke(recipient);
            return onServer(current, () -> {
                ServerPlayer player = current.getPlayerList().getPlayer(from);
                return player == null || BodyManager.get() != null && BodyManager.get().allowPrivateMessage(player, to);
            });
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warn(exception);
            return false;
        }
    }

    public static boolean allowChannelMessage(Object sender, Component message) {
        MinecraftServer current = server;
        if (current == null || current.isStopped()) {
            return false;
        }
        try {
            UUID from = (UUID) playerId.invoke(sender);
            if (from.equals(FORCED_SPEAKER.get())) {
                return true;
            }
            StringBuilder content = new StringBuilder();
            for (Component part : message.iterable(net.kyori.adventure.text.ComponentIteratorType.DEPTH_FIRST)) {
                if (part instanceof net.kyori.adventure.text.TextComponent text) {
                    content.append(text.content());
                }
            }
            String text = content.toString();
            return onServer(current, () -> {
                ServerPlayer player = current.getPlayerList().getPlayer(from);
                return player == null || BodyManager.get() != null
                        && !BodyManager.restricted(player, icu.tatyrealms.tatykefei.state.HypnosisRestrictions.SPEECH)
                        && BodyManager.get().allowChat(player, PlayerChatMessage.unsigned(from, text));
            });
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warn(exception);
            return false;
        }
    }

    private static boolean onServer(MinecraftServer current, BooleanSupplier action) {
        return current.isSameThread() ? action.getAsBoolean() : current.submit(action::getAsBoolean).join();
    }

    private static void warn(Throwable exception) {
        if (!warned) {
            warned = true;
            LoggerFactory.getLogger("TSF").warn("Unable to integrate CarbonChat", exception);
        }
    }
}
