package icu.tatyrealms.tatykefei.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import icu.tatyrealms.tatykefei.body.BodyManager;
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
import org.slf4j.LoggerFactory;

public final class GiveBodyCommand {
    private GiveBodyCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> create() {
        return Commands.literal("givebody")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                // Vanilla's single-entity parser accepts UUIDs as well as names and selectors.
                .then(Commands.argument("player", EntityArgument.entity())
                        .executes(context -> execute(context, context.getSource().getPlayerOrException()))
                        .then(Commands.argument("recipient", EntityArgument.player()).executes(context ->
                                execute(context, EntityArgument.getPlayer(context, "recipient")))));
    }

    private static int execute(CommandContext<CommandSourceStack> context, ServerPlayer recipient) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        var server = source.getServer();
        BodyManager manager = BodyManager.get();
        EntitySelector selector = context.getArgument("player", EntitySelector.class);
        if (selector.usesSelector()) {
            if (selector.includesEntities() && !selector.isSelfSelector()) {
                throw EntityArgument.ERROR_ONLY_PLAYERS_ALLOWED.create();
            }
            ServerPlayer player = selector.findSinglePlayer(source);
            manager.giveBody(player, recipient);
            success(source, player.getGameProfile().name(), recipient);
            return 1;
        }
        // Keep the typed name/UUID even when vanilla cannot find an online player for it.
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
            manager.giveBody(online, recipient);
            success(source, online.getGameProfile().name(), recipient);
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
                                LoggerFactory.getLogger("TSF").warn("Unable to resolve body profile {}", query, cause);
                            }
                        } else if (profile.isEmpty()) {
                            source.sendFailure(Component.translatable("message.tsf.body_profile_not_found", query));
                        } else {
                            manager.giveBody(profile.get(), recipient);
                            success(source, profile.get().name(), recipient);
                        }
                    });
                } catch (RejectedExecutionException ignored) {
                    // The world closed while its profile lookup was still running.
                }
            });
        } catch (RejectedExecutionException exception) {
            source.sendFailure(Component.translatable("message.tsf.body_profile_busy"));
            return 0;
        }
        return 1;
    }

    private static void success(CommandSourceStack source, String name, ServerPlayer recipient) {
        source.sendSuccess(() -> Component.translatable("message.tsf.body_given", name, recipient.getGameProfile().name()), true);
    }
}
