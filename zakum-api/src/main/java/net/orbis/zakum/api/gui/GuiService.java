package net.orbis.zakum.api.gui;

import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Optional GUI runtime (InfiniteGUI-style).
 *
 * Resolve via Bukkit ServicesManager:
 *   GuiService gui = Bukkit.getServicesManager().load(GuiService.class);
 */
public interface GuiService {

  /**
   * @return true if the GUI runtime is active and can open menus.
   */
  boolean available();

  /**
   * Open a GUI by id (usually YAML-backed).
   *
   * @return true if opened, false if id not found or runtime unavailable.
   */
  boolean open(Player player, String guiId);

  /**
   * Open a GUI with extra contextual variables.
   *
   * Context is small string map (no large objects) to avoid leaks.
   */
  boolean open(Player player, String guiId, Map<String, String> context);

  /**
   * Close any GUI opened by this runtime.
   */
  void close(Player player);
}
