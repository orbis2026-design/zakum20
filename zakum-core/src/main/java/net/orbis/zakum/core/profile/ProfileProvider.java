package net.orbis.zakum.core.profile;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import net.orbis.zakum.api.storage.DataStore;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Prefetches profile documents during async pre-login.
 */
public final class ProfileProvider implements Listener {

  private static final long DEFAULT_MAX_PREFETCH_ENTRIES = 10_000L;
  private static final long DEFAULT_PREFETCH_TTL_SECONDS = 120L;

  private final DataStore dataStore;
  private final Cache<UUID, CompletableFuture<String>> prefetchedProfiles;

  public ProfileProvider(DataStore dataStore) {
    this(dataStore, DEFAULT_MAX_PREFETCH_ENTRIES, DEFAULT_PREFETCH_TTL_SECONDS);
  }

  public ProfileProvider(DataStore dataStore, long maximumPrefetchEntries, long prefetchTtlSeconds) {
    this.dataStore = Objects.requireNonNull(dataStore, "dataStore");
    long maxEntries = Math.max(100L, maximumPrefetchEntries);
    long ttlSeconds = Math.max(5L, prefetchTtlSeconds);
    this.prefetchedProfiles = Caffeine.newBuilder()
      .maximumSize(maxEntries)
      .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
      .removalListener((UUID __, CompletableFuture<String> future, RemovalCause cause) -> {
        if (future != null && !future.isDone() && cause != RemovalCause.REPLACED) {
          future.cancel(false);
        }
      })
      .build();
  }

  @EventHandler
  public void onPreLogin(AsyncPlayerPreLoginEvent event) {
    UUID uuid = event.getUniqueId();
    prefetchedProfiles.asMap().compute(uuid, (id, existing) -> {
      if (existing != null && !existing.isDone()) return existing;
      return dataStore.loadProfile(uuid).exceptionally(ex -> "{}");
    });
  }

  public CompletableFuture<String> takeOrLoad(UUID uuid) {
    CompletableFuture<String> prefetched = prefetchedProfiles.asMap().remove(uuid);
    if (prefetched != null) return prefetched;
    return dataStore.loadProfile(uuid);
  }

  public void invalidate(UUID uuid) {
    if (uuid == null) return;
    prefetchedProfiles.invalidate(uuid);
  }

  public void clear() {
    prefetchedProfiles.invalidateAll();
  }
}
