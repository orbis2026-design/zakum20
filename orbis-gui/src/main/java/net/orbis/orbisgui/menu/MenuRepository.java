package net.orbis.orbisgui.menu;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class MenuRepository {

  private final Plugin plugin;
  private final Map<String, MenuDef> menus;

  public MenuRepository(Plugin plugin) {
    this.plugin = plugin;
    this.menus = new ConcurrentHashMap<>();
  }

  public void reload() {
    Map<String, MenuDef> loaded = new LinkedHashMap<>();
    loadFromFolder(folder("menus.system-path", "SystemMenus"), loaded);
    loadFromFolder(folder("menus.custom-path", "CustomGuis"), loaded);
    menus.clear();
    menus.putAll(loaded);
  }

  public Set<String> ids() {
    return Collections.unmodifiableSet(menus.keySet());
  }

  public MenuDef get(String id) {
    if (id == null || id.isBlank()) return null;
    return menus.get(id.trim().toLowerCase(Locale.ROOT));
  }

  private File folder(String pathKey, String fallback) {
    String configured = plugin.getConfig().getString(pathKey, fallback);
    return new File(plugin.getDataFolder(), configured == null ? fallback : configured);
  }

  private void loadFromFolder(File folder, Map<String, MenuDef> out) {
    if (folder == null || !folder.exists() || !folder.isDirectory()) return;
    File[] files = folder.listFiles((dir, name) -> name != null && name.toLowerCase(Locale.ROOT).endsWith(".yml"));
    if (files == null) return;

    for (File file : files) {
      YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
      MenuDef def = parse(yaml);
      if (def == null) continue;
      out.put(def.id(), def);
    }
  }

  private static MenuDef parse(YamlConfiguration yaml) {
    if (yaml == null) return null;
    String codeId = yaml.getString("codeid", "").trim().toLowerCase(Locale.ROOT);
    if (codeId.isBlank()) return null;

    int rows = clampRows(yaml.getInt("rows", 3));
    String title = yaml.getString("title", codeId);

    ConfigurationSection scene = yaml.getConfigurationSection("scenes.0.items");
    Map<Integer, MenuDef.MenuItemDef> items = new LinkedHashMap<>();
    if (scene != null) {
      for (String key : scene.getKeys(false)) {
        ConfigurationSection item = scene.getConfigurationSection(key);
        if (item == null) continue;

        int slot = item.getInt("slot", -1);
        if (slot < 0 || slot >= rows * 9) continue;

        Material material = parseMaterial(item.getString("item", "STONE"));
        int amount = Math.max(1, Math.min(64, item.getInt("amount", 1)));
        String name = item.getString("item-name", "");
        List<String> lore = item.getStringList("item-lore");

        ConfigurationSection clicks = item.getConfigurationSection("click-events");
        String openGuiId = null;
        String message = null;
        boolean close = false;

        if (clicks != null) {
          openGuiId = clicks.getString("open-gui.id", null);
          message = clicks.getString("message.message", null);
          close = clicks.isConfigurationSection("close-inventory");
        }

        items.put(slot, new MenuDef.MenuItemDef(slot, material, amount, name, new ArrayList<>(lore), openGuiId, message, close));
      }
    }

    return new MenuDef(codeId, rows, title, Map.copyOf(items));
  }

  private static int clampRows(int rows) {
    return Math.max(1, Math.min(6, rows));
  }

  private static Material parseMaterial(String raw) {
    if (raw == null || raw.isBlank()) return Material.STONE;
    try {
      return Material.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return Material.STONE;
    }
  }
}
