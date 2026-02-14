package net.orbis.zakum.api.luckperms;

import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Optional LuckPerms bridge.
 *
 * Read-only by default: this service is intended to expose authoritative meta
 * (prefix/suffix/groups) without mutating permission state.
 */
public interface LuckPermsService {

  boolean available();

  /**
   * Online fast-path: reads cached meta for this player.
   * Never null.
   */
  String prefix(Player player);

  /**
   * Online fast-path: reads cached meta for this player.
   * Never null.
   */
  String suffix(Player player);

  /**
   * Async: loads user data (if necessary) and returns the prefix in current contexts.
   * Never null.
   */
  CompletableFuture<String> prefix(UUID uuid);

  /**
   * Async: loads user data (if necessary) and returns the suffix in current contexts.
   * Never null.
   */
  CompletableFuture<String> suffix(UUID uuid);

  /**
   * Async: loads user data (if necessary) and returns primary group.
   * Never null.
   */
  CompletableFuture<String> primaryGroup(UUID uuid);

  /**
   * Async: checks if user is in a group (inherited groups included).
   */
  CompletableFuture<Boolean> inGroup(UUID uuid, String groupName);
}
