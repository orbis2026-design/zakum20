package net.orbis.zakum.api.placeholders;

import org.bukkit.entity.Player;

/**
 * Optional PlaceholderAPI bridge.
 *
 * Consumers should resolve this via ServicesManager:
 *   PlaceholderService ps = Bukkit.getServicesManager().load(PlaceholderService.class);
 *
 * Design rules:
 * - main-thread only (most placeholders assume sync context)
 * - do not cache results inside the service (placeholders are volatile)
 */
public interface PlaceholderService {

  /**
   * Resolves a PlaceholderAPI expression for this player.
   *
   * @param player player (online)
   * @param expression placeholder expression, ex. "%player_name%"
   * @return resolved string (never null). If not available: returns expression unchanged.
   */
  String resolve(Player player, String expression);

  /**
   * Resolves a placeholder and parses it as a double.
   */
  default double resolveNumber(Player player, String expression, double fallback) {
    try {
      String s = resolve(player, expression);
      if (s == null) return fallback;
      s = s.trim().replace(",", "");
      return Double.parseDouble(s);
    } catch (Exception ignored) {
      return fallback;
    }
  }
}
