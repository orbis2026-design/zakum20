package net.orbis.zakum.battlepass.editor;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small, bounded chat-input session manager for admin editors.
 *
 * - One active session per player.
 * - Sessions expire automatically (to avoid leaks).
 * - All operations are safe on reload/disable.
 */
public final class EditorSessionManager {

  private final Plugin plugin;
  private final long timeoutMs;
  private final ConcurrentHashMap<UUID, EditSession> sessions = new ConcurrentHashMap<>();

  public EditorSessionManager(Plugin plugin, long timeoutMs) {
    this.plugin = plugin;
    this.timeoutMs = Math.max(5_000L, timeoutMs);
  }

  public EditSession get(UUID playerId) {
    if (playerId == null) return null;
    EditSession s = sessions.get(playerId);
    if (s == null) return null;
    if (System.currentTimeMillis() > s.expiresAtMs()) {
      end(playerId, true);
      return null;
    }
    return s;
  }

  public void begin(Player p, EditKind kind, String a, String b, int index) {
    if (p == null || kind == null) return;
    UUID id = p.getUniqueId();

    end(id, true);

    long exp = System.currentTimeMillis() + timeoutMs;

    int taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
      EditSession cur = sessions.get(id);
      if (cur == null) return;
      if (System.currentTimeMillis() <= cur.expiresAtMs()) return;
      sessions.remove(id);
      Player pl = Bukkit.getPlayer(id);
      if (pl != null) pl.sendMessage(ChatColor.GRAY + "Editor input timed out.");
    }, Math.max(1L, timeoutMs / 50L));

    sessions.put(id, new EditSession(id, kind, a, b, index, exp, taskId));
  }

  public void end(UUID playerId, boolean cancelTask) {
    if (playerId == null) return;
    EditSession s = sessions.remove(playerId);
    if (s != null && cancelTask) {
      try { Bukkit.getScheduler().cancelTask(s.timeoutTaskId()); } catch (Throwable ignored) {}
    }
  }

  public void shutdown() {
    for (Map.Entry<UUID, EditSession> e : sessions.entrySet()) {
      try { Bukkit.getScheduler().cancelTask(e.getValue().timeoutTaskId()); } catch (Throwable ignored) {}
    }
    sessions.clear();
  }
}
