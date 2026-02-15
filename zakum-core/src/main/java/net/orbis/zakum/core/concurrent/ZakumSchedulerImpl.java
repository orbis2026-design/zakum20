package net.orbis.zakum.core.concurrent;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.orbis.zakum.api.config.ZakumSettings;
import net.orbis.zakum.api.concurrent.ZakumScheduler;
import net.orbis.zakum.core.metrics.MetricsMonitor;
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
import java.util.logging.Logger;

public final class ZakumSchedulerImpl implements ZakumScheduler {

  private final Plugin plugin;
  private final ExecutorService virtualExecutor;
  private final AsyncBackpressureController backpressure;
  private final AtomicInteger taskIds;
  private final Map<Integer, ScheduledTask> scheduledTasks;

  public ZakumSchedulerImpl(Plugin plugin) {
    this(plugin, Executors.newVirtualThreadPerTaskExecutor(), null, plugin.getLogger(), null);
  }

  public ZakumSchedulerImpl(Plugin plugin, ExecutorService virtualExecutor) {
    this(plugin, virtualExecutor, null, plugin.getLogger(), null);
  }

  public ZakumSchedulerImpl(
    Plugin plugin,
    ExecutorService virtualExecutor,
    ZakumSettings.Operations.Async asyncCfg,
    Logger logger,
    MetricsMonitor metrics
  ) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.virtualExecutor = Objects.requireNonNull(virtualExecutor, "virtualExecutor");
    this.backpressure = new AsyncBackpressureController(
      this.virtualExecutor,
      asyncCfg,
      logger == null ? plugin.getLogger() : logger,
      metrics
    );
    this.taskIds = new AtomicInteger(1);
    this.scheduledTasks = new ConcurrentHashMap<>();
  }

  @Override
  public void runAsync(Runnable task) {
    if (task == null) return;
    backpressure.execute(task);
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

  public AsyncSnapshot asyncSnapshot() {
    AsyncBackpressureController.Snapshot snap = backpressure.snapshot();
    return new AsyncSnapshot(
      snap.configuredEnabled(),
      snap.runtimeEnabled(),
      snap.enabled(),
      snap.maxInFlight(),
      snap.maxQueue(),
      snap.callerRunsOffMainThread(),
      snap.inFlight(),
      snap.queued(),
      snap.submitted(),
      snap.executed(),
      snap.queuedTasks(),
      snap.rejected(),
      snap.callerRuns(),
      snap.drainRuns(),
      snap.lastQueueAtMs(),
      snap.lastRejectAtMs(),
      snap.lastCallerRunAtMs()
    );
  }

  public void setAsyncRuntimeEnabled(boolean enabled) {
    backpressure.setRuntimeEnabled(enabled);
  }

  public boolean asyncConfiguredEnabled() {
    return backpressure.configuredEnabled();
  }

  public boolean asyncRuntimeEnabled() {
    return backpressure.runtimeEnabled();
  }

  public record AsyncSnapshot(
    boolean configuredEnabled,
    boolean runtimeEnabled,
    boolean enabled,
    int maxInFlight,
    int maxQueue,
    boolean callerRunsOffMainThread,
    int inFlight,
    int queued,
    long submitted,
    long executed,
    long queuedTasks,
    long rejected,
    long callerRuns,
    long drainRuns,
    long lastQueueAtMs,
    long lastRejectAtMs,
    long lastCallerRunAtMs
  ) {}
}
