package net.orbis.orbisloot.config;

import java.util.List;
import java.util.Locale;

public record LootCrateConfig(
  String id,
  String displayName,
  String permission,
  List<LootRewardConfig> rewards
) {

  public LootCrateConfig {
    id = normalize(id);
    displayName = displayName == null || displayName.isBlank() ? id : displayName;
    permission = permission == null || permission.isBlank() ? "" : permission.trim();
    rewards = rewards == null ? List.of() : List.copyOf(rewards);
  }

  public double totalWeight() {
    double total = 0.0D;
    for (LootRewardConfig reward : rewards) {
      total += Math.max(0.0D, reward.weight());
    }
    return total;
  }

  private static String normalize(String value) {
    if (value == null || value.isBlank()) return "crate";
    return value.trim().toLowerCase(Locale.ROOT);
  }
}
