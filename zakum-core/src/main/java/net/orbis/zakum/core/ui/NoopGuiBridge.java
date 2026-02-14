package net.orbis.zakum.core.ui;

import net.orbis.zakum.api.ui.GuiBridge;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Map;

public final class NoopGuiBridge implements GuiBridge {

  @Override
  public void openLayout(Player player, String layoutId, Map<String, String> placeholders) {
    if (player == null) return;
    String id = (layoutId == null || layoutId.isBlank()) ? "unknown" : layoutId;
    player.sendMessage(ChatColor.GRAY + "Layout '" + id + "' is not available yet.");
  }
}
