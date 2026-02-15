package net.orbis.orbisworlds.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.logging.Logger;

public record WorldsConfig(
  boolean enabled,
  int updateIntervalTicks,
  boolean autoLoad,
  boolean safeTeleport,
  int maxParallelWorldLoads,
  Map<String, ManagedWorldConfig> managedWorlds
) {
  private static final Pattern WORLD_ID_PATTERN = Pattern.compile("[a-z0-9_-]{1,32}");

  public static WorldsConfig load(FileConfiguration config, Logger logger) {
    boolean enabled = config.getBoolean("worlds.enabled", true);
    int updateIntervalTicks = clamp(config.getInt("worlds.updateIntervalTicks", 40), 10, 400);
    boolean autoLoad = config.getBoolean("worlds.autoLoad", true);
    boolean safeTeleport = config.getBoolean("worlds.safeTeleport", true);
    int maxParallelWorldLoads = clamp(config.getInt("worlds.maxParallelWorldLoads", 2), 1, 8);

    Map<String, ManagedWorldConfig> managed = new LinkedHashMap<>();
    ConfigurationSection managedSection = config.getConfigurationSection("managed");
    if (managedSection != null) {
      for (String rawId : managedSection.getKeys(false)) {
        String id = normalize(rawId);
        if (!WORLD_ID_PATTERN.matcher(id).matches()) {
          logger.warning("Skipping invalid managed world id '" + rawId + "'.");
          continue;
        }

        String worldName = managedSection.getString(rawId + ".name", id);
        boolean worldAutoLoad = managedSection.getBoolean(rawId + ".autoLoad", autoLoad);
        managed.put(id, new ManagedWorldConfig(id, worldName, worldAutoLoad));
      }
    }

    if (managed.isEmpty()) {
      logger.warning("No managed worlds configured. Injecting default world entry.");
      managed.put("world", new ManagedWorldConfig("world", "world", autoLoad));
    }

    return new WorldsConfig(
      enabled,
      updateIntervalTicks,
      autoLoad,
      safeTeleport,
      maxParallelWorldLoads,
      Map.copyOf(managed)
    );
  }

  public static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  private static String normalize(String value) {
    if (value == null || value.isBlank()) return "world";
    return value.trim().toLowerCase(Locale.ROOT);
  }
}
