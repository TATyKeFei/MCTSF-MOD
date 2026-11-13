package icu.tatyrealms.tatykefei.persistence;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import icu.tatyrealms.tatykefei.config.TSFServerConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;

/** 不依赖游戏启动的持久化行为检查，真实数据库通过环境变量选择。 */
public final class StorageChecks {
    public static void main(String[] arguments) throws Exception {
        configChecks();
        localChecks();
        sharedChecks(new FakeBackend());
        // 故障注入会有意触发保存错误，断言仍验证失败和恢复结果。
        org.apache.logging.log4j.core.config.Configurator.setLevel("TSF", org.apache.logging.log4j.Level.OFF);
        try { failureChecks(); expiryChecks(); }
        finally { org.apache.logging.log4j.core.config.Configurator.setLevel("TSF", org.apache.logging.log4j.Level.INFO); }
        for (String type : new String[]{"redis", "valkey", "postgresql", "mariadb"}) {
            String port = System.getenv("TSF_TEST_" + type.toUpperCase() + "_PORT");
            if (port == null) continue;
            TSFServerConfig.Storage settings = settings(type, "integration");
            settings.namespace = "check_" + UUID.randomUUID().toString().replace("-", "");
            settings.redisPort = Integer.parseInt(port);
            settings.sqlPort = Integer.parseInt(port);
            settings.sqlUsername = System.getenv().getOrDefault("TSF_TEST_SQL_USER", "tsf");
            settings.sqlDatabase = System.getenv().getOrDefault("TSF_TEST_SQL_DATABASE", "tsf");
            settings.sqlPassword = System.getenv().getOrDefault("TSF_TEST_SQL_PASSWORD", "");
            settings.redisPassword = System.getenv().getOrDefault("TSF_TEST_REDIS_PASSWORD", "");
            sharedChecks(SharedStateBackend.create(settings));
            System.out.println(type + " 实例检查通过");
        }
        System.out.println("TSF 配置、内存/文件存储、跨服租约与故障重试检查通过");
    }

    private static void configChecks() throws Exception {
        String secret = "p# : ' \" / 中文";
        var config = TSFServerConfig.parseYaml("config-version: 1\nstorage:\n  type: mariadb\n  sql:\n    password: \"p# : ' \\\" / 中文\"\n");
        require(config.storage().sqlPort == 3306, "MariaDB 默认端口");
        require(config.storage().sqlPassword.equals(secret), "YAML 特殊字符密码");
        var reloaded = TSFServerConfig.parseYaml(TSFServerConfig.yaml(config));
        require(reloaded.storage().sqlPassword.equals(secret), "YAML 生成/解析往返");
        require(!config.json().contains("storage") && !config.json().contains(secret), "网络设置不得泄露连接凭据");
        require(TSFServerConfig.parse(config.json()).maxSkinLayers == 16, "网络玩法设置往返");
        expectIOException(() -> TSFServerConfig.parseYaml("config-version: 999\n"));
        expectIOException(() -> TSFServerConfig.parseYaml("gameplay:\n  max-skin-layers: -1\n"));
        expectIOException(() -> TSFServerConfig.parseYaml("storage:\n  type: unknown\n"));
        expectIOException(() -> TSFServerConfig.parseYaml("storage:\n  sql:\n    password: false\n"));
        expectIOException(() -> TSFServerConfig.parseYaml("storage:\n  type: memory\n  type: file\n"));
        expectIOException(() -> TSFServerConfig.parseYaml("storage:\n  sync-interval-seconds: 30\n  lease-seconds: 15\n"));
    }

