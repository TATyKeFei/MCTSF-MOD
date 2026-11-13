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
        icu.tatyrealms.tatykefei.config.ConfigLocationChecks.run();
        configChecks();
        localChecks();
        sharedChecks(new FakeBackend());
        exportChecks(new FakeBackend());
        // 故障注入会有意触发保存错误，断言仍验证失败和恢复结果。
        org.apache.logging.log4j.core.config.Configurator.setLevel("TSF", org.apache.logging.log4j.Level.OFF);
        try { failureChecks(); expiryChecks(); exportFailureChecks(); }
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
            settings.namespace = "export_" + UUID.randomUUID().toString().replace("-", "");
            exportChecks(SharedStateBackend.create(settings));
            System.out.println(type + " 实例检查通过");
        }
        System.out.println("TSF 配置、内存/文件存储、跨服租约、故障重试与数据库导出检查通过");
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

    private static void exportChecks(SharedStateBackend backend) throws Exception {
        Path root = Files.createTempDirectory("tsf-export-check-");
        Path original = root.resolve("world/tsf/state.json");
        Files.createDirectories(original.getParent());
        Files.writeString(original, "existing file state must remain unchanged");
        Path exports = root.resolve("config/tsf/exports");
        long now = System.currentTimeMillis();
        long gameTime = 1200;
        UUID localPlayer = UUID.randomUUID();
        UUID remotePlayer = UUID.randomUUID();
        UUID localBody = UUID.randomUUID();
        UUID remoteBody = UUID.randomUUID();
        try (var a = new StatePersistence(settings("redis", "export-a"), original, backend);
             var b = new StatePersistence(settings("redis", "export-b"), original, backend)) {
            a.claim(localPlayer).join();
            b.claim(remotePlayer).join();
            JsonObject player = data("export-a", 1);
            player.addProperty("body", localBody.toString());
            player.addProperty("homeBody", localBody.toString());
            player.addProperty("expulsionAt", now + 5000);
            player.addProperty("hypnosisUntil", now - 1000);
            JsonObject body = data("export-a", 2);
            body.addProperty("skinRecoveryAt", now + 1500);
            body.add("skinOrder", JsonParser.parseString("[\"" + localBody + "\"]"));
            body.add("inventory", JsonParser.parseString("[{\"id\":\"minecraft:stone\",\"count\":3}]"));
            JsonObject foreign = data("export-b", 3);
            foreign.addProperty("skinRecoveryAt", 0);
            a.submit(Map.of("p:" + localPlayer, player, "b:" + localBody, body), Map.of(localBody, localPlayer));
            a.flush();
            b.submit(Map.of("p:" + remotePlayer, data("export-b", 4), "b:" + remoteBody, foreign), Map.of());
            b.flush();
            long foreignRevision = revision(backend, "b:" + remoteBody);

            // 导出必须包含已提交但尚未显式 flush 的最新本服状态。
            player.addProperty("testValue", 99);
            a.submit(Map.of("p:" + localPlayer, player, "b:" + localBody, body), Map.of(localBody, localPlayer));
            var local = a.export(exports, false, gameTime, now).join();
            require(local.path().getParent().getParent().equals(exports), "导出到 config/tsf/exports/时间戳目录");
            require(local.path().getFileName().toString().equals("state.json"), "导出存档文件名");
            require(local.players() == 1 && local.bodies() == 1, "默认仅导出当前服务器的数据");
            var loaded = StateStore.load(local.path());
            require(loaded.players.size() == 1 && loaded.players.containsKey(localPlayer)
                    && loaded.bodies.size() == 1 && loaded.bodies.containsKey(localBody), "导出存档可直接由 file 读取");
            require(loaded.players.get(localPlayer).body.equals(localBody)
                    && loaded.bodies.get(localBody).id.equals(localBody), "保留玩家肉体引用及肉体 ID");
            require(loaded.players.get(localPlayer).expulsionAt == gameTime + 100, "驱逐时间由毫秒期限转为游戏刻");
            require(loaded.players.get(localPlayer).hypnosisUntil == gameTime, "过期计时立即到期");
            require(loaded.bodies.get(localBody).skinRecoveryAt == gameTime + 30, "皮物恢复时间转换");
            require(loaded.bodies.get(localBody).skinOrder.equals(java.util.List.of(localBody))
                    && loaded.bodies.get(localBody).inventory.equals(body.getAsJsonArray("inventory").asList()), "保留皮物层序及物品数据");
            require(loaded.players.get(localPlayer).storageServer == null
                    && loaded.bodies.get(localBody).storageCarrier == null, "移除数据库所属服和携带元数据");
            JsonObject json = JsonParser.parseString(Files.readString(local.path())).getAsJsonObject();
            JsonObject exportedPlayer = json.getAsJsonObject("players").getAsJsonObject(localPlayer.toString());
            require(exportedPlayer.get("testValue").getAsInt() == 99, "导出前保存最新状态");
            require(!exportedPlayer.has("session") && !exportedPlayer.has("revision") && !exportedPlayer.has("writer"),
                    "导出不携带数据库租约和版本封装");
            require(player.get("expulsionAt").getAsLong() == now + 5000
                    && body.get("skinRecoveryAt").getAsLong() == now + 1500, "导出不得修改提交快照");

            String firstExport = Files.readString(local.path());
            var all = a.export(exports, true, gameTime, now).join();
            require(all.players() == 2 && all.bodies() == 2, "all 导出整个 namespace 的跨服数据");
            var allLoaded = StateStore.load(all.path());
            require(allLoaded.bodies.get(remoteBody).skinRecoveryAt == 0, "零计时哨兵保持为零");
            require(allLoaded.players.get(remotePlayer).expulsionAt == 0, "缺省计时保持关闭");
            require(!local.path().equals(all.path()) && Files.readString(local.path()).equals(firstExport), "连续导出不得覆盖已有备份");
            require(revision(backend, "b:" + remoteBody) == foreignRevision
                    && owner(backend, "b:" + remoteBody).equals("export-b"), "导出不得改变其他服务器的数据或所属关系");
            require(backend.transaction((values, time) -> JsonParser.parseString(values.get("p:" + localPlayer))
                    .getAsJsonObject().getAsJsonObject("data").get("expulsionAt").getAsLong()) == now + 5000,
                    "数据库保留原始毫秒期限");
            try (var restored = new StatePersistence(settings("file", "restored"), local.path())) {
                require(restored.load().players.containsKey(localPlayer), "导出存档能以 file 后端启动");
            }
            var zeroTime = StateStore.load(a.export(exports, false, 0, now + 10000).join().path());
            require(zeroTime.players.get(localPlayer).expulsionAt == 1
                    && zeroTime.players.get(localPlayer).hypnosisUntil == 1
                    && zeroTime.bodies.get(localBody).skinRecoveryAt == 1, "游戏刻为零时已到期计时不得变成关闭哨兵");
            Path blocked = root.resolve("blocked");
            Files.writeString(blocked, "not a directory");
            try {
                a.export(blocked, true, gameTime, now).join();
                throw new AssertionError("文件写入失败必须报告失败");
            } catch (CompletionException expected) {
                require(expected.getCause() instanceof IOException, "导出写入失败返回 IO 异常");
            }
            require(a.healthy(), "导出文件写入失败不影响数据库租约");
            require(a.export(exports, false, gameTime, now).join().players() == 1, "文件写入失败后允许重新导出");
            require(Files.readString(original).equals("existing file state must remain unchanged"), "导出不得覆盖世界存档");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static void exportFailureChecks() throws Exception {
        FakeBackend backend = new FakeBackend();
        Path root = Files.createTempDirectory("tsf-export-failure-");
        Path exports = root.resolve("exports");
        UUID player = UUID.randomUUID();
        try (var storage = new StatePersistence(settings("redis", "export-failure"), root.resolve("unused"), backend)) {
            storage.claim(player).join();
            storage.submit(Map.of("p:" + player, data("export-failure", 5)), Map.of());
            storage.flush();
            backend.failBefore = 1;
            try {
                storage.export(exports, false, 1200, System.currentTimeMillis()).join();
                throw new AssertionError("数据库读取失败时导出必须失败");
            } catch (CompletionException expected) {
                require(!Files.exists(exports), "数据库失败时不得创建成功存档");
            }
            require(storage.export(exports, false, 1200, System.currentTimeMillis()).join().players() == 1,
                    "数据库恢复后可重新导出");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
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
