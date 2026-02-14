package net.orbis.zakum.core.social;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.orbis.zakum.api.concurrent.ZakumScheduler;
import net.orbis.zakum.api.social.SocialService;
import net.orbis.zakum.api.storage.DataStore;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Cache-first social graph service with async refresh.
 */
public final class CaffeineSocialService implements SocialService {

  private static final String FRIENDS_KEY = "social.friends";
  private static final String ALLIES_KEY = "social.allies";
  private static final String RIVALS_KEY = "social.rivals";

  private final ZakumScheduler scheduler;
  private final DataStore dataStore;
  private final Cache<UUID, SocialSnapshot> cache;
  private final Set<UUID> inFlight;

  public CaffeineSocialService(ZakumScheduler scheduler, DataStore dataStore, long maximumSize, Duration expireAfterAccess) {
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    this.dataStore = dataStore;
    this.cache = Caffeine.newBuilder()
      .maximumSize(Math.max(1_000L, maximumSize))
      .expireAfterAccess(expireAfterAccess == null ? Duration.ofMinutes(10) : expireAfterAccess)
      .build();
    this.inFlight = ConcurrentHashMap.newKeySet();
  }

  @Override
  public SocialSnapshot snapshot(UUID playerId) {
    if (playerId == null) return SocialSnapshot.EMPTY;
    SocialSnapshot cached = cache.getIfPresent(playerId);
    if (cached != null) return cached;
    refreshAsync(playerId);
    return SocialSnapshot.EMPTY;
  }

  @Override
  public CompletableFuture<SocialSnapshot> refreshAsync(UUID playerId) {
    if (playerId == null) return CompletableFuture.completedFuture(SocialSnapshot.EMPTY);
    if (!inFlight.add(playerId)) {
      SocialSnapshot current = cache.getIfPresent(playerId);
      return CompletableFuture.completedFuture(current == null ? SocialSnapshot.EMPTY : current);
    }
    return scheduler.supplyAsync(() -> {
      try {
        SocialSnapshot loaded = load(playerId);
        cache.put(playerId, loaded);
        return loaded;
      } finally {
        inFlight.remove(playerId);
      }
    });
  }

  @Override
  public void upsert(UUID playerId, SocialSnapshot snapshot) {
    if (playerId == null || snapshot == null) return;
    cache.put(playerId, snapshot);
    if (dataStore == null) return;

    dataStore.setSessionData(playerId, FRIENDS_KEY, join(snapshot.friends()));
    dataStore.setSessionData(playerId, ALLIES_KEY, join(snapshot.allies()));
    dataStore.setSessionData(playerId, RIVALS_KEY, join(snapshot.rivals()));
  }

  @Override
  public void invalidate(UUID playerId) {
    if (playerId == null) return;
    cache.invalidate(playerId);
  }

  private SocialSnapshot load(UUID playerId) {
    if (dataStore == null) return SocialSnapshot.EMPTY;
    Set<UUID> friends = parseUuidSet(dataStore.getSessionData(playerId, FRIENDS_KEY));
    Set<UUID> allies = parseUuidSet(dataStore.getSessionData(playerId, ALLIES_KEY));
    Set<UUID> rivals = parseUuidSet(dataStore.getSessionData(playerId, RIVALS_KEY));
    if (friends.isEmpty() && allies.isEmpty() && rivals.isEmpty()) {
      return SocialSnapshot.EMPTY;
    }
    return new SocialSnapshot(friends, allies, rivals, System.currentTimeMillis());
  }

  private static Set<UUID> parseUuidSet(String raw) {
    if (raw == null || raw.isBlank()) return Set.of();
    return Arrays.stream(raw.split("[,;\\s]+"))
      .map(String::trim)
      .filter(s -> !s.isBlank())
      .map(CaffeineSocialService::parseUuid)
      .filter(Objects::nonNull)
      .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private static UUID parseUuid(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private static String join(Set<UUID> values) {
    if (values == null || values.isEmpty()) return "";
    return values.stream().map(UUID::toString).collect(Collectors.joining(","));
  }
}
