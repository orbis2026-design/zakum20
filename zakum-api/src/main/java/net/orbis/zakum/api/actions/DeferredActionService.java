package net.orbis.zakum.api.actions;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Stores offline-attributed actions by player name and replays them when the player joins.
 *
 * Design intent:
 * - supports votes, offline purchases, offline rewards
 * - avoids username->uuid lookups; UUID is resolved on join
 * - DB-backed, async APIs
 */
public interface DeferredActionService {

  /**
   * Enqueue an action for a player name (case-insensitive).
   *
   * @param serverId server scope id; use null for network-wide actions
   */
  CompletableFuture<Void> enqueue(String serverId, String playerName, DeferredAction action, long ttlSeconds, String source);

  /**
   * Claims and deletes pending actions for this player name and server, returning ActionEvents
   * already attributed to the given UUID.
   */
  CompletableFuture<List<ActionEvent>> claim(String serverId, String playerName, UUID playerId, int limit);
}
