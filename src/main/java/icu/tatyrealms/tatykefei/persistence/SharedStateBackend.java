package icu.tatyrealms.tatykefei.persistence;

import icu.tatyrealms.tatykefei.config.TSFServerConfig;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/** 短事务只写发生变化的记录；时间来自存储服务器，避免各游戏服时钟偏差。 */
public interface SharedStateBackend {
    <T> T transaction(Transaction<T> transaction) throws IOException;

    interface Transaction<T> {
        T run(Map<String, String> records, long now) throws IOException;
    }

    static SharedStateBackend create(TSFServerConfig.Storage settings) throws IOException {
        settings.validate();
        return switch (settings.type) {
            case "postgresql", "mariadb" -> new Sql(settings);
            case "redis", "valkey" -> new Redis(settings);
            default -> throw new IOException("Not a shared storage backend: " + settings.type);
        };
    }

    final class Sql implements SharedStateBackend {
        private final TSFServerConfig.Storage settings;
        private final boolean postgres;
        private final String url;
        private final Properties properties = new Properties();

        Sql(TSFServerConfig.Storage settings) throws IOException {
            this.settings = settings.copy();
            postgres = settings.type.equals("postgresql");
            try {
                Class.forName(postgres ? "org.postgresql.Driver" : "org.mariadb.jdbc.Driver");
            } catch (ClassNotFoundException exception) {
                throw new IOException("Missing bundled SQL driver", exception);
            }
            String host = settings.sqlHost.contains(":") ? "[" + settings.sqlHost + "]" : settings.sqlHost;
            url = "jdbc:" + settings.type + "://" + host + ":" + settings.sqlPort + "/" + settings.sqlDatabase;
            properties.setProperty("user", settings.sqlUsername);
            properties.setProperty("password", settings.sqlPassword);
            properties.setProperty("connectTimeout", postgres ? "5" : "5000");
            properties.setProperty("socketTimeout", postgres ? "10" : "10000");
            properties.setProperty(postgres ? "sslmode" : "sslMode", settings.sqlTls ? "verify-full" : "disable");
            try (Connection connection = connect(); var statement = connection.createStatement()) {
                String namespaceType = postgres ? "VARCHAR(48)" : "VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin";
                String keyType = postgres ? "VARCHAR(128)" : "VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin";
                String engine = postgres ? "" : " ENGINE=InnoDB";
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS tsf_namespaces (namespace " + namespaceType + " PRIMARY KEY, schema_version INTEGER NOT NULL)" + engine);
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS tsf_records (namespace " + namespaceType + " NOT NULL, record_key " + keyType + " NOT NULL, value "
                        + (postgres ? "TEXT" : "LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin") + " NOT NULL, PRIMARY KEY(namespace,record_key))" + engine);
                try (var insert = connection.prepareStatement("INSERT INTO tsf_namespaces(namespace,schema_version) VALUES (?,1) "
                        + (postgres ? "ON CONFLICT DO NOTHING" : "ON DUPLICATE KEY UPDATE namespace=namespace"))) {
                    insert.setString(1, settings.namespace);
                    insert.executeUpdate();
                }
            } catch (SQLException exception) {
                throw new IOException("Cannot initialize TSF SQL storage", exception);
            }
        }

        private Connection connect() throws SQLException {
            return DriverManager.getConnection(url, properties);
        }

