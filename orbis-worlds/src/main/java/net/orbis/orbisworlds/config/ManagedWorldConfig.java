package net.orbis.orbisworlds.config;

import java.util.Locale;

public record ManagedWorldConfig(
  String id,
  String worldName,
  boolean autoLoad
) {

  public ManagedWorldConfig {
    id = normalize(id);
    worldName = worldName == null || worldName.isBlank() ? id : worldName.trim();
  }

  private static String normalize(String value) {
    if (value == null || value.isBlank()) return "world";
    return value.trim().toLowerCase(Locale.ROOT);
  }
}
