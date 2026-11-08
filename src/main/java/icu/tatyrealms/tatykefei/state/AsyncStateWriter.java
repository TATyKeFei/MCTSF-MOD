package icu.tatyrealms.tatykefei.state;

import com.google.gson.JsonElement;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.LoggerFactory;

final class AsyncStateWriter implements AutoCloseable {
    private final Path path;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("TSF state writer").factory());
    private JsonElement pending;
    private IOException failure;
    private boolean running;
    private boolean closed;

    AsyncStateWriter(Path path) {
        this.path = path;
    }

    synchronized void submit(JsonElement snapshot) {
        if (closed) {
            throw new IllegalStateException("TSF state writer is closed");
        }
        pending = snapshot;
        if (!running) {
            running = true;
            executor.execute(this::writePending);
        }
    }

    private void writePending() {
        while (true) {
            JsonElement snapshot;
            synchronized (this) {
                snapshot = pending;
                pending = null;
                if (snapshot == null) {
                    running = false;
                    notifyAll();
                    return;
                }
            }
            try {
                StateStore.write(path, snapshot);
                synchronized (this) {
                    failure = null;
                }
            } catch (IOException | RuntimeException exception) {
                synchronized (this) {
                    failure = new IOException("Unable to save TSF state: " + path, exception);
                    if (pending == null) {
                        pending = snapshot;
                    }
                    running = false;
                    notifyAll();
                }
                LoggerFactory.getLogger("TSF").error("Unable to save TSF state: {}", path, exception);
                return;
            }
        }
    }

    synchronized void flush() throws IOException {
        while (running) {
            try {
                wait();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while saving TSF state: " + path, exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public void close() throws IOException {
        synchronized (this) {
            closed = true;
        }
        try {
            flush();
        } finally {
            executor.close();
        }
    }
}
