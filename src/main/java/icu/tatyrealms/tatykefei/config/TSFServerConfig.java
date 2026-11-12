package icu.tatyrealms.tatykefei.config;

import icu.tatyrealms.tatykefei.TSF;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public final class TSFServerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("tsf-server.json");
    private static TSFServerConfig current = new TSFServerConfig();
    public boolean skinKnifeEnabled = true;
    public boolean skinKnifeRequiresHypnosis = false;
    public boolean skinKnifeRequiresDowned = false;
    public int expulsionDelaySeconds = 3;
    public boolean deathLeavesBody = true;
    public int maxSkinLayers = 16;
    public boolean bodyLimbPhysics = true;
    public boolean bodyPosePicking = true;

    public static TSFServerConfig parse(String json) throws IOException {
        try {
            TSFServerConfig loaded = GSON.fromJson(json, TSFServerConfig.class);
            if (loaded == null || loaded.expulsionDelaySeconds < 1 || loaded.expulsionDelaySeconds > 60) {
                throw new IOException("expulsionDelaySeconds must be between 1 and 60");
            }
            if (loaded.maxSkinLayers < 1 || loaded.maxSkinLayers > 64) {
                throw new IOException("maxSkinLayers must be between 1 and 64");
            }
            return loaded;
        } catch (RuntimeException exception) {
            throw new IOException("Invalid TSF server settings", exception);
        }
    }

    public String json() {
        return GSON.toJson(this);
    }

    public static void save(TSFServerConfig settings) throws IOException {
        TSFServerConfig validated = parse(settings.json());
        Files.createDirectories(PATH.getParent());
        Files.writeString(PATH, validated.json() + "\n", StandardCharsets.UTF_8);
        current = validated;
    }

    public static TSFServerConfig read() throws IOException {
        return Files.exists(PATH) ? parse(Files.readString(PATH, StandardCharsets.UTF_8)) : new TSFServerConfig();
    }

    public static TSFServerConfig get() {
        return current;
    }

    public static void reload() throws IOException {
        TSFServerConfig loaded = new TSFServerConfig();
        if (Files.exists(PATH)) {
            try {
                loaded = parse(Files.readString(PATH, StandardCharsets.UTF_8));
            } catch (RuntimeException exception) {
                throw new IOException("Cannot read TSF server config: " + PATH, exception);
            }
        } else {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(loaded) + "\n", StandardCharsets.UTF_8);
        }
        current = loaded;
    }
}
