package net.orbis.zakum.api.social;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Cache-first social relationship graph.
 *
 * Implementations should avoid blocking for hot-path relation checks.
 */
public interface SocialService {

  SocialSnapshot snapshot(UUID playerId);

  CompletableFuture<SocialSnapshot> refreshAsync(UUID playerId);

  void upsert(UUID playerId, SocialSnapshot snapshot);

  void invalidate(UUID playerId);

  default boolean isFriend(UUID playerId, UUID otherId) {
    if (playerId == null || otherId == null) return false;
    return snapshot(playerId).friends().contains(otherId);
  }

  default boolean isAlly(UUID playerId, UUID otherId) {
    if (playerId == null || otherId == null) return false;
    return snapshot(playerId).allies().contains(otherId);
  }

  default boolean isRival(UUID playerId, UUID otherId) {
    if (playerId == null || otherId == null) return false;
    return snapshot(playerId).rivals().contains(otherId);
  }

  record SocialSnapshot(Set<UUID> friends, Set<UUID> allies, Set<UUID> rivals, long loadedAtEpochMs) {
    public static final SocialSnapshot EMPTY = new SocialSnapshot(Set.of(), Set.of(), Set.of(), 0L);

    public SocialSnapshot {
      friends = friends == null ? Set.of() : Set.copyOf(friends);
      allies = allies == null ? Set.of() : Set.copyOf(allies);
      rivals = rivals == null ? Set.of() : Set.copyOf(rivals);
    }
  }
}
