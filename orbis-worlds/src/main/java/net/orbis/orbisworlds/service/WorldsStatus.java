package net.orbis.orbisworlds.service;

public record WorldsStatus(
  boolean running,
  boolean enabled,
  int updateIntervalTicks,
  boolean autoLoad,
  boolean safeTeleport,
  int maxParallelWorldLoads,
  int managedWorlds,
  int loadedManagedWorlds,
  int taskId
) {
}
