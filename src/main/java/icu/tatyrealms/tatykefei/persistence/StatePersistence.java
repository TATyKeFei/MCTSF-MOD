package icu.tatyrealms.tatykefei.persistence;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import icu.tatyrealms.tatykefei.config.TSFServerConfig;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.LoggerFactory;

/** 工作线程持有数据库状态，游戏线程只提交独立的记录快照。 */
public final class StatePersistence implements AutoCloseable {
    private static final Gson GSON = new Gson();
    private final TSFServerConfig.Storage settings;
    private final Path path;
    private final SharedStateBackend backend;
    private final String token = UUID.randomUUID().toString();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("TSF persistence").factory());
    private final Map<String, String> saved = new HashMap<>();
    private final Map<String, Long> revisions = new HashMap<>();
    private final Set<UUID> sessions = new HashSet<>();
    private final Set<UUID> releasing = new HashSet<>();
    private Update incoming;
    private Submission latest;
    private boolean snapshotScheduled;
    private Map<String, JsonElement> pending = new HashMap<>();
    private Map<UUID, UUID> carriers = Map.of();
    private volatile IOException failure;
    private volatile long lastSuccess = System.nanoTime();
    private volatile boolean closed;
    private final StateStore initial;

    public StatePersistence(TSFServerConfig.Storage settings, Path path) throws IOException {
        this(settings, path, settings.shared() ? SharedStateBackend.create(settings) : null);
    }

    StatePersistence(TSFServerConfig.Storage settings, Path path, SharedStateBackend backend) throws IOException {
        this.settings = settings.copy();
        this.path = path;
        settings.validate();
        this.backend = backend;
        try {
            if (backend == null) {
                initial = settings.type.equals("memory") ? new StateStore() : StateStore.load(path);
            } else {
                long started = System.nanoTime();
                Frame frame = backend.transaction((values, now) -> {
                    heartbeat(values, now, true);
                    return decode(values);
                });
                initial = StateStore.fromRecords(frame.records);
                revisions.putAll(frame.revisions);
                saved.putAll(strings(frame.records));
                lastSuccess = started;
            }
        } catch (IOException | RuntimeException exception) {
            executor.shutdownNow();
            throw exception;
        }
        executor.scheduleWithFixedDelay(this::cycle, settings.syncIntervalSeconds,
                settings.syncIntervalSeconds, TimeUnit.SECONDS);
    }

    public StateStore load() { return initial; }
    public boolean shared() { return backend != null; }
    public boolean ephemeral() { return settings.type.equals("memory"); }
    public String serverId() { return settings.serverId; }
    public int flushIntervalTicks() { return settings.flushIntervalSeconds * 20; }

    public boolean healthy() {
        return backend == null || System.nanoTime() - lastSuccess
                < TimeUnit.SECONDS.toNanos(settings.leaseSeconds - settings.syncIntervalSeconds);
    }

    public CompletableFuture<Void> claim(UUID player) {
        if (backend == null) return CompletableFuture.completedFuture(null);
        return CompletableFuture.runAsync(() -> {
            try {
                if (sessions.contains(player)) throw new IOException("TSF player already has an active local session");
                Set<String> acquired = new HashSet<>();
                long started = System.nanoTime();
                Frame frame = backend.transaction((values, now) -> {
                    heartbeat(values, now, false);
                    String key = "p:" + player;
                    acquired.add(key);
                    Record record = record(values.get(key));
                    if (record != null && record.until > now && !token.equals(record.session)) {
                        throw new IOException("Player TSF data is still in use on another server; retry after disconnect/save");
                    }
                    if (record == null) record = new Record();
                    record.home = settings.serverId;
                    record.session = token;
                    record.until = now + settings.leaseSeconds * 1000L;
                    record.revision++;
                    record.writer = token;
                    values.put(key, GSON.toJson(record));
                    for (String bodyKey : Set.copyOf(values.keySet())) {
                        if (!bodyKey.startsWith("b:")) continue;
                        Record body = record(values.get(bodyKey));
                        if (player.equals(body.carrier)) {
                            acquired.add(bodyKey);
                            body.home = settings.serverId;
                            body.revision++;
                            body.writer = token;
                            values.put(bodyKey, GSON.toJson(body));
                        }
                    }
                    return decode(values);
                });
                sessions.add(player);
                revisions.clear();
                revisions.putAll(frame.revisions);
                acquired.forEach(pending::remove);
                synchronized (this) {
                    if (latest != null) acquired.forEach(latest.records::remove);
                }
                offer(frame.records, acquired);
                lastSuccess = started;
                failure = null;
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        }, executor);
    }

    /** 先保存再释放，失败时保留释放请求供周期任务重试。 */
    public void release(UUID player) {
        if (backend == null || closed) return;
        executor.execute(() -> {
            if (!sessions.contains(player)) return;
            releasing.add(player);
            cycle();
        });
    }

    public synchronized Update poll() {
        Update result = incoming;
        incoming = null;
        return result;
    }

    private synchronized void offer(Map<String, JsonElement> records, Set<String> acquired) {
        Set<String> all = new HashSet<>(acquired);
        if (incoming != null) all.addAll(incoming.acquired);
        incoming = new Update(records, all);
    }

    public record Update(Map<String, JsonElement> records, Set<String> acquired) { }

    public synchronized void submit(Map<String, JsonElement> snapshot, Map<UUID, UUID> carriers) {
        if (closed) throw new IllegalStateException("TSF storage is closed");
        // 调用方已序列化，之后不再接触实时玩家或物品对象。
        Map<String, JsonElement> detached = new HashMap<>();
        snapshot.forEach((key, value) -> detached.put(key, value.deepCopy()));
        Map<UUID, UUID> detachedCarriers = new HashMap<>(carriers);
        latest = new Submission(detached, detachedCarriers);
        if (!snapshotScheduled) {
            snapshotScheduled = true;
            executor.execute(() -> {
                synchronized (this) { snapshotScheduled = false; }
                cycle();
            });
        }
    }

    private void cycle() {
        if (closed) return;
        try {
            persist();
            failure = null;
        } catch (IOException | RuntimeException exception) {
            failed(new IOException("TSF storage synchronization failed", exception));
        }
    }

    private void failed(IOException exception) {
        if (failure == null) LoggerFactory.getLogger("TSF").error("TSF 存储失败，将重试；共享存储失去租约时停止服务以保护数据", exception);
        failure = exception;
    }

    private void persist() throws IOException {
        synchronized (this) {
            if (latest != null) {
                pending = latest.records;
                carriers = latest.carriers;
                latest = null;
            }
        }
        if (backend == null) {
            if (!settings.type.equals("memory") && !strings(pending).equals(saved) && !pending.isEmpty()) {
                StateStore.write(path, StateStore.json(pending));
                saved.clear();
                saved.putAll(strings(pending));
            }
            success();
            return;
        }
        Map<String, String> snapshot = strings(pending);
        long started = System.nanoTime();
        Frame frame = backend.transaction((values, now) -> {
            heartbeat(values, now, false);
            for (var entry : snapshot.entrySet()) {
                String key = entry.getKey();
                JsonObject data = JsonParser.parseString(entry.getValue()).getAsJsonObject();
                if (data.has("storageServer") && !settings.serverId.equals(data.get("storageServer").getAsString())) continue;
                Record record = record(values.get(key));
                if (record != null && !settings.serverId.equals(record.home)) continue;
                if (record != null && record.revision != revisions.getOrDefault(key, record.revision)
                        && !token.equals(record.writer)) {
                    throw new IOException("TSF record revision conflict: " + key);
                }
                UUID carrier = key.startsWith("b:") ? carriers.get(UUID.fromString(key.substring(2))) : null;
                if (key.startsWith("b:")) {
                    if (carrier == null) data.remove("storageCarrier");
                    else data.addProperty("storageCarrier", carrier.toString());
                }
                boolean changed = !GSON.toJson(data).equals(saved.get(key)) || (record != null && !java.util.Objects.equals(record.carrier, carrier));
                if (!changed) continue;
                if (record == null) record = new Record();
                record.home = settings.serverId;
                record.data = data;
                record.carrier = carrier;
                record.revision++;
                record.writer = token;
                values.put(key, GSON.toJson(record));
            }
            for (UUID player : sessions) {
                String key = "p:" + player;
                Record record = record(values.get(key));
                if (record == null || !token.equals(record.session)) {
                    // 释放已提交但响应丢失时，允许重试完成本地清理。
                    if (releasing.contains(player)) continue;
                    throw new IOException("TSF player lease lost: " + player);
                }
                if (releasing.contains(player)) { record.session = null; record.until = 0; }
                else record.until = now + settings.leaseSeconds * 1000L;
                values.put(key, GSON.toJson(record));
            }
            return decode(values);
        });
        saved.clear();
        saved.putAll(strings(frame.records));
        revisions.clear();
        revisions.putAll(frame.revisions);
        sessions.removeAll(releasing);
        releasing.clear();
        offer(frame.records, Set.of());
        lastSuccess = started;
        failure = null;
    }

    private void heartbeat(Map<String, String> values, long now, boolean initial) throws IOException {
        String key = "s:" + settings.serverId;
        Lease lease = GSON.fromJson(values.get(key), Lease.class);
        if (initial) {
            if (lease != null && lease.until > now) throw new IOException("Duplicate active TSF server-id: " + settings.serverId);
        } else if (lease == null || !token.equals(lease.token) || lease.until <= now) {
            throw new IOException("TSF server lease lost; restart this server after restoring storage");
        }
        Lease next = new Lease();
        next.token = token;
        next.until = now + settings.leaseSeconds * 1000L;
        values.put(key, GSON.toJson(next));
    }

    private Frame decode(Map<String, String> values) throws IOException {
        Map<String, JsonElement> result = new HashMap<>();
        Map<String, Long> nextRevisions = new HashMap<>();
        for (var entry : values.entrySet()) {
            if (!entry.getKey().startsWith("p:") && !entry.getKey().startsWith("b:")) continue;
            Record record = record(entry.getValue());
            if (record.data == null) continue;
            JsonObject data = record.data.deepCopy().getAsJsonObject();
            data.addProperty("storageServer", record.home);
            if (entry.getKey().startsWith("b:")) {
                if (record.carrier == null) data.remove("storageCarrier");
                else data.addProperty("storageCarrier", record.carrier.toString());
            }
            result.put(entry.getKey(), data);
            nextRevisions.put(entry.getKey(), record.revision);
        }
        return new Frame(result, nextRevisions);
    }

    private static Record record(String text) throws IOException {
        if (text == null) return null;
        try {
            Record record = GSON.fromJson(text, Record.class);
            if (record == null || record.revision < 0) throw new IOException("Invalid TSF storage record");
            return record;
        } catch (RuntimeException exception) {
            throw new IOException("Invalid TSF storage record", exception);
        }
    }

    private static Map<String, String> strings(Map<String, JsonElement> values) {
        Map<String, String> result = new HashMap<>();
        values.forEach((key, value) -> result.put(key, GSON.toJson(value)));
        return result;
    }

    private void success() { lastSuccess = System.nanoTime(); failure = null; }

    public void flush() throws IOException {
        try {
            executor.submit(() -> { cycle(); }).get();
            if (failure != null) throw failure;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted saving TSF state", exception);
        } catch (java.util.concurrent.ExecutionException exception) {
            throw new IOException("Cannot save TSF state", exception.getCause());
        }
    }

    public void close() throws IOException {
        IOException problem = null;
        try {
            flush();
            if (backend != null) executor.submit(() -> {
                try {
                    backend.transaction((values, now) -> {
                        Lease lease = GSON.fromJson(values.get("s:" + settings.serverId), Lease.class);
                        if (lease != null && token.equals(lease.token)) values.remove("s:" + settings.serverId);
                        for (UUID player : sessions) {
                            String key = "p:" + player;
                            Record record = record(values.get(key));
                            if (record != null && token.equals(record.session)) {
                                record.session = null; record.until = 0;
                                values.put(key, GSON.toJson(record));
                            }
                        }
                        return null;
                    });
                } catch (IOException exception) { throw new CompletionException(exception); }
            }).get();
        } catch (IOException exception) { problem = exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); problem = new IOException(exception);
        } catch (java.util.concurrent.ExecutionException exception) { problem = new IOException(exception.getCause());
        } finally {
            closed = true;
            executor.shutdownNow();
        }
        if (problem != null) throw problem;
    }

    private record Frame(Map<String, JsonElement> records, Map<String, Long> revisions) { }
    private record Submission(Map<String, JsonElement> records, Map<UUID, UUID> carriers) { }

    private static final class Record {
        long revision;
        String writer;
        String home;
        JsonElement data;
        UUID carrier;
        String session;
        long until;
    }
    private static final class Lease { String token; long until; }
}