    private static void localChecks() throws Exception {
        Path root = Files.createTempDirectory("tsf-storage-check");
        Path path = root.resolve("state.json");
        Files.writeString(path, "invalid legacy data");
        UUID id = UUID.randomUUID();
        try (var storage = new StatePersistence(settings("memory", "a"), path)) {
            require(storage.load().players.isEmpty(), "内存模式不得读取文件");
            storage.submit(Map.of("p:" + id, data("a", 7)), Map.of());
            storage.flush();
        }
        require(Files.readString(path).equals("invalid legacy data"), "内存模式不得修改文件");
        try (var storage = new StatePersistence(settings("memory", "a"), path)) {
            require(storage.load().players.isEmpty(), "内存模式重启清空");
        }
        Files.delete(path);
        try (var storage = new StatePersistence(settings("file", "a"), path)) {
            storage.submit(Map.of("p:" + id, data("a", 8)), Map.of());
            storage.flush();
        }
        require(JsonParser.parseString(Files.readString(path)).getAsJsonObject().getAsJsonObject("players").has(id.toString()), "文件存储格式兼容");
        try (var storage = new StatePersistence(settings("file", "a"), path)) {
            require(storage.load().players.containsKey(id), "文件存储重启读取");
        }
        Files.delete(path);
        Files.delete(root);
    }

    private static void sharedChecks(SharedStateBackend backend) throws Exception {
        Path unused = Path.of("unused-tsf-state.json");
        UUID player = UUID.randomUUID();
        UUID carried = UUID.randomUUID();
        UUID placed = UUID.randomUUID();
        try (var a = new StatePersistence(settings("redis", "a"), unused, backend);
             var b = new StatePersistence(settings("redis", "b"), unused, backend)) {
            expectIOException(() -> new StatePersistence(settings("redis", "a"), unused, backend));
            a.claim(player).join();
            try { a.claim(player).join(); throw new AssertionError("同服也必须拒绝重复会话"); }
            catch (CompletionException expected) { }
            var snapshot = new HashMap<String, JsonElement>();
            snapshot.put("p:" + player, data("a", 1));
            snapshot.put("b:" + carried, data("a", 2));
            snapshot.put("b:" + placed, data("a", 3));
            a.submit(snapshot, Map.of(carried, player));
            a.flush();
            try { b.claim(player).join(); throw new AssertionError("两服不得同时接管在线玩家"); }
            catch (CompletionException expected) { }
            long revision = revision(backend, "b:" + placed);
            long carriedRevision = revision(backend, "b:" + carried);
            a.flush();
            require(revision(backend, "b:" + placed) == revision, "无变化的记录不得重新写入");
            require(revision(backend, "b:" + carried) == carriedRevision, "无变化的携带关系不得重新写入");
            a.release(player);
            a.flush();
            b.claim(player).join();
            var transfer = b.poll();
            require(transfer.acquired().contains("b:" + carried), "携带的肉体需要原子接管");
            require(!transfer.acquired().contains("b:" + placed), "世界肉体不得随玩家迁移");
            require(transfer.records().get("b:" + carried).getAsJsonObject().get("storageServer").getAsString().equals("b"), "携带肉体所属服");
            a.submit(snapshot, Map.of(carried, player));
            a.flush();
            require(owner(backend, "b:" + carried).equals("b"), "旧服务器不得覆盖已接管肉体");
            require(owner(backend, "b:" + placed).equals("a"), "地面肉体保留来源服");
            b.submit(Map.of("p:" + player, data("b", 9), "b:" + carried, data("b", 10)), Map.of(carried, player));
            b.flush();
            require(revision(backend, "b:" + placed) == revision, "其他服保存不得覆写来源服记录");
            b.release(player); b.flush();
            a.claim(player).join();
            require(a.poll().acquired().contains("p:" + player), "返回原服务器必须替换旧缓存");
            try (var c = new StatePersistence(settings("redis", "c"), unused, backend)) {
                UUID competing = UUID.randomUUID();
                var first = b.claim(competing).handle((result, error) -> error == null);
                var second = c.claim(competing).handle((result, error) -> error == null);
                require((first.join() ? 1 : 0) + (second.join() ? 1 : 0) == 1, "并发接管必须仅有一个获胜者");
            }
        }
        require(!Files.exists(unused), "共享模式不得写本地状态文件");
    }

