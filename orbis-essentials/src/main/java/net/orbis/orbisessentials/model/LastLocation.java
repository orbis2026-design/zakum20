package net.orbis.orbisessentials.model;

import java.util.UUID;

public record LastLocation(
  UUID world,
  double x,
  double y,
  double z,
  float yaw,
  float pitch
) {}
