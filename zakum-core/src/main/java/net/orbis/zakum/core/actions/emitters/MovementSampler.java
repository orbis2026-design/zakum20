package net.orbis.zakum.core.actions.emitters;

import net.orbis.zakum.api.actions.ActionBus;
import net.orbis.zakum.api.actions.ActionEvent;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Samples player movement at a fixed interval (default 1s).
 *
 * Emits:
 *   type=move
 *   key=mode
 *   value=WALK|FLY|SWIM
 *   amount=centimeters moved since last sample
 *
 * Notes:
 * - single task total
 * - clamps large deltas (teleports) to avoid quest abuse/noise
 */
public final class MovementSampler implements Listener {

  private final Plugin plugin;
  private final ActionBus bus;

  private final int sampleTicks;
  private final long maxCmPerSample;

  private final Map<UUID, Pos> last = new HashMap<>();
  private int taskId = -1;

  public MovementSampler(Plugin plugin, ActionBus bus, int sampleTicks, long maxCmPerSample) {
    this.plugin = plugin;
    this.bus = bus;
    this.sampleTicks = Math.max(1, sampleTicks);
    this.maxCmPerSample = Math.max(1, maxCmPerSample);

    // seed on enable
    for (Player p : Bukkit.getOnlinePlayers()) {
      last.put(p.getUniqueId(), Pos.from(p));
    }
  }

  public void start() {
    if (taskId != -1) return;

    taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, sampleTicks, sampleTicks);
  }

  public void stop() {
    if (taskId != -1) {
      Bukkit.getScheduler().cancelTask(taskId);
      taskId = -1;
    }
    last.clear();
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent e) {
    last.put(e.getPlayer().getUniqueId(), Pos.from(e.getPlayer()));
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent e) {
    last.remove(e.getPlayer().getUniqueId());
  }

  private void tick() {
    for (Player p : Bukkit.getOnlinePlayers()) {
      UUID uuid = p.getUniqueId();

      Pos prev = last.get(uuid);
      if (prev == null) {
        last.put(uuid, Pos.from(p));
        continue;
      }

      Pos now = Pos.from(p);

      if (!sameWorld(prev.world, now.world)) {
        last.put(uuid, now);
        continue;
      }

      double dx = now.x - prev.x;
      double dy = now.y - prev.y;
      double dz = now.z - prev.z;

      double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
      long cm = (long) Math.floor(dist * 100.0);

      if (cm <= 0) {
        last.put(uuid, now);
        continue;
      }

      if (cm > maxCmPerSample) {
        // teleport or chunk jump; reset without publishing
        last.put(uuid, now);
        continue;
      }

      String mode = p.isFlying() ? "FLY" : (p.isSwimming() ? "SWIM" : "WALK");

      bus.publish(new ActionEvent(
        "move",
        uuid,
        cm,
        "mode",
        mode
      ));

      last.put(uuid, now);
    }
  }

  private static boolean sameWorld(World a, World b) {
    if (a == b) return true;
    if (a == null || b == null) return false;
    return a.getUID().equals(b.getUID());
  }

  private record Pos(World world, double x, double y, double z) {
    static Pos from(Player p) {
      var loc = p.getLocation();
      return new Pos(p.getWorld(), loc.getX(), loc.getY(), loc.getZ());
    }
  }
}
