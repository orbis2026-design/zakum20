package net.orbis.orbisholograms.config;

import java.util.List;
import java.util.Locale;

public record HologramDefinition(
  String id,
  String world,
  double x,
  double y,
  double z,
  List<String> lines
) {

  public HologramDefinition {
    id = normalize(id);
    world = world == null || world.isBlank() ? "world" : world.trim();
    lines = lines == null ? List.of() : List.copyOf(lines);
  }

  private static String normalize(String id) {
    if (id == null || id.isBlank()) return "hologram";
    return id.trim().toLowerCase(Locale.ROOT);
  }
}
