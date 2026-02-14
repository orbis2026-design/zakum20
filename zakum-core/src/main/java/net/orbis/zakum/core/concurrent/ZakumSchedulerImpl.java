package net.orbis.zakum.core.concurrent;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.orbis.zakum.api.concurrent.ZakumScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class ZakumSchedulerImpl implements ZakumScheduler {

  private final Plugin plugin;
  private final ExecutorService virtualExecutor;
  private final AtomicInteger taskIds;
  private final Map<Integer, ScheduledTask> scheduledTasks;

  public ZakumSchedulerImpl(Plugin plugin) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
    this.taskIds = new AtomicInteger(1);
    this.scheduledTasks = new ConcurrentHashMap<>();
  }

  @Override
  public void runAsync(Runnable task) {
    if (task == null) return;
    virtualExecutor.execute(task);
  }

  @Override
  public void runAtLocation(Location loc, Runnable task) {
    if (task == null) return;
    if (loc == null || loc.getWorld() == null) {
      runGlobal(task);
      return;
    }
    Bukkit.getRegionScheduler().execute(plugin, loc, task);
  }

  @Override
  public void runAtEntity(Entity entity, Runnable task) {
    if (task == null) return;
    if (entity == null) {
      runGlobal(task);
      return;
    }
    entity.getScheduler().execute(plugin, task, null, 1L);
  }

  @Override
  public Executor asyncExecutor() {
    return virtualExecutor;
  }

  @Override
  public void runGlobal(Runnable task) {
    if (task == null) return;
    Bukkit.getGlobalRegionScheduler().execute(plugin, task);
  }

  @Override
  public int runTask(Plugin owner, Runnable task) {
    if (task == null) return -1;
    Plugin effectiveOwner = owner != null ? owner : plugin;
    int id = taskIds.getAndIncrement();
    ScheduledTask scheduled = Bukkit.getGlobalRegionScheduler().run(effectiveOwner, st -> {
      try {
        task.run();
      } finally {
        scheduledTasks.remove(id);
      }
    });
    scheduledTasks.put(id, scheduled);
    return id;
  }

  @Override
  public int runTaskLater(Plugin owner, Runnable task, long delayTicks) {
    if (task == null) return -1;
    Plugin effectiveOwner = owner != null ? owner : plugin;
    long delay = Math.max(1L, delayTicks);
    int id = taskIds.getAndIncrement();
    ScheduledTask scheduled = Bukkit.getGlobalRegionScheduler().runDelayed(effectiveOwner, st -> {
      try {
        task.run();
      } finally {
        scheduledTasks.remove(id);
      }
    }, delay);
    scheduledTasks.put(id, scheduled);
    return id;
  }

  @Override
  public int runTaskTimer(Plugin owner, Runnable task, long delayTicks, long periodTicks) {
    if (task == null) return -1;
    Plugin effectiveOwner = owner != null ? owner : plugin;
    long delay = Math.max(1L, delayTicks);
    long period = Math.max(1L, periodTicks);
    int id = taskIds.getAndIncrement();
    ScheduledTask scheduled = Bukkit.getGlobalRegionScheduler().runAtFixedRate(effectiveOwner, st -> task.run(), delay, period);
    scheduledTasks.put(id, scheduled);
    return id;
  }

  @Override
  public int runTaskTimerAsynchronously(Plugin owner, Runnable task, long delayTicks, long periodTicks) {
    if (task == null) return -1;
    Plugin effectiveOwner = owner != null ? owner : plugin;
    long delayMs = Math.max(1L, delayTicks) * 50L;
    long periodMs = Math.max(1L, periodTicks) * 50L;
    int id = taskIds.getAndIncrement();
    ScheduledTask scheduled = Bukkit.getAsyncScheduler().runAtFixedRate(
      effectiveOwner,
      st -> task.run(),
      delayMs,
      periodMs,
      TimeUnit.MILLISECONDS
    );
    scheduledTasks.put(id, scheduled);
    return id;
  }

  @Override
  public int scheduleSyncDelayedTask(Plugin owner, Runnable task, long delayTicks) {
    return runTaskLater(owner, task, delayTicks);
  }

  @Override
  public int scheduleSyncRepeatingTask(Plugin owner, Runnable task, long delayTicks, long periodTicks) {
    return runTaskTimer(owner, task, delayTicks, periodTicks);
  }

  @Override
  public void cancelTask(int taskId) {
    ScheduledTask task = scheduledTasks.remove(taskId);
    if (task != null) {
      task.cancel();
    }
  }

  public void shutdown() {
    for (ScheduledTask task : scheduledTasks.values()) {
      try {
        task.cancel();
      } catch (Throwable ignored) {}
    }
    scheduledTasks.clear();
    virtualExecutor.shutdownNow();
  }
}
