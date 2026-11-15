package icu.tatyrealms.tatykefei.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import icu.tatyrealms.tatykefei.body.BodyManager;
import icu.tatyrealms.tatykefei.item.LatexPotions;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.Prediction;
import org.slf4j.LoggerFactory;

public final class GiveCommand {
    private GiveCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> create() {
        return Commands.literal("give")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                // 保留玩家名、UUID 和在线玩家选择器，以及可选的收件人。
                .then(Commands.literal("body").then(Commands.argument("player", EntityArgument.entity())
                        .executes(context -> execute(context, context.getSource().getPlayerOrException(), 0))
                        .then(Commands.argument("recipient", EntityArgument.player()).executes(context ->
                                execute(context, EntityArgument.getPlayer(context, "recipient"), 0)))))
                .then(Commands.literal("latex_potion").then(Commands.argument("player", EntityArgument.entity())
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, LatexPotions.MAX_LEVEL))
                                .executes(context -> execute(context, context.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(context, "level")))
                                .then(Commands.argument("recipient", EntityArgument.player()).executes(context ->
                                        execute(context, EntityArgument.getPlayer(context, "recipient"),
                                                IntegerArgumentType.getInteger(context, "level")))))));
    }

    private static int execute(CommandContext<CommandSourceStack> context, ServerPlayer recipient, int latexLevel) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        var server = source.getServer();
        BodyManager manager = BodyManager.get();
        EntitySelector selector = context.getArgument("player", EntitySelector.class);
        if (selector.usesSelector()) {
            if (selector.includesEntities() && !selector.isSelfSelector()) {
                throw EntityArgument.ERROR_ONLY_PLAYERS_ALLOWED.create();
            }
            ServerPlayer player = selector.findSinglePlayer(source);
            give(manager, player.getGameProfile(), player, recipient, latexLevel);
            success(source, player.getGameProfile().name(), recipient, latexLevel);
            return 1;
        }
        // 在线玩家不存在时仍保留输入的名字或 UUID，交给档案查询。
        String query = new StringReader(context.getNodes().stream().filter(node -> node.getNode().getName().equals("player"))
                .findFirst().orElseThrow().getRange().get(context.getInput())).readString();
        UUID id = null;
        if (query.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            id = UUID.fromString(query);
        } else if (!query.matches("[A-Za-z0-9_]{1,16}")) {
            throw new SimpleCommandExceptionType(Component.translatable("message.tsf.body_profile_invalid")).create();
        }
        ServerPlayer online = id == null ? server.getPlayerList().getPlayerByName(query) : server.getPlayerList().getPlayer(id);
        if (online != null) {
            give(manager, online.getGameProfile(), online, recipient, latexLevel);
            success(source, online.getGameProfile().name(), recipient, latexLevel);
            return 1;
        }
        try {
            var lookup = manager.resolveBodyProfile(query, id);
            source.sendSuccess(() -> Component.translatable("message.tsf.body_profile_loading", query), false);
            lookup.whenComplete((profile, failure) -> {
                try {
                    server.executeIfPossible(() -> {
                        if (BodyManager.get() != manager || !server.isRunning()) {
                            return;
                        }
                        if (server.getPlayerList().getPlayer(recipient.getUUID()) != recipient) {
                            source.sendFailure(Component.translatable("message.tsf.body_recipient_offline"));
                            return;
                        }
                        if (failure != null) {
                            Throwable cause = failure instanceof CompletionException ? failure.getCause() : failure;
                            source.sendFailure(Component.translatable(cause instanceof TimeoutException
                                    ? "message.tsf.body_profile_timeout" : "message.tsf.body_profile_failed", query));
                            if (!(cause instanceof TimeoutException)) {
                                LoggerFactory.getLogger("TSF").warn("Unable to resolve player profile {}", query, cause);
                            }
                        } else if (profile.isEmpty()) {
                            source.sendFailure(Component.translatable("message.tsf.body_profile_not_found", query));
                        } else {
                            give(manager, profile.get(), null, recipient, latexLevel);
                            success(source, profile.get().name(), recipient, latexLevel);
                        }
                    });
                } catch (RejectedExecutionException ignored) {
                    // 档案查询结束前世界已关闭。
                }
            });
        } catch (RejectedExecutionException exception) {
            source.sendFailure(Component.translatable("message.tsf.body_profile_busy"));
            return 0;
        }
        return 1;
    }

    private static void give(BodyManager manager, GameProfile profile, ServerPlayer online, ServerPlayer recipient, int latexLevel) {
        if (latexLevel > 0) {
            recipient.getInventory().placeItemBackInInventory(LatexPotions.create(profile, latexLevel), Prediction.SERVER_ONLY);
        } else if (online != null) {
            manager.giveBody(online, recipient);
        } else {
            manager.giveBody(profile, recipient);
        }
    }

    private static void success(CommandSourceStack source, String name, ServerPlayer recipient, int latexLevel) {
        source.sendSuccess(() -> latexLevel > 0
                ? Component.translatable("message.tsf.latex_potion_given", name, latexLevel, recipient.getGameProfile().name())
                : Component.translatable("message.tsf.body_given", name, recipient.getGameProfile().name()), true);
    }
}
