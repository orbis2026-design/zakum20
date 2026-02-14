package net.orbis.orbisgui.menu;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
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
import java.util.LinkedHashSet;
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
        List<ItemFlag> flags = parseFlags(item.getStringList("item-flags"));

        ConfigurationSection clicks = item.getConfigurationSection("click-events");
        String openGuiId = null;
        String message = null;
        boolean close = false;

        if (clicks != null) {
          openGuiId = clicks.getString("open-gui.id", null);
          message = clicks.getString("message.message", null);
          close = clicks.isConfigurationSection("close-inventory");
        }

        items.put(slot, new MenuDef.MenuItemDef(slot, material, amount, name, new ArrayList<>(lore), flags, openGuiId, message, close));
      }
    }

    applyEmptyFill(yaml, rows, items);

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

  private static void applyEmptyFill(YamlConfiguration yaml, int rows, Map<Integer, MenuDef.MenuItemDef> items) {
    if (yaml == null) return;
    ConfigurationSection fill = yaml.getConfigurationSection("empty-fill");
    if (fill == null || !fill.getBoolean("enabled", false)) return;

    ConfigurationSection item = fill.getConfigurationSection("item");
    if (item == null) return;

    String type = item.getString("type", item.getString("item", "STONE"));
    Material material = parseMaterial(type);
    int amount = Math.max(1, Math.min(64, item.getInt("amount", 1)));
    String name = item.getString("name", item.getString("item-name", ""));
    List<String> lore = item.getStringList("lore");
    if (lore == null || lore.isEmpty()) {
      lore = item.getStringList("item-lore");
    }
    List<ItemFlag> flags = parseFlags(item.getStringList("item-flags"));

    Set<Integer> slots = resolveFillSlots(fill, rows);
    for (int slot : slots) {
      if (slot < 0 || slot >= rows * 9) continue;
      if (items.containsKey(slot)) continue;
      items.put(slot, new MenuDef.MenuItemDef(slot, material, amount, name, new ArrayList<>(lore), flags, null, null, false));
    }
  }

  private static Set<Integer> resolveFillSlots(ConfigurationSection fill, int rows) {
    Set<Integer> slots = new LinkedHashSet<>();
    boolean fillAll = false;
    List<String> rawSlots = fill.getStringList("slots");
    if (rawSlots != null) {
      for (String token : rawSlots) {
        if (token == null) continue;
        String trimmed = token.trim();
        if (trimmed.isBlank()) continue;
        if (trimmed.equalsIgnoreCase("all")) {
          fillAll = true;
          continue;
        }
        try {
          slots.add(Integer.parseInt(trimmed));
        } catch (NumberFormatException ignored) {
          // skip invalid token
        }
      }
    }

    List<Integer> numericSlots = fill.getIntegerList("slots");
    if (numericSlots != null && !numericSlots.isEmpty()) {
      slots.addAll(numericSlots);
    }

    if (fillAll || (slots.isEmpty() && (rawSlots == null || rawSlots.isEmpty()))) {
      int size = rows * 9;
      for (int i = 0; i < size; i++) {
        slots.add(i);
      }
    }
    return slots;
  }

  private static List<ItemFlag> parseFlags(List<String> raw) {
    if (raw == null || raw.isEmpty()) return List.of();
    boolean all = false;
    List<ItemFlag> flags = new ArrayList<>();
    for (String token : raw) {
      if (token == null || token.isBlank()) continue;
      String value = token.trim().toUpperCase(Locale.ROOT);
      if (value.equals("ALL")) {
        all = true;
        continue;
      }
      try {
        flags.add(ItemFlag.valueOf(value));
      } catch (IllegalArgumentException ignored) {
        // ignore unknown flag
      }
    }
    if (all) {
      return List.of(ItemFlag.values());
    }
    return List.copyOf(flags);
  }
}