    private static void failureChecks() throws Exception {
        FakeBackend backend = new FakeBackend();
        UUID player = UUID.randomUUID();
        try (var storage = new StatePersistence(settings("redis", "a"), Path.of("unused"), backend)) {
            storage.claim(player).join();
            backend.failBefore = 2;
            storage.submit(Map.of("p:" + player, data("a", 5)), Map.of());
            expectIOException(storage::flush);
            storage.submit(Map.of("p:" + player, data("a", 55)), Map.of());
            storage.flush();
            require(value(backend, "p:" + player) == 55, "失败重试保留最新快照");
            backend.failAfter = 1;
            storage.submit(Map.of("p:" + player, data("a", 6)), Map.of());
            storage.flush();
            require(value(backend, "p:" + player) == 6, "提交成功但响应丢失允许安全重试");
            backend.failBefore = 2;
            storage.release(player);
            expectIOException(storage::flush);
            storage.flush();
            try (var next = new StatePersistence(settings("redis", "b"), Path.of("unused"), backend)) {
                next.claim(player).join();
                backend.failAfter = 1;
                next.release(player);
                next.flush();
                storage.claim(player).join();
            }
        }
    }

    private static void expiryChecks() throws Exception {
        FakeBackend backend = new FakeBackend();
        UUID player = UUID.randomUUID();
        var old = new StatePersistence(settings("redis", "a"), Path.of("unused"), backend);
        old.claim(player).join();
        old.submit(Map.of("p:" + player, data("a", 1)), Map.of());
        old.flush();
        backend.offset = 31000;
        try (var next = new StatePersistence(settings("redis", "b"), Path.of("unused"), backend);
             var sameId = new StatePersistence(settings("redis", "a"), Path.of("unused"), backend)) {
            next.claim(player).join();
            next.submit(Map.of("p:" + player, data("b", 2)), Map.of());
            next.flush();
            old.submit(Map.of("p:" + player, data("a", 3)), Map.of());
            expectIOException(old::flush);
            expectIOException(old::close);
            require(value(backend, "p:" + player) == 2, "租约过期后旧服不得覆盖新服");
            sameId.flush();
        }
    }

    private static long revision(SharedStateBackend backend, String key) throws IOException {
        return backend.transaction((records, now) -> JsonParser.parseString(records.get(key)).getAsJsonObject().get("revision").getAsLong());
    }
    private static String owner(SharedStateBackend backend, String key) throws IOException {
        return backend.transaction((records, now) -> JsonParser.parseString(records.get(key)).getAsJsonObject().get("home").getAsString());
    }
    private static int value(SharedStateBackend backend, String key) throws IOException {
        return backend.transaction((records, now) -> JsonParser.parseString(records.get(key)).getAsJsonObject().getAsJsonObject("data").get("testValue").getAsInt());
    }
    private static JsonObject data(String server, int value) {
        JsonObject data = new JsonObject(); data.addProperty("storageServer", server); data.addProperty("testValue", value);
        data.addProperty("testText", "中文皮物与肉体 🦊");
        return data;
    }
    private static TSFServerConfig.Storage settings(String type, String server) {
        var settings = new TSFServerConfig.Storage(); settings.type = type; settings.serverId = server; return settings;
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void expectIOException(Action action) throws Exception {
        try { action.run(); throw new AssertionError("Expected IOException"); } catch (IOException expected) { }
    }
    private interface Action { void run() throws Exception; }

    private static final class FakeBackend implements SharedStateBackend {
        private Map<String, String> records = new HashMap<>();
        volatile int failBefore;
        volatile int failAfter;
        volatile long offset;
        public synchronized <T> T transaction(Transaction<T> transaction) throws IOException {
            if (failBefore > 0) { failBefore--; throw new IOException("模拟连接失败"); }
            Map<String, String> next = new HashMap<>(records);
            T result = transaction.run(next, System.currentTimeMillis() + offset);
            records = next;
            if (failAfter > 0) { failAfter--; throw new IOException("模拟提交响应丢失"); }
            return result;
        }
    }
}
