package net.orbis.zakum.api.concurrent;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public interface ZakumScheduler {

  void runAsync(Runnable task);

  void runAtLocation(Location loc, Runnable task);

  void runAtEntity(Entity entity, Runnable task);

  Executor asyncExecutor();

  default <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
    return CompletableFuture.supplyAsync(supplier, asyncExecutor());
  }

  // Compatibility/transition helpers for legacy BukkitScheduler patterns.
  void runGlobal(Runnable task);

  int runTask(Plugin owner, Runnable task);

  int runTaskLater(Plugin owner, Runnable task, long delayTicks);

  int runTaskTimer(Plugin owner, Runnable task, long delayTicks, long periodTicks);

  int runTaskTimerAsynchronously(Plugin owner, Runnable task, long delayTicks, long periodTicks);

  int scheduleSyncDelayedTask(Plugin owner, Runnable task, long delayTicks);

  int scheduleSyncRepeatingTask(Plugin owner, Runnable task, long delayTicks, long periodTicks);

  void cancelTask(int taskId);
}
