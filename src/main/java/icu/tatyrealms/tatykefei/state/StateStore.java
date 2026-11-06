package icu.tatyrealms.tatykefei.state;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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

    public void save(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, GSON.toJson(this), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
