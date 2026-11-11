package icu.tatyrealms.tatykefei.body;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.SessionService;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Resolves official profiles without the server's offline-name fallback or live game state. */
final class BodyProfileLookup implements AutoCloseable {
    private final GameProfileRepository repository;
    private final SessionService sessions;
    private final Cache<String, GameProfile> names = CacheBuilder.newBuilder()
            .maximumSize(256).expireAfterWrite(Duration.ofMinutes(10)).build();
    private final Cache<UUID, GameProfile> profiles = CacheBuilder.newBuilder()
            .maximumSize(256).expireAfterWrite(Duration.ofMinutes(10)).build();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(16), Thread.ofPlatform().daemon().name("TSF body profiles-", 1).factory(),
            new ThreadPoolExecutor.AbortPolicy());

    BodyProfileLookup(GameProfileRepository repository, SessionService sessions) {
        this.repository = repository;
        this.sessions = sessions;
    }

    CompletableFuture<Optional<GameProfile>> resolve(String name, UUID id) {
        return CompletableFuture.supplyAsync(() -> fetch(name, id), executor).orTimeout(20, TimeUnit.SECONDS);
    }

    private Optional<GameProfile> fetch(String name, UUID id) {
        GameProfile cached = id == null ? names.getIfPresent(name.toLowerCase(Locale.ROOT)) : profiles.getIfPresent(id);
        if (cached != null) {
            return Optional.of(cached);
        }
        UUID resolved = id;
        if (resolved == null) {
            var account = repository.findProfileByName(name);
            if (account.isEmpty()) {
                return Optional.empty();
            }
            resolved = account.get().id();
        }
        var result = sessions.fetchProfile(resolved, true);
        if (result == null || !resolved.equals(result.profile().id()) || result.profile().name().isBlank()) {
            return Optional.empty();
        }
        GameProfile profile = result.profile();
        profiles.put(profile.id(), profile);
        names.put(profile.name().toLowerCase(Locale.ROOT), profile);
        return Optional.of(profile);
    }

    @Override
    public void close() {
        executor.shutdownNow();
        names.invalidateAll();
        profiles.invalidateAll();
    }
}
