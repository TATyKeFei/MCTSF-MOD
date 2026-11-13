package icu.tatyrealms.tatykefei.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** 网络只交换玩法设置，连接凭据只读写服务器本地 YAML。 */
public final class TSFServerConfig {
    public static final int CURRENT_CONFIG_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static TSFServerConfig current = new TSFServerConfig();

    public boolean skinKnifeEnabled = true;
    public boolean skinKnifeRequiresHypnosis;
    public boolean skinKnifeRequiresDowned;
    public int expulsionDelaySeconds = 3;
    public boolean deathLeavesBody = true;
    public int maxSkinLayers = 16;
    public boolean bodyLimbPhysics = true;
    public boolean bodyPosePicking = true;
    private Storage storage = new Storage();

    public static TSFServerConfig parse(String json) throws IOException {
        try {
            TSFServerConfig loaded = GSON.fromJson(json, TSFServerConfig.class);
            if (loaded == null) {
                throw new IOException("Invalid TSF gameplay settings");
            }
            validateGameplay(loaded);
            loaded.storage = new Storage();
            return loaded;
        } catch (RuntimeException exception) {
            throw new IOException("Invalid TSF gameplay settings", exception);
        }
    }

    private static void validateGameplay(TSFServerConfig loaded) throws IOException {
        if (loaded.expulsionDelaySeconds < 1 || loaded.expulsionDelaySeconds > 60
                || loaded.maxSkinLayers < 1 || loaded.maxSkinLayers > 64) {
            throw new IOException("Invalid TSF gameplay settings");
        }
    }

    public String json() {
        var json = GSON.toJsonTree(this).getAsJsonObject();
        json.remove("storage");
        return GSON.toJson(json);
    }

    public Storage storage() {
        return storage.copy();
    }

    /** 游戏内保存只更新玩法，保留磁盘上的连接配置。 */
    public static void save(TSFServerConfig settings) throws IOException {
        validateGameplay(settings);
        settings.storage = read().storage;
        writeYaml(path(), settings);
        current = settings;
    }

    public static TSFServerConfig read() throws IOException {
        return Files.exists(path()) ? parseYaml(Files.readString(path(), StandardCharsets.UTF_8))
                : parseYaml(defaultYaml());
    }

    public static TSFServerConfig get() {
        return current;
    }

    public static void reload() throws IOException {
        current = load(directory());
    }

    static TSFServerConfig load(Path directory) throws IOException {
        Path configPath = directory.resolve("tsf-server.yml");
        if (Files.exists(configPath)) {
            return parseYaml(Files.readString(configPath, StandardCharsets.UTF_8));
        }
        // 仅移动已有的 YAML 配置，保留凭据和注释，不导入旧 JSON。
        Path previousPath = directory.resolveSibling("tsf-server.yml");
        if (Files.exists(previousPath)) {
            TSFServerConfig loaded = parseYaml(Files.readString(previousPath, StandardCharsets.UTF_8));
            Files.createDirectories(directory);
            Files.move(previousPath, configPath);
            return loaded;
        }
        String defaults = defaultYaml();
        TSFServerConfig loaded = parseYaml(defaults);
        writeYaml(configPath, defaults);
        return loaded;
    }

