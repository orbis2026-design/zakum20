package net.orbis.orbisloot.config;

import java.util.List;
import java.util.Locale;

public record LootRewardConfig(
  String id,
  double weight,
  List<String> actions
) {

  public LootRewardConfig {
    id = normalize(id);
    weight = Math.max(0.000001D, weight);
    actions = actions == null ? List.of() : List.copyOf(actions);
  }

  private static String normalize(String value) {
    if (value == null || value.isBlank()) return "reward";
    return value.trim().toLowerCase(Locale.ROOT);
  }
}
