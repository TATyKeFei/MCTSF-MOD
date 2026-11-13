package icu.tatyrealms.tatykefei.persistence;

import icu.tatyrealms.tatykefei.body.Body;
import icu.tatyrealms.tatykefei.state.PlayerState;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class StateStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public Map<UUID, PlayerState> players = new HashMap<>();
    public Map<UUID, Body> bodies = new HashMap<>();

    public static StateStore load(Path path) throws IOException {
        if (!Files.exists(path)) {
            return new StateStore();
        }
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            StateStore state = GSON.fromJson(reader, StateStore.class);
            if (state == null || state.players == null || state.bodies == null) {
                throw new IOException("Invalid TSF state: " + path);
            }
            state.bodies.forEach((id, body) -> body.id = id);
            return state;
        } catch (RuntimeException exception) {
            throw new IOException("Cannot read TSF state: " + path, exception);
        }
    }

    public Map<String, JsonElement> records(String server) {
        Map<String, JsonElement> records = new HashMap<>();
        players.forEach((id, state) -> {
            if (server == null || server.equals(state.storageServer)) records.put("p:" + id, GSON.toJsonTree(state));
        });
        bodies.forEach((id, body) -> {
            if (server == null || server.equals(body.storageServer)) records.put("b:" + id, GSON.toJsonTree(body));
        });
        return records;
    }

    public static JsonElement json(Map<String, JsonElement> records) {
        var root = new com.google.gson.JsonObject();
        var players = new com.google.gson.JsonObject();
        var bodies = new com.google.gson.JsonObject();
        records.forEach((key, value) -> {
            if (key.startsWith("p:")) players.add(key.substring(2), value);
            else if (key.startsWith("b:")) bodies.add(key.substring(2), value);
        });
        root.add("players", players);
        root.add("bodies", bodies);
        return root;
    }

    public static StateStore fromRecords(Map<String, JsonElement> records) {
        StateStore state = GSON.fromJson(json(records), StateStore.class);
        state.bodies.forEach((id, body) -> body.id = id);
        return state;
    }

    static void write(Path path, JsonElement snapshot) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, GSON.toJson(snapshot), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