    private static String defaultYaml() throws IOException {
        try (var stream = TSFServerConfig.class.getResourceAsStream("/tsf-server.yml")) {
            if (stream == null) {
                throw new IOException("Missing bundled TSF default configuration: tsf-server.yml");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Path path() {
        return directory().resolve("tsf-server.yml");
    }

    public static Path directory() {
        return FabricLoader.getInstance().getConfigDir().resolve("tsf");
    }

    public static TSFServerConfig parseYaml(String text) throws IOException {
        try {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            options.setMaxAliasesForCollections(10);
            options.setCodePointLimit(65536);
            Map<String, Object> root = mapping(new Yaml(new SafeConstructor(options)).load(text), "root");
            int version = number(root, "config-version", CURRENT_CONFIG_VERSION);
            if (version < 1 || version > CURRENT_CONFIG_VERSION) {
                throw new IOException("Unsupported TSF config-version: " + version);
            }
            keys(root, Set.of("config-version", "gameplay", "storage"), "root");
            TSFServerConfig config = new TSFServerConfig();
            Map<String, Object> gameplay = section(root, "gameplay");
            keys(gameplay, Set.of("skin-knife-enabled", "skin-knife-requires-hypnosis",
                    "skin-knife-requires-downed", "expulsion-delay-seconds", "death-leaves-body",
                    "max-skin-layers", "body-limb-physics", "body-pose-picking"), "gameplay");
            config.skinKnifeEnabled = bool(gameplay, "skin-knife-enabled", true);
            config.skinKnifeRequiresHypnosis = bool(gameplay, "skin-knife-requires-hypnosis", false);
            config.skinKnifeRequiresDowned = bool(gameplay, "skin-knife-requires-downed", false);
            config.expulsionDelaySeconds = number(gameplay, "expulsion-delay-seconds", 3);
            config.deathLeavesBody = bool(gameplay, "death-leaves-body", true);
            config.maxSkinLayers = number(gameplay, "max-skin-layers", 16);
            config.bodyLimbPhysics = bool(gameplay, "body-limb-physics", true);
            config.bodyPosePicking = bool(gameplay, "body-pose-picking", true);
            Map<String, Object> values = section(root, "storage");
            keys(values, Set.of("type", "namespace", "server-id", "flush-interval-seconds",
                    "sync-interval-seconds", "lease-seconds", "sql", "redis"), "storage");
            Storage storage = config.storage;
            storage.type = string(values, "type", "file");
            storage.namespace = string(values, "namespace", "tsf");
            storage.serverId = string(values, "server-id", "server-1");
            storage.flushIntervalSeconds = number(values, "flush-interval-seconds", 5);
            storage.syncIntervalSeconds = number(values, "sync-interval-seconds", 2);
            storage.leaseSeconds = number(values, "lease-seconds", 30);
            Map<String, Object> sql = section(values, "sql");
            keys(sql, Set.of("host", "port", "database", "username", "password", "tls"), "storage.sql");
            storage.sqlHost = string(sql, "host", "localhost");
            storage.sqlPort = number(sql, "port", storage.type.equals("mariadb") ? 3306 : 5432);
            storage.sqlDatabase = string(sql, "database", "tsf");
            storage.sqlUsername = string(sql, "username", "tsf");
            storage.sqlPassword = string(sql, "password", "");
            storage.sqlTls = bool(sql, "tls", false);
            Map<String, Object> redis = section(values, "redis");
            keys(redis, Set.of("host", "port", "database", "username", "password", "tls"), "storage.redis");
            storage.redisHost = string(redis, "host", "localhost");
            storage.redisPort = number(redis, "port", 6379);
            storage.redisDatabase = number(redis, "database", 0);
            storage.redisUsername = string(redis, "username", "");
            storage.redisPassword = string(redis, "password", "");
            storage.redisTls = bool(redis, "tls", false);
            validateGameplay(config);
            storage.validate();
            return config;
        } catch (RuntimeException exception) {
            throw new IOException("Invalid TSF YAML configuration", exception);
        }
    }

    private static Map<String, Object> section(Map<String, Object> parent, String key) throws IOException {
        return parent.containsKey(key) ? mapping(parent.get(key), key) : Map.of();
    }

    private static Map<String, Object> mapping(Object value, String key) throws IOException {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IOException(key + " must be a YAML mapping");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (var entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String name)) {
                throw new IOException("Non-string YAML key in " + key);
            }
            result.put(name, entry.getValue());
        }
        return result;
    }

    private static void keys(Map<String, Object> values, Set<String> allowed, String section) throws IOException {
        for (String key : values.keySet()) {
            if (!allowed.contains(key)) {
                throw new IOException("Unknown TSF config key: " + section + "." + key);
            }
        }
    }

    private static String string(Map<String, Object> values, String key, String fallback) throws IOException {
        Object value = values.getOrDefault(key, fallback);
        if (!(value instanceof String text)) {
            throw new IOException(key + " must be a string");
        }
        return text;
    }

    private static int number(Map<String, Object> values, String key, int fallback) throws IOException {
        Object value = values.getOrDefault(key, fallback);
        if (!(value instanceof Number number) || number.doubleValue() != number.intValue()) {
            throw new IOException(key + " must be an integer");
        }
        return number.intValue();
    }

    private static boolean bool(Map<String, Object> values, String key, boolean fallback) throws IOException {
        Object value = values.getOrDefault(key, fallback);
        if (!(value instanceof Boolean enabled)) {
            throw new IOException(key + " must be a boolean");
        }
        return enabled;
    }

    public static String yaml(TSFServerConfig config) {
        Map<String, Object> gameplay = new LinkedHashMap<>();
        gameplay.put("skin-knife-enabled", config.skinKnifeEnabled);
        gameplay.put("skin-knife-requires-hypnosis", config.skinKnifeRequiresHypnosis);
        gameplay.put("skin-knife-requires-downed", config.skinKnifeRequiresDowned);
        gameplay.put("expulsion-delay-seconds", config.expulsionDelaySeconds);
        gameplay.put("death-leaves-body", config.deathLeavesBody);
        gameplay.put("max-skin-layers", config.maxSkinLayers);
        gameplay.put("body-limb-physics", config.bodyLimbPhysics);
        gameplay.put("body-pose-picking", config.bodyPosePicking);
        Storage s = config.storage;
        Map<String, Object> storage = new LinkedHashMap<>();
        storage.put("type", s.type);
        storage.put("namespace", s.namespace);
        storage.put("server-id", s.serverId);
        storage.put("flush-interval-seconds", s.flushIntervalSeconds);
        storage.put("sync-interval-seconds", s.syncIntervalSeconds);
        storage.put("lease-seconds", s.leaseSeconds);
        storage.put("sql", connection(s.sqlHost, s.sqlPort, s.sqlDatabase, s.sqlUsername, s.sqlPassword, s.sqlTls));
        storage.put("redis", connection(s.redisHost, s.redisPort, s.redisDatabase, s.redisUsername, s.redisPassword, s.redisTls));
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("config-version", CURRENT_CONFIG_VERSION);
        root.put("gameplay", gameplay);
        root.put("storage", storage);
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setPrettyFlow(true);
        return "# TSF 配置格式版本独立于模组版本，旧 JSON 不会自动导入\n"
                + "# 配置路径：config/tsf/tsf-server.yml\n"
                + "# storage.type: file、memory、postgresql、mariadb、redis、valkey\n"
                + "# memory 不持久化；数据库和同步参数修改后需要重启\n"
                + "# 多服使用相同 namespace，每服 server-id 必须唯一且保持不变\n"
                + "# SQL 数据库需预先创建，表由模组创建；Redis/Valkey 持久化由其服务端配置\n"
                + "# 同步范围为 TSF 数据，原版背包和世界文件需要独立的同步方案\n"
                + new Yaml(options).dump(root);
    }

    private static Map<String, Object> connection(String host, int port, Object database,
            String username, String password, boolean tls) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("host", host);
        values.put("port", port);
        values.put("database", database);
        values.put("username", username);
        values.put("password", password);
        values.put("tls", tls);
        return values;
    }