        public <T> T transaction(Transaction<T> transaction) throws IOException {
            try (Connection connection = connect()) {
                connection.setAutoCommit(false);
                try {
                    try (var lock = connection.prepareStatement("SELECT schema_version FROM tsf_namespaces WHERE namespace=? FOR UPDATE")) {
                        lock.setQueryTimeout(10);
                        lock.setString(1, settings.namespace);
                        try (var result = lock.executeQuery()) {
                            if (!result.next() || result.getInt(1) != 1) throw new IOException("Unsupported TSF storage schema");
                        }
                    }
                    long now;
                    try (var statement = connection.createStatement(); var result = statement.executeQuery(postgres
                            ? "SELECT CAST(EXTRACT(EPOCH FROM clock_timestamp())*1000 AS BIGINT)"
                            : "SELECT CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000 AS SIGNED)")) {
                        result.next();
                        now = result.getLong(1);
                    }
                    Map<String, String> original = new HashMap<>();
                    try (var query = connection.prepareStatement("SELECT record_key,value FROM tsf_records WHERE namespace=?")) {
                        query.setString(1, settings.namespace);
                        try (var result = query.executeQuery()) {
                            while (result.next()) original.put(result.getString(1), result.getString(2));
                        }
                    }
                    Map<String, String> records = new HashMap<>(original);
                    T value = transaction.run(records, now);
                    String upsert = "INSERT INTO tsf_records(namespace,record_key,value) VALUES (?,?,?) "
                            + (postgres ? "ON CONFLICT(namespace,record_key) DO UPDATE SET value=EXCLUDED.value"
                            : "ON DUPLICATE KEY UPDATE value=VALUES(value)");
                    try (var update = connection.prepareStatement(upsert); var delete = connection.prepareStatement(
                            "DELETE FROM tsf_records WHERE namespace=? AND record_key=?")) {
                        for (var entry : records.entrySet()) {
                            if (entry.getValue().equals(original.get(entry.getKey()))) continue;
                            update.setString(1, settings.namespace);
                            update.setString(2, entry.getKey());
                            update.setString(3, entry.getValue());
                            update.addBatch();
                        }
                        update.executeBatch();
                        for (String key : original.keySet()) {
                            if (records.containsKey(key)) continue;
                            delete.setString(1, settings.namespace);
                            delete.setString(2, key);
                            delete.addBatch();
                        }
                        delete.executeBatch();
                    }
                    connection.commit();
                    return value;
                } catch (IOException | SQLException | RuntimeException exception) {
                    connection.rollback();
                    throw exception;
                }
            } catch (SQLException | RuntimeException exception) {
                throw new IOException("TSF SQL transaction failed", exception);
            }
        }
    }

    final class Redis implements SharedStateBackend {
        private static final String COMMIT = "if redis.call('GET',KEYS[1]) ~= ARGV[1] then return 0 end "
                + "for i=2,#ARGV,2 do if ARGV[i+1] == '' then redis.call('HDEL',KEYS[2],ARGV[i]) "
                + "else redis.call('HSET',KEYS[2],ARGV[i],ARGV[i+1]) end end redis.call('DEL',KEYS[1]); return 1";
        private static final String UNLOCK = "if redis.call('GET',KEYS[1]) == ARGV[1] then return redis.call('DEL',KEYS[1]) end return 0";
        private final TSFServerConfig.Storage settings;
        Redis(TSFServerConfig.Storage settings) { this.settings = settings.copy(); }

