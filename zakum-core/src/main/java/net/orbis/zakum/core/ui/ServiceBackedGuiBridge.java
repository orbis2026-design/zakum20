package net.orbis.zakum.core.ui;

import net.orbis.zakum.api.gui.GuiService;
import net.orbis.zakum.api.ui.GuiBridge;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Adapter from Zakum core's GuiBridge contract to the optional GuiService runtime.
 */
public final class ServiceBackedGuiBridge implements GuiBridge {

  private final GuiBridge fallback;

  public ServiceBackedGuiBridge(GuiBridge fallback) {
    this.fallback = fallback;
  }

  @Override
  public void openLayout(Player player, String layoutId, Map<String, String> placeholders) {
    if (player == null) return;
    String id = (layoutId == null || layoutId.isBlank()) ? "system.root" : layoutId;
    Map<String, String> context = placeholders == null ? Map.of() : placeholders;

    GuiService service = Bukkit.getServicesManager().load(GuiService.class);
    if (service != null && service.available()) {
      boolean opened = service.open(player, id, context);
      if (!opened) {
        player.sendMessage(ChatColor.RED + "Unknown GUI id: " + id);
      }
      return;
    }

    if (fallback != null) {
      fallback.openLayout(player, id, context);
    }
  }
}
