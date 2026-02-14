package net.orbis.orbisgui.menu;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;

import java.util.List;
import java.util.Map;

public record MenuDef(
  String id,
  int rows,
  String title,
  String commandAlias,
  Map<Integer, MenuSceneDef> scenes
) {

  public record MenuSceneDef(
    int delayTicks,
    Map<Integer, MenuItemDef> items
  ) {}

  public record MenuItemDef(
    int slot,
    Material material,
    int amount,
    String name,
    List<String> lore,
    List<ItemFlag> flags,
    String openGuiId,
    Map<String, String> openContext,
    String message,
    boolean closeInventory,
    CommandAction commandAction,
    AceAction aceAction
  ) {}

  public record CommandAction(String command, boolean asConsole) {}

  public record AceAction(List<String> script, Map<String, String> metadata) {}
}
