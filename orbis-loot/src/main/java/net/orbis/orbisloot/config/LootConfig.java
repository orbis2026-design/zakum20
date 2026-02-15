package net.orbis.orbisloot.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.logging.Logger;

public record LootConfig(
  boolean enabled,
  int cleanupIntervalTicks,
  int openCooldownSeconds,
  int maxRewardsPerOpen,
  Map<String, LootCrateConfig> crates
) {
  private static final Pattern CRATE_ID_PATTERN = Pattern.compile("[a-z0-9_-]{1,32}");
  private static final Pattern REWARD_ID_PATTERN = Pattern.compile("[a-z0-9_-]{1,32}");

  public static LootConfig load(FileConfiguration config, Logger logger) {
    boolean enabled = config.getBoolean("loot.enabled", true);
    int cleanupIntervalTicks = clamp(config.getInt("loot.cleanupIntervalTicks", 100), 20, 1200);
    int openCooldownSeconds = clamp(config.getInt("loot.openCooldownSeconds", 1), 0, 300);
    int maxRewardsPerOpen = clamp(config.getInt("loot.maxRewardsPerOpen", 1), 1, 10);

    Map<String, LootCrateConfig> loadedCrates = new LinkedHashMap<>();
    ConfigurationSection cratesSection = config.getConfigurationSection("crates");
    if (cratesSection != null) {
      for (String rawCrateId : cratesSection.getKeys(false)) {
        String crateId = normalize(rawCrateId);
        if (!CRATE_ID_PATTERN.matcher(crateId).matches()) {
          logger.warning("Skipping invalid crate id '" + rawCrateId + "'.");
          continue;
        }

        String displayName = cratesSection.getString(rawCrateId + ".displayName", crateId);
        String permission = cratesSection.getString(rawCrateId + ".permission", "");
        List<LootRewardConfig> rewards = loadRewards(cratesSection, rawCrateId, logger);
        if (rewards.isEmpty()) {
          logger.warning("Skipping crate '" + crateId + "' because it has no valid rewards.");
          continue;
        }

        LootCrateConfig crate = new LootCrateConfig(crateId, displayName, permission, rewards);
        if (crate.totalWeight() <= 0.0D) {
          logger.warning("Skipping crate '" + crateId + "' because total reward weight is zero.");
          continue;
        }
        loadedCrates.put(crateId, crate);
      }
    }

    if (loadedCrates.isEmpty()) {
      logger.warning("No crates configured. Injecting fallback crate.");
      loadedCrates.put(
        "starter",
        new LootCrateConfig(
          "starter",
          "&bStarter Crate",
          "",
          List.of(
            new LootRewardConfig("coins_small", 70.0D, List.of("[MESSAGE] &a+100 coins")),
            new LootRewardConfig("coins_large", 30.0D, List.of("[MESSAGE] &a+500 coins"))
          )
        )
      );
    }

    return new LootConfig(
      enabled,
      cleanupIntervalTicks,
      openCooldownSeconds,
      maxRewardsPerOpen,
      Map.copyOf(loadedCrates)
    );
  }

  public LootCrateConfig crate(String id) {
    if (id == null || id.isBlank()) return null;
    return crates.get(normalize(id));
  }

  public static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  private static List<LootRewardConfig> loadRewards(
    ConfigurationSection cratesSection,
    String rawCrateId,
    Logger logger
  ) {
    List<LootRewardConfig> rewards = new ArrayList<>();
    ConfigurationSection rewardsSection = cratesSection.getConfigurationSection(rawCrateId + ".rewards");
    if (rewardsSection == null) return rewards;

    for (String rawRewardId : rewardsSection.getKeys(false)) {
      String rewardId = normalize(rawRewardId);
      if (!REWARD_ID_PATTERN.matcher(rewardId).matches()) {
        logger.warning("Skipping invalid reward id '" + rawRewardId + "' in crate '" + rawCrateId + "'.");
        continue;
      }

      double weight = rewardsSection.getDouble(rawRewardId + ".weight", 0.0D);
      if (weight <= 0.0D) {
        logger.warning("Skipping reward '" + rewardId + "' in crate '" + rawCrateId + "' due zero/negative weight.");
        continue;
      }

      List<String> actions = sanitizeActions(rewardsSection.getStringList(rawRewardId + ".actions"));
      if (actions.isEmpty()) {
        logger.warning("Skipping reward '" + rewardId + "' in crate '" + rawCrateId + "' with empty actions.");
        continue;
      }

      rewards.add(new LootRewardConfig(rewardId, weight, actions));
    }
    return rewards;
  }

  private static List<String> sanitizeActions(List<String> source) {
    List<String> out = new ArrayList<>();
    if (source != null) {
      for (String action : source) {
        if (action == null || action.isBlank()) continue;
        out.add(action);
      }
    }
    return List.copyOf(out);
  }

  private static String normalize(String value) {
    if (value == null || value.isBlank()) return "crate";
    return value.trim().toLowerCase(Locale.ROOT);
  }
}
