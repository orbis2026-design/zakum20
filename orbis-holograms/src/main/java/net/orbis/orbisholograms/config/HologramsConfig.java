package net.orbis.orbisholograms.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.logging.Logger;

public record HologramsConfig(
  boolean enabled,
  int renderTickInterval,
  int viewDistance,
  int maxVisiblePerPlayer,
  boolean hideThroughWalls,
  Map<String, HologramDefinition> definitions
) {
  private static final Pattern DEFINITION_ID_PATTERN = Pattern.compile("[a-z0-9_-]{1,32}");

  public static HologramsConfig load(FileConfiguration config, Logger logger) {
    boolean enabled = config.getBoolean("holograms.enabled", true);
    int interval = clamp(config.getInt("holograms.renderTickInterval", 10), 1, 100);
    int viewDistance = clamp(config.getInt("holograms.viewDistance", 48), 8, 128);
    int maxVisiblePerPlayer = clamp(config.getInt("holograms.maxVisiblePerPlayer", 64), 1, 256);
    boolean hideThroughWalls = config.getBoolean("holograms.hideThroughWalls", false);

    Map<String, HologramDefinition> loaded = new LinkedHashMap<>();
    ConfigurationSection section = config.getConfigurationSection("definitions");
    if (section != null) {
      for (String rawId : section.getKeys(false)) {
        String id = normalize(rawId);
        if (!DEFINITION_ID_PATTERN.matcher(id).matches()) {
          logger.warning("Skipping invalid hologram id '" + rawId + "'.");
          continue;
        }

        String world = section.getString(rawId + ".world", "world");
        double x = section.getDouble(rawId + ".x", 0.0D);
        double y = section.getDouble(rawId + ".y", 64.0D);
        double z = section.getDouble(rawId + ".z", 0.0D);
        List<String> lines = sanitizeLines(section.getStringList(rawId + ".lines"), id, logger);
        loaded.put(id, new HologramDefinition(id, world, x, y, z, lines));
      }
    }

    if (loaded.isEmpty()) {
      logger.warning("No hologram definitions found. Injecting default entry.");
      loaded.put(
        "spawn",
        new HologramDefinition("spawn", "world", 0.5D, 80.0D, 0.5D, List.of("&b&lORBIS", "&7Welcome"))
      );
    }

    return new HologramsConfig(
      enabled,
      interval,
      viewDistance,
      maxVisiblePerPlayer,
      hideThroughWalls,
      Map.copyOf(loaded)
    );
  }

  public static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  private static List<String> sanitizeLines(List<String> source, String id, Logger logger) {
    List<String> out = new ArrayList<>();
    if (source != null) {
      for (String line : source) {
        if (line == null || line.isBlank()) continue;
        out.add(line);
      }
    }

    if (out.isEmpty()) {
      logger.warning("Hologram '" + id + "' has no lines. Injecting fallback line.");
      out.add("&7Undefined hologram");
    }

    if (out.size() > 20) {
      logger.warning("Hologram '" + id + "' has " + out.size() + " lines. Trimming to 20.");
      out = new ArrayList<>(out.subList(0, 20));
    }

    return List.copyOf(out);
  }

  private static String normalize(String id) {
    if (id == null || id.isBlank()) return "hologram";
    return id.trim().toLowerCase(Locale.ROOT);
  }
}
