package net.orbis.zakum.battlepass.ui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight name cache for UI/leaderboards.
 *
 * Avoid calling Bukkit APIs off-thread. Names are populated on join (sync).
 */
public final class NameCache {

  private final Map<UUID, String> names = new ConcurrentHashMap<>();

  public void put(UUID uuid, String name) {
    if (uuid == null) return;
    if (name == null || name.isBlank()) return;
    names.put(uuid, name);
  }

  public String get(UUID uuid) {
    if (uuid == null) return null;
    return names.get(uuid);
  }
}
