package net.orbis.orbishud.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.logging.Logger;

public record HudConfig(
  boolean enabled,
  int updateIntervalTicks,
  String defaultProfile,
  boolean hideWhenInSpectator,
  Map<String, HudProfile> profiles
) {
  private static final Pattern PROFILE_ID_PATTERN = Pattern.compile("[a-z0-9_-]{1,32}");

  public static HudConfig load(FileConfiguration config, Logger logger) {
    boolean enabled = config.getBoolean("hud.enabled", true);
    int configuredInterval = config.getInt("hud.updateIntervalTicks", 20);
    int interval = clamp(configuredInterval, 5, 200);
    if (interval != configuredInterval) {
      logger.warning("hud.updateIntervalTicks out of range, clamped to " + interval);
    }

    boolean hideWhenInSpectator = config.getBoolean("hud.hideWhenInSpectator", true);

    Map<String, HudProfile> loadedProfiles = new LinkedHashMap<>();
    ConfigurationSection profilesSection = config.getConfigurationSection("profiles");
    if (profilesSection != null) {
      for (String key : profilesSection.getKeys(false)) {
        String normalized = normalizeProfileId(key);
        if (!PROFILE_ID_PATTERN.matcher(normalized).matches()) {
          logger.warning("Profile id '" + key + "' is invalid. Allowed pattern: " + PROFILE_ID_PATTERN + ". Skipping.");
          continue;
        }
        if (loadedProfiles.containsKey(normalized)) {
          logger.warning("Duplicate normalized profile id '" + normalized + "'. Overwriting previous entry.");
        }
        String title = profilesSection.getString(key + ".title", "&b&lORBIS");
        List<String> lines = sanitizeLines(profilesSection.getStringList(key + ".lines"), logger, normalized);
        loadedProfiles.put(normalized, new HudProfile(normalized, title, lines));
      }
    }

    if (loadedProfiles.isEmpty()) {
      logger.warning("No valid HUD profiles found. Creating fallback default profile.");
      loadedProfiles.put("default", new HudProfile("default", "&b&lORBIS", fallbackLines()));
    }

    String configuredDefault = normalizeProfileId(config.getString("hud.defaultProfile", "default"));
    if (!loadedProfiles.containsKey(configuredDefault)) {
      String fallback = loadedProfiles.keySet().iterator().next();
      logger.warning("hud.defaultProfile='" + configuredDefault + "' missing. Falling back to '" + fallback + "'.");
      configuredDefault = fallback;
    }

    return new HudConfig(
      enabled,
      interval,
      configuredDefault,
      hideWhenInSpectator,
      Map.copyOf(loadedProfiles)
    );
  }

  public HudProfile profile(String id) {
    if (id == null || id.isBlank()) return profiles.get(defaultProfile);
    HudProfile direct = profiles.get(normalizeProfileId(id));
    return direct != null ? direct : profiles.get(defaultProfile);
  }

  public Set<String> profileIds() {
    return profiles.keySet();
  }

  private static List<String> sanitizeLines(List<String> source, Logger logger, String profileId) {
    List<String> out = new ArrayList<>();
    if (source != null) {
      for (String line : source) {
        if (line == null || line.isBlank()) continue;
        out.add(line);
      }
    }

    if (out.isEmpty()) {
      logger.warning("Profile '" + profileId + "' has no lines. Injecting fallback lines.");
      out.addAll(fallbackLines());
    }

    if (out.size() > 15) {
      logger.warning("Profile '" + profileId + "' has " + out.size() + " lines. Trimming to 15.");
      out = new ArrayList<>(out.subList(0, 15));
    }

    return List.copyOf(out);
  }

  private static List<String> fallbackLines() {
    return List.of(
      "&7Player: &f%player%",
      "&7Online: &a%online%",
      "&7World: &b%world%",
      "&7Server: &b%server_id%"
    );
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  private static String normalizeProfileId(String id) {
    if (id == null || id.isBlank()) return "default";
    return id.trim().toLowerCase(Locale.ROOT);
  }
}
