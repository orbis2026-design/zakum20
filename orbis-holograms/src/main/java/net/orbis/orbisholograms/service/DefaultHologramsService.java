package net.orbis.orbisholograms.service;

import net.orbis.orbisholograms.config.HologramDefinition;
import net.orbis.orbisholograms.config.HologramsConfig;
import net.orbis.zakum.api.ZakumApi;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

public final class DefaultHologramsService implements HologramsService {

  private final Plugin plugin;
  private final ZakumApi zakum;
  private final Logger logger;

  private volatile HologramsConfig config;
  private volatile boolean running;
  private volatile int taskId = -1;
  private volatile int visibleAssignments;

  public DefaultHologramsService(Plugin plugin, ZakumApi zakum, HologramsConfig config, Logger logger) {
    this.plugin = plugin;
    this.zakum = zakum;
    this.config = config;
    this.logger = logger;
  }

  @Override
  public void start() {
    if (running) return;
    running = true;
    if (config.enabled()) {
      startTask();
      tick();
    }
  }

  @Override
  public void stop() {
    if (!running) return;
    running = false;
    stopTask();
    visibleAssignments = 0;
  }

  @Override
  public void reload(HologramsConfig config) {
    this.config = config;
    if (!running) return;
    if (config.enabled()) {
      startTask();
      tick();
    } else {
      stopTask();
      visibleAssignments = 0;
    }
    logger.info("OrbisHolograms config reloaded. definitions=" + config.definitions().size());
  }

  @Override
  public HologramsStatus snapshot() {
    HologramsConfig cfg = config;
    return new HologramsStatus(
      running,
      cfg.enabled(),
      cfg.renderTickInterval(),
      cfg.viewDistance(),
      cfg.maxVisiblePerPlayer(),
      cfg.hideThroughWalls(),
      cfg.definitions().size(),
      visibleAssignments,
      taskId
    );
  }

  private void tick() {
    HologramsConfig cfg = config;
    int assignments = 0;
    double maxDistanceSquared = (double) cfg.viewDistance() * (double) cfg.viewDistance();

    for (Player player : Bukkit.getOnlinePlayers()) {
      int playerVisible = 0;
      for (HologramDefinition def : cfg.definitions().values()) {
        World world = Bukkit.getWorld(def.world());
        if (world == null || world != player.getWorld()) continue;
        Location point = new Location(world, def.x(), def.y(), def.z());
        if (player.getLocation().distanceSquared(point) <= maxDistanceSquared) {
          assignments++;
          playerVisible++;
          if (playerVisible >= cfg.maxVisiblePerPlayer()) {
            break;
          }
        }
      }
    }

    visibleAssignments = assignments;
  }

  private void startTask() {
    stopTask();
    HologramsConfig cfg = config;
    long period = HologramsConfig.clamp(cfg.renderTickInterval(), 1, 100);
    taskId = zakum.getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, 0L, period);
  }

  private void stopTask() {
    if (taskId >= 0) {
      zakum.getScheduler().cancelTask(taskId);
      taskId = -1;
    }
  }
}
