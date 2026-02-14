package net.orbis.zakum.core.packet;

import net.kyori.adventure.text.Component;
import net.orbis.zakum.api.config.ZakumSettings;
import net.orbis.zakum.api.concurrent.ZakumScheduler;
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

  public AnimationService(Plugin plugin, ZakumScheduler scheduler) {
    this(plugin, scheduler, null);
  }

  public AnimationService(Plugin plugin, ZakumScheduler scheduler, ZakumSettings.Visuals visuals) {
    super(plugin, scheduler);
    this.plugin = plugin;
    this.scheduler = scheduler;
    var lod = visuals == null ? null : visuals.lod();
    this.adaptiveLodEnabled = lod != null && lod.enabled();
    this.maxPingMs = lod == null ? 180 : lod.maxPingMs();
    this.minTps = lod == null ? 18.5d : lod.minTps();
  }

  public void spawnGhostItem(Player viewer, Location loc, ItemStack item, int entityId) {
    if (viewer == null || loc == null || item == null || loc.getWorld() == null) return;
    DisplayPacketWriter.spawnGhostItem(viewer, loc, item, entityId);
  }

  @Override
  public void spawnCrateItem(Player viewer, Location loc, ItemStack item) {
    if (viewer == null || loc == null || item == null || loc.getWorld() == null) return;
    if (shouldDowngrade(viewer)) {
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

  private boolean shouldDowngrade(Player viewer) {
    if (!adaptiveLodEnabled) return false;
    int ping = safePing(viewer);
    double tps = safeTps();
    return ping >= maxPingMs || tps < minTps;
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
