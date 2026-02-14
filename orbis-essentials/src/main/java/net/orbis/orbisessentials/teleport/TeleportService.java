package net.orbis.orbisessentials.teleport;

import net.orbis.zakum.api.ZakumApi;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TeleportService {

  private final Plugin plugin;
  private final Messages msg;

  private final Map<UUID, PendingTp> pending = new ConcurrentHashMap<>();
  private final Map<UUID, Long> cooldownUntilMs = new ConcurrentHashMap<>();

  public TeleportService(Plugin plugin, Messages msg) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.msg = Objects.requireNonNull(msg, "msg");
  }

  public boolean teleport(Player p, Location dest, int warmupSeconds, boolean cancelOnMove, double maxMoveBlocks, int cooldownSeconds) {
    if (p == null || dest == null) return false;

    long now = System.currentTimeMillis();
    long cdUntil = cooldownUntilMs.getOrDefault(p.getUniqueId(), 0L);
    if (cdUntil > now) return false;

    cancel(p.getUniqueId());

    if (warmupSeconds <= 0) {
      doTeleport(p, dest);
      if (cooldownSeconds > 0) cooldownUntilMs.put(p.getUniqueId(), now + cooldownSeconds * 1000L);
      return true;
    }

    Location start = p.getLocation().clone();
    p.sendMessage(msg.pref(msg.tpWarmup().replace("{seconds}", String.valueOf(warmupSeconds))));

    int taskId = ZakumApi.get().getScheduler().runTaskLater(plugin, () -> {
      PendingTp pt = pending.remove(p.getUniqueId());
      if (pt == null) return;

      Player live = Bukkit.getPlayer(p.getUniqueId());
      if (live == null) return;

      if (pt.cancelOnMove) {
        Location cur = live.getLocation();
        if (cur.getWorld() != null && pt.start.getWorld() != null) {
          if (!cur.getWorld().getUID().equals(pt.start.getWorld().getUID())
            || cur.distanceSquared(pt.start) > (pt.maxMoveBlocks * pt.maxMoveBlocks)) {
            live.sendMessage(msg.pref(msg.tpCancelledMove()));
            return;
          }
        }
      }

      doTeleport(live, pt.dest);
      if (cooldownSeconds > 0) cooldownUntilMs.put(live.getUniqueId(), System.currentTimeMillis() + cooldownSeconds * 1000L);
    }, warmupSeconds * 20L);

    pending.put(p.getUniqueId(), new PendingTp(start, dest.clone(), cancelOnMove, maxMoveBlocks, taskId));
    return true;
  }

  public void cancel(UUID uuid) {
    PendingTp pt = pending.remove(uuid);
    if (pt != null) ZakumApi.get().getScheduler().cancelTask(pt.taskId());
  }

  private void doTeleport(Player p, Location dest) {
    // Paper: teleportAsync avoids sync chunk loads.
    p.teleportAsync(dest).whenComplete((ok, err) -> {
      if (err != null) return;
      if (ok) p.sendMessage(msg.pref(msg.tpSuccess()));
    });
  }

  public record PendingTp(Location start, Location dest, boolean cancelOnMove, double maxMoveBlocks, int taskId) {}
}

