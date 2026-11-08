package icu.tatyrealms.tatykefei.state;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;

public final class ObserverChat {
    private static final Set<String> SPEECH_COMMANDS = Set.of("msg", "tell", "w", "say", "me", "teammsg", "tm");

    private ObserverChat() {
    }

    public static boolean allowChat(ServerPlayer sender, PlayerChatMessage message, ServerPlayer controller,
                                    boolean privateOnly, List<ServerPlayer> observers) {
        boolean prefixed = message.signedContent().startsWith("@!");
        if (!prefixed && (controller == null || !privateOnly)) {
            return true;
        }
        String content = message.filterMask().apply(message.signedContent());
        if (content == null) {
            return false;
        }
        content = prefixed ? content.substring(2).strip() : content;
        if (content.isBlank()) {
            return false;
        }
        List<ServerPlayer> recipients = controller == null ? observers : List.of(controller);
        if (recipients.isEmpty()) {
            BodyManager.message(sender, "observer_chat_no_target");
            return false;
        }
        Component privateMessage = Component.translatable("chat.tsf.observer_private", sender.getGameProfile().name(), content)
                .withStyle(ChatFormatting.AQUA);
        sender.sendSystemMessage(privateMessage);
        for (ServerPlayer recipient : recipients) {
            if (recipient != sender) {
                recipient.sendSystemMessage(privateMessage);
            }
        }
        return false;
    }

    public static boolean allowCommand(ServerPlayer sender, String command, ServerPlayer controller, boolean privateOnly) {
        if (controller == null || !privateOnly) {
            return true;
        }
        String[] parts = command.strip().split("\\s+", 3);
        String root = parts[0].toLowerCase(Locale.ROOT);
        if (root.startsWith("minecraft:")) {
            root = root.substring("minecraft:".length());
        }
        if (!SPEECH_COMMANDS.contains(root)) {
            return true;
        }
        if ((root.equals("msg") || root.equals("tell") || root.equals("w")) && parts.length == 3
                && parts[1].equalsIgnoreCase(controller.getGameProfile().name())) {
            return true;
        }
        BodyManager.message(sender, "observer_chat_private_only");
        return false;
    }
}
