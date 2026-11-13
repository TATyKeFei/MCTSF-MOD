package icu.tatyrealms.tatykefei.persistence;

import icu.tatyrealms.tatykefei.body.BodyManager;
import java.util.concurrent.CompletableFuture;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

/** 进入世界之前等待后台接管，不让数据库访问阻塞游戏线程。 */
public final class StorageLoginTask implements ConfigurationTask {
    private static final Type TYPE = new Type("tsf:storage");
    private static final Map<ServerConfigurationPacketListenerImpl, StorageLoginTask> TASKS = java.util.Collections.synchronizedMap(new IdentityHashMap<>());
    private final ServerConfigurationPacketListenerImpl handler;
    private final BodyManager manager;
    private volatile CompletableFuture<Void> future;
    private boolean done;

    public StorageLoginTask(ServerConfigurationPacketListenerImpl handler) {
        this.handler = handler;
        manager = BodyManager.get();
        TASKS.put(handler, this);
    }

    public static void entered(UUID player) {
        synchronized (TASKS) {
            TASKS.entrySet().removeIf(entry -> entry.getKey().getOwner().id().equals(player));
        }
    }

    public static void disconnected(ServerConfigurationPacketListenerImpl handler) {
        StorageLoginTask task = TASKS.remove(handler);
        if (task != null && task.future != null) task.future.thenRun(() -> {
            task.manager.cancelLogin(handler.getOwner().id());
        });
    }

    public void start(Consumer<Packet<?>> sender) {
        future = manager.prepareLogin(handler.getOwner().id());
    }

    public boolean tick() {
        if (done || future == null || !future.isDone()) return false;
        done = true;
        try {
            future.join();
            return true;
        } catch (RuntimeException exception) {
            org.slf4j.LoggerFactory.getLogger("TSF").warn("无法接管玩家 {} 的 TSF 数据", handler.getOwner().id(), exception);
            handler.disconnect(Component.translatable("message.tsf.storage_login_failed"));
            return false;
        }
    }

    public Type type() { return TYPE; }
}
