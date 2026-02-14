package net.orbis.zakum.api.ui;

import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Bridge for future GUI runtime integration.
 */
public interface GuiBridge {

  void openLayout(Player player, String layoutId, Map<String, String> placeholders);
}
