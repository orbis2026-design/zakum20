package net.orbis.zakum.core.packet;

import net.kyori.adventure.text.Component;
import net.orbis.zakum.api.config.ZakumSettings;
import net.orbis.zakum.api.concurrent.ZakumScheduler;
import net.orbis.zakum.core.metrics.MetricsMonitor;
import net.orbis.zakum.core.perf.PlayerVisualModeService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 1.21.11 packet-only animation service.
 */
public class AnimationService extends AnimationService1_21_11 {

  private final Plugin plugin;
  private final ZakumScheduler scheduler;
  private final boolean adaptiveLodEnabled;
  private final int maxPingMs;
  private final double minTps;
  private final boolean densityCullingEnabled;
  private final int densityThreshold;
  private final int densityRadius;
  private final MetricsMonitor metrics;
  private final PlayerVisualModeService visualModes;

  public AnimationService(Plugin plugin, ZakumScheduler scheduler) {
    this(plugin, scheduler, null, null, null);
  }

  public AnimationService(Plugin plugin, ZakumScheduler scheduler, ZakumSettings.Visuals visuals) {
    this(plugin, scheduler, visuals, null, null);
  }

  public AnimationService(Plugin plugin, ZakumScheduler scheduler, ZakumSettings.Visuals visuals, MetricsMonitor metrics) {
    this(plugin, scheduler, visuals, metrics, null);
  }

  public AnimationService(
    Plugin plugin,
    ZakumScheduler scheduler,
    ZakumSettings.Visuals visuals,
    MetricsMonitor metrics,
    PlayerVisualModeService visualModes
  ) {
    super(plugin, scheduler);
    this.plugin = plugin;
    this.scheduler = scheduler;
    this.metrics = metrics;
    this.visualModes = visualModes;
    var lod = visuals == null ? null : visuals.lod();
    this.adaptiveLodEnabled = lod != null && lod.enabled();
    this.maxPingMs = lod == null ? 180 : lod.maxPingMs();
    this.minTps = lod == null ? 18.5d : lod.minTps();
    var culling = visuals == null ? null : visuals.culling();
    this.densityCullingEnabled = culling != null && culling.enabled();
    this.densityThreshold = culling == null ? 40 : culling.densityThreshold();
    this.densityRadius = culling == null ? 16 : culling.radius();
  }

  public void spawnGhostItem(Player viewer, Location loc, ItemStack item, int entityId) {
    if (viewer == null || loc == null || item == null || loc.getWorld() == null) return;
    DisplayPacketWriter.spawnGhostItem(viewer, loc, item, entityId);
  }

  @Override
  public void spawnCrateItem(Player viewer, Location loc, ItemStack item) {
    if (viewer == null || loc == null || item == null || loc.getWorld() == null) return;
    if (shouldDowngrade(viewer, loc)) {
      sendStaticLabel(viewer, item);
      return;
    }
    int entityId = ThreadLocalRandom.current().nextInt(2_100_000, 2_150_000);
    spawnGhostItem(viewer, loc, item, entityId);
    scheduler.runTaskLater(plugin, () -> tryDestroyPacketDisplay(viewer, entityId), 40L);
  }

  @Override
  public void spawnCrateItem(Player viewer, Location loc) {
    spawnCrateItem(viewer, loc, new ItemStack(Material.CHEST));
  }

  private boolean shouldDowngrade(Player viewer, Location loc) {
    if (visualModes != null) {
      PlayerVisualModeService.Mode mode = visualModes.mode(viewer);
      if (mode == PlayerVisualModeService.Mode.PERFORMANCE) {
        if (metrics != null) metrics.recordAction("animation_cull_profile");
        return true;
      }
      if (mode == PlayerVisualModeService.Mode.QUALITY) {
        return false;
      }
    }

    if (densityCullingEnabled && loc != null && loc.getWorld() != null) {
      int density = loc.getWorld().getNearbyEntities(loc, densityRadius, densityRadius, densityRadius).size();
      if (density >= densityThreshold) {
        if (metrics != null) metrics.recordAction("animation_cull_density");
        return true;
      }
    }

    if (!adaptiveLodEnabled) return false;
    int ping = safePing(viewer);
    double tps = safeTps();
    boolean downgrade = ping >= maxPingMs || tps < minTps;
    if (downgrade && metrics != null) {
      metrics.recordAction(ping >= maxPingMs ? "animation_cull_ping" : "animation_cull_tps");
    }
    return downgrade;
  }

  private static int safePing(Player viewer) {
    try {
      return Math.max(0, viewer.getPing());
    } catch (Throwable ignored) {
      return 0;
    }
  }

  private static double safeTps() {
    try {
      double[] tps = Bukkit.getTPS();
      if (tps == null || tps.length == 0) return 20.0d;
      double oneMinute = tps[0];
      if (Double.isNaN(oneMinute) || oneMinute <= 0.0d) return 20.0d;
      return oneMinute;
    } catch (Throwable ignored) {
      return 20.0d;
    }
  }

  private static void sendStaticLabel(Player viewer, ItemStack item) {
    String label = item.getType().name().toLowerCase().replace('_', ' ');
    viewer.sendActionBar(Component.text(label));
  }
}
