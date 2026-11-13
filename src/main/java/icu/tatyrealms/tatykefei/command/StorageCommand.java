package icu.tatyrealms.tatykefei.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import icu.tatyrealms.tatykefei.body.BodyManager;
import java.util.concurrent.RejectedExecutionException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import org.slf4j.LoggerFactory;

public final class StorageCommand {
    private boolean exporting;

    private StorageCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> create() {
        StorageCommand command = new StorageCommand();
        return Commands.literal("storage")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("export")
                        .executes(context -> command.export(context.getSource(), false))
                        .then(Commands.literal("all").executes(context -> command.export(context.getSource(), true))));
    }

    private int export(CommandSourceStack source, boolean all) {
        var server = source.getServer();
        BodyManager manager = BodyManager.get();
        if (!manager.sharedStorage()) {
            source.sendFailure(Component.translatable("message.tsf.storage_export_database_required"));
            return 0;
        }
        if (exporting) {
            source.sendFailure(Component.translatable("message.tsf.storage_export_busy"));
            return 0;
        }
        exporting = true;
        try {
            var future = manager.exportStorage(all);
            source.sendSuccess(() -> Component.translatable(all
                    ? "message.tsf.storage_export_started_all" : "message.tsf.storage_export_started"), false);
            future.whenComplete((result, failure) -> {
                if (failure != null) LoggerFactory.getLogger("TSF").error("TSF 数据库导出失败", failure);
                else LoggerFactory.getLogger("TSF").info("TSF 数据库已导出至 {}", result.path().toAbsolutePath());
                try {
                    server.executeIfPossible(() -> {
                        exporting = false;
                        if (BodyManager.get() != manager || !server.isRunning()) return;
                        if (failure != null) {
                            source.sendFailure(Component.translatable("message.tsf.storage_export_failed"));
                        } else {
                            source.sendSuccess(() -> Component.translatable("message.tsf.storage_export_done",
                                    result.players(), result.bodies(), result.path().toAbsolutePath().toString()), true);
                        }
                    });
                } catch (RejectedExecutionException ignored) {
                    // 服务器已关闭，导出结果仍写入日志。
                }
            });
        } catch (RuntimeException exception) {
            exporting = false;
            LoggerFactory.getLogger("TSF").error("TSF 数据库导出启动失败", exception);
            source.sendFailure(Component.translatable("message.tsf.storage_export_failed"));
            return 0;
        }
        return 1;
    }
}
