package icu.tatyrealms.tatykefei.persistence;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/** 将独立的数据库快照转换为可供 file 存储读取的存档。 */
public final class StateExporter {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS");

    private StateExporter() {
    }

    static Result write(Path exports, Map<String, JsonElement> snapshot, String serverId,
            long gameTime, long now) throws IOException {
        Map<String, JsonElement> records = new HashMap<>();
        int players = 0;
        int bodies = 0;
        for (var entry : snapshot.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("p:") && !key.startsWith("b:")) continue;
            JsonObject data = entry.getValue().deepCopy().getAsJsonObject();
            if (serverId != null && (!data.has("storageServer")
                    || !serverId.equals(data.get("storageServer").getAsString()))) continue;
            data.remove("storageServer");
            data.remove("storageCarrier");
            if (key.startsWith("p:")) {
                timer(data, "expulsionAt", gameTime, now);
                timer(data, "hypnosisUntil", gameTime, now);
                players++;
            } else {
                timer(data, "skinRecoveryAt", gameTime, now);
                bodies++;
            }
            records.put(key, data);
        }

        Files.createDirectories(exports);
        String timestamp = TIMESTAMP.format(LocalDateTime.now());
        Path directory = exports.resolve(timestamp);
        for (int suffix = 1; ; suffix++) {
            try {
                Files.createDirectory(directory);
                break;
            } catch (FileAlreadyExistsException exception) {
                // 同一毫秒内重复导出时分配新目录，禁止覆盖已有备份。
                directory = exports.resolve(timestamp + "-" + suffix);
            }
        }
        Path path = directory.resolve("state.json");
        try {
            StateStore.write(path, StateStore.json(records));
            return new Result(path, players, bodies);
        } catch (IOException | RuntimeException exception) {
            try {
                Files.deleteIfExists(path.resolveSibling("state.json.tmp"));
                Files.deleteIfExists(path);
                Files.deleteIfExists(directory);
            } catch (IOException cleanup) {
                exception.addSuppressed(cleanup);
            }
            throw exception;
        }
    }

    private static void timer(JsonObject data, String key, long gameTime, long now) {
        if (data.has(key) && data.get(key).getAsLong() != 0) {
            data.addProperty(key, Math.max(1, gameTime + Math.max(0, data.get(key).getAsLong() - now) / 50));
        }
    }

    public record Result(Path path, int players, int bodies) { }
}