        public <T> T transaction(Transaction<T> transaction) throws IOException {
            String prefix = "tsf:{" + settings.namespace + "}:";
            String token = UUID.randomUUID().toString();
            try (Resp client = new Resp(settings)) {
                long deadline = System.nanoTime() + 5_000_000_000L;
                while (client.command("SET", prefix + "lock", token, "NX", "PX", "15000") == null) {
                    if (System.nanoTime() >= deadline) throw new IOException("TSF storage transaction is busy");
                    try { Thread.sleep(25); } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted waiting for TSF storage", exception);
                    }
                }
                try {
                    List<?> time = (List<?>) client.command("TIME");
                    long now = Long.parseLong((String) time.get(0)) * 1000 + Long.parseLong((String) time.get(1)) / 1000;
                    List<?> values = (List<?>) client.command("HGETALL", prefix + "records");
                    Map<String, String> original = new HashMap<>();
                    for (int i = 0; i < values.size(); i += 2) original.put((String) values.get(i), (String) values.get(i + 1));
                    Map<String, String> records = new HashMap<>(original);
                    String schema = records.putIfAbsent("schema", "1");
                    if (schema != null && !schema.equals("1")) throw new IOException("Unsupported TSF storage schema");
                    T value = transaction.run(records, now);
                    List<String> arguments = new ArrayList<>(List.of("EVAL", COMMIT, "2", prefix + "lock", prefix + "records", token));
                    for (var entry : records.entrySet()) {
                        if (entry.getValue().equals(original.get(entry.getKey()))) continue;
                        arguments.add(entry.getKey());
                        arguments.add(entry.getValue());
                    }
                    for (String key : original.keySet()) {
                        if (!records.containsKey(key)) { arguments.add(key); arguments.add(""); }
                    }
                    if (!Long.valueOf(1).equals(client.command(arguments.toArray(String[]::new)))) {
                        throw new IOException("TSF storage transaction lease expired; changes were discarded");
                    }
                    return value;
                } finally {
                    client.command("EVAL", UNLOCK, "1", prefix + "lock", token);
                }
            } catch (RuntimeException exception) {
                throw new IOException("Invalid Redis/Valkey response", exception);
            }
        }
    }

    /** 每个事务使用独立连接，失败后的重试不会复用失效连接。 */
    final class Resp implements AutoCloseable {
        private final Socket socket;
        private final BufferedInputStream input;
        private final BufferedOutputStream output;

        Resp(TSFServerConfig.Storage settings) throws IOException {
            socket = settings.redisTls ? SSLSocketFactory.getDefault().createSocket() : new Socket();
            try {
                socket.connect(new InetSocketAddress(settings.redisHost, settings.redisPort), 5000);
                socket.setSoTimeout(10000);
                if (socket instanceof SSLSocket secure) {
                    SSLParameters parameters = secure.getSSLParameters();
                    parameters.setEndpointIdentificationAlgorithm("HTTPS");
                    secure.setSSLParameters(parameters);
                    secure.startHandshake();
                }
                input = new BufferedInputStream(socket.getInputStream());
                output = new BufferedOutputStream(socket.getOutputStream());
                if (!settings.redisPassword.isEmpty()) {
                    if (settings.redisUsername.isEmpty()) command("AUTH", settings.redisPassword);
                    else command("AUTH", settings.redisUsername, settings.redisPassword);
                }
                command("SELECT", Integer.toString(settings.redisDatabase));
            } catch (IOException | RuntimeException exception) {
                socket.close();
                throw exception;
            }
        }

        Object command(String... arguments) throws IOException {
            output.write(("*" + arguments.length + "\r\n").getBytes(StandardCharsets.US_ASCII));
            for (String argument : arguments) {
                byte[] bytes = argument.getBytes(StandardCharsets.UTF_8);
                output.write(("$" + bytes.length + "\r\n").getBytes(StandardCharsets.US_ASCII));
                output.write(bytes);
                output.write('\r'); output.write('\n');
            }
            output.flush();
            return response(0);
        }

        private Object response(int depth) throws IOException {
            if (depth > 4) throw new IOException("Redis response nesting limit exceeded");
            int prefix = input.read();
            if (prefix < 0) throw new EOFException("Redis closed the connection");
            String line = line();
            return switch (prefix) {
                case '+' -> line;
                case '-' -> throw new IOException("Redis rejected TSF storage command: " + line);
                case ':' -> Long.parseLong(line);
                case '$' -> {
                    int size = Integer.parseInt(line);
                    if (size == -1) yield null;
                    if (size < 0 || size > 268435456) throw new IOException("Invalid Redis bulk size");
                    byte[] bytes = input.readNBytes(size);
                    if (bytes.length != size || input.read() != '\r' || input.read() != '\n') throw new EOFException("Truncated Redis response");
                    yield new String(bytes, StandardCharsets.UTF_8);
                }
                case '*' -> {
                    int size = Integer.parseInt(line);
                    if (size < 0 || size > 1000000) throw new IOException("Invalid Redis array size");
                    List<Object> result = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) result.add(response(depth + 1));
                    yield result;
                }
                default -> throw new IOException("Unsupported Redis response type");
            };
        }

        private String line() throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            while (bytes.size() < 65536) {
                int current = input.read();
                if (current < 0) throw new EOFException("Truncated Redis response");
                if (current == '\r') {
                    if (input.read() != '\n') throw new IOException("Invalid Redis line ending");
                    return bytes.toString(StandardCharsets.UTF_8);
                }
                bytes.write(current);
            }
            throw new IOException("Redis response line too long");
        }

        public void close() throws IOException { socket.close(); }
    }
}
