package net.orbis.orbishud.service;

public record HudStatus(
  boolean running,
  boolean enabled,
  int updateIntervalTicks,
  int profiles,
  String defaultProfile,
  int trackedPlayers,
  int onlinePlayers,
  int taskId
) {}