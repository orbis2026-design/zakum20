package net.orbis.zakum.bridge.placeholderapi;

import me.clip.placeholderapi.PlaceholderAPI;
import net.orbis.zakum.api.placeholders.PlaceholderService;
import org.bukkit.entity.Player;

final class PlaceholderApiServiceImpl implements PlaceholderService {

  @Override
  public String resolve(Player player, String expression) {
    if (player == null || expression == null) return "";
    try {
      return PlaceholderAPI.setPlaceholders(player, expression);
    } catch (Exception ignored) {
      return expression;
    }
  }
}