    private static void writeYaml(Path path, TSFServerConfig config) throws IOException {
        writeYaml(path, yaml(config));
    }

    private static void writeYaml(Path path, String yaml) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, yaml, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static final class Storage {
        public String type = "file";
        public String namespace = "tsf";
        public String serverId = "server-1";
        public int flushIntervalSeconds = 5;
        public int syncIntervalSeconds = 2;
        public int leaseSeconds = 30;
        public String sqlHost = "localhost";
        public int sqlPort = 5432;
        public String sqlDatabase = "tsf";
        public String sqlUsername = "tsf";
        public String sqlPassword = "";
        public boolean sqlTls;
        public String redisHost = "localhost";
        public int redisPort = 6379;
        public int redisDatabase;
        public String redisUsername = "";
        public String redisPassword = "";
        public boolean redisTls;

        public Storage copy() {
            return GSON.fromJson(GSON.toJson(this), Storage.class);
        }

        public boolean shared() {
            return !type.equals("file") && !type.equals("memory");
        }

        public void validate() throws IOException {
            if (!Set.of("file", "memory", "postgresql", "mariadb", "redis", "valkey").contains(type)) {
                throw new IOException("Unsupported TSF storage type: " + type);
            }
            if (!namespace.matches("[a-zA-Z0-9_-]{1,48}") || !serverId.matches("[a-zA-Z0-9_-]{1,48}")) {
                throw new IOException("namespace and server-id must contain 1-48 letters, digits, underscores or hyphens");
            }
            if (flushIntervalSeconds < 1 || flushIntervalSeconds > 300 || syncIntervalSeconds < 1
                    || syncIntervalSeconds > 30 || leaseSeconds < 15 || leaseSeconds > 300
                    || leaseSeconds < syncIntervalSeconds * 3) {
                throw new IOException("Invalid storage flush/sync interval or lease duration");
            }
            if (sqlPort < 1 || sqlPort > 65535 || redisPort < 1 || redisPort > 65535 || redisDatabase < 0
                    || sqlHost.isBlank() || redisHost.isBlank() || !sqlDatabase.matches("[a-zA-Z0-9_-]{1,64}")) {
                throw new IOException("Invalid storage connection settings");
            }
        }
    }
}
