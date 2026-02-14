package net.orbis.orbisessentials.teleport;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TpaManager {

  private final Map<UUID, Request> byTarget = new ConcurrentHashMap<>();

  public void request(Player from, Player to, long expiresAtMs) {
    byTarget.put(to.getUniqueId(), new Request(from.getUniqueId(), to.getUniqueId(), expiresAtMs));
  }

  public Request popForTarget(UUID target) {
    Request r = byTarget.get(target);
    if (r == null) return null;
    if (r.expiresAtMs < System.currentTimeMillis()) {
      byTarget.remove(target);
      return null;
    }
    byTarget.remove(target);
    return r;
  }

  public record Request(UUID requester, UUID target, long expiresAtMs) {}
}
