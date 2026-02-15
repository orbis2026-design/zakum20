package net.orbis.orbisworlds.service;

import net.orbis.orbisworlds.config.ManagedWorldConfig;
import net.orbis.orbisworlds.config.WorldsConfig;
import net.orbis.zakum.api.ZakumApi;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

public final class DefaultWorldsService implements WorldsService {

  private final Plugin plugin;
  private final ZakumApi zakum;
  private final Logger logger;

  private volatile WorldsConfig config;
  private volatile boolean running;
  private volatile int taskId = -1;
  private volatile int loadedManagedWorlds;

  public DefaultWorldsService(Plugin plugin, ZakumApi zakum, WorldsConfig config, Logger logger) {
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
    loadedManagedWorlds = 0;
  }

  @Override
  public void reload(WorldsConfig config) {
    this.config = config;
    if (!running) return;
    if (config.enabled()) {
      startTask();
      tick();
    } else {
      stopTask();
      loadedManagedWorlds = 0;
    }
    logger.info("OrbisWorlds config reloaded. managedWorlds=" + config.managedWorlds().size());
  }

  @Override
  public WorldsStatus snapshot() {
    WorldsConfig cfg = config;
    return new WorldsStatus(
      running,
      cfg.enabled(),
      cfg.updateIntervalTicks(),
      cfg.autoLoad(),
      cfg.safeTeleport(),
      cfg.maxParallelWorldLoads(),
      cfg.managedWorlds().size(),
      loadedManagedWorlds,
      taskId
    );
  }

  private void tick() {
    WorldsConfig cfg = config;
    int loaded = 0;
    for (ManagedWorldConfig worldConfig : cfg.managedWorlds().values()) {
      World world = Bukkit.getWorld(worldConfig.worldName());
      if (world != null) loaded++;
    }
    loadedManagedWorlds = loaded;
  }

  private void startTask() {
    stopTask();
    WorldsConfig cfg = config;
    long period = WorldsConfig.clamp(cfg.updateIntervalTicks(), 10, 400);
    taskId = zakum.getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, 0L, period);
  }

  private void stopTask() {
    if (taskId >= 0) {
      zakum.getScheduler().cancelTask(taskId);
      taskId = -1;
    }
  }
}
