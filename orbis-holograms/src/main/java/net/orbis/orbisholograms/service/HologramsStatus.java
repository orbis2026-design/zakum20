package net.orbis.orbisholograms.service;

public record HologramsStatus(
  boolean running,
  boolean enabled,
  int renderTickInterval,
  int viewDistance,
  int maxVisiblePerPlayer,
  boolean hideThroughWalls,
  int definitions,
  int visibleAssignments,
  int taskId
) {
}
