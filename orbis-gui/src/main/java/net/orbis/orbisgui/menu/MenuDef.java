package net.orbis.orbisgui.menu;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;

import java.util.List;
import java.util.Map;

public record MenuDef(
  String id,
  int rows,
  String title,
  Map<Integer, MenuItemDef> items
) {

  public record MenuItemDef(
    int slot,
    Material material,
    int amount,
    String name,
    List<String> lore,
    List<ItemFlag> flags,
    String openGuiId,
    String message,
    boolean closeInventory
  ) {}
}
