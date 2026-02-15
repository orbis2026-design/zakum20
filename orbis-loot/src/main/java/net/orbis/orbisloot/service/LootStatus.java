package net.orbis.orbisloot.service;

public record LootStatus(
  boolean running,
  boolean enabled,
  int cleanupIntervalTicks,
  int openCooldownSeconds,
  int maxRewardsPerOpen,
  int crates,
  int activeCooldownEntries,
  int taskId
) {
}
