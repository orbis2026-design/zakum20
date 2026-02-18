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
  private final Map<String, String> aliases;

  public MenuRepository(Plugin plugin) {
    this.plugin = plugin;
    this.menus = new ConcurrentHashMap<>();
    this.aliases = new ConcurrentHashMap<>();
  }

  public void reload() {
    Map<String, MenuDef> loaded = new LinkedHashMap<>();
    Map<String, String> aliasLoaded = new LinkedHashMap<>();
    loadFromFolder(folder("menus.system-path", "SystemMenus"), loaded, aliasLoaded);
    loadFromFolder(folder("menus.custom-path", "CustomGuis"), loaded, aliasLoaded);
    menus.clear();
    menus.putAll(loaded);
    aliases.clear();
    aliases.putAll(aliasLoaded);
  }

  public Set<String> ids() {
    return Collections.unmodifiableSet(menus.keySet());
  }

  public MenuDef get(String id) {
    if (id == null || id.isBlank()) return null;
    String key = id.trim().toLowerCase(Locale.ROOT);
    String resolved = aliases.getOrDefault(key, key);
    return menus.get(resolved);
  }

  private File folder(String pathKey, String fallback) {
    String configured = plugin.getConfig().getString(pathKey, fallback);
    return new File(plugin.getDataFolder(), configured == null ? fallback : configured);
  }

  private void loadFromFolder(File folder, Map<String, MenuDef> out, Map<String, String> aliasesOut) {
    if (folder == null || !folder.exists() || !folder.isDirectory()) return;
    File[] files = folder.listFiles((dir, name) -> name != null && name.toLowerCase(Locale.ROOT).endsWith(".yml"));
    if (files == null) return;

    for (File file : files) {
      YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
      MenuDef def = parse(yaml);
      if (def == null) continue;
      out.put(def.id(), def);
      if (def.commandAlias() != null && !def.commandAlias().isBlank()) {
        aliasesOut.put(def.commandAlias().trim().toLowerCase(Locale.ROOT), def.id());
      }
    }
  }

  private static MenuDef parse(YamlConfiguration yaml) {
    if (yaml == null) return null;
    String codeId = yaml.getString("codeid", "").trim().toLowerCase(Locale.ROOT);
    if (codeId.isBlank()) return null;

    int rows = clampRows(yaml.getInt("rows", 3));
    String title = yaml.getString("title", codeId);
    String commandAlias = yaml.getString("commandAlias", null);
    if (commandAlias != null && commandAlias.isBlank()) commandAlias = null;

    Map<Integer, MenuDef.MenuSceneDef> scenes = new LinkedHashMap<>();
    ConfigurationSection scenesSection = yaml.getConfigurationSection("scenes");
    if (scenesSection != null) {
      for (String key : scenesSection.getKeys(false)) {
        int sceneIndex;
        try {
          sceneIndex = Integer.parseInt(key);
        } catch (NumberFormatException ex) {
          continue;
        }
        ConfigurationSection sceneSection = scenesSection.getConfigurationSection(key);
        if (sceneSection == null) continue;
        int delay = Math.max(0, sceneSection.getInt("delay", 0));
        ConfigurationSection sceneItems = sceneSection.getConfigurationSection("items");
        Map<Integer, MenuDef.MenuItemDef> items = parseItems(sceneItems, rows);
        applyEmptyFill(yaml, rows, items);
        scenes.put(sceneIndex, new MenuDef.MenuSceneDef(delay, Map.copyOf(items)));
      }
    }

    if (scenes.isEmpty()) {
      scenes.put(0, new MenuDef.MenuSceneDef(0, Map.of()));
    }

    return new MenuDef(codeId, rows, title, commandAlias, Map.copyOf(scenes));
  }

  private static Map<Integer, MenuDef.MenuItemDef> parseItems(ConfigurationSection scene, int rows) {
    Map<Integer, MenuDef.MenuItemDef> items = new LinkedHashMap<>();
    if (scene == null) return items;
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
      Map<String, String> openContext = Map.of();
      String message = null;
      boolean close = false;
      MenuDef.CommandAction commandAction = null;
      MenuDef.AceAction aceAction = null;

      if (clicks != null) {
        ConfigurationSection openGui = clicks.getConfigurationSection("open-gui");
        if (openGui != null) {
          openGuiId = openGui.getString("id", null);
          openContext = parseContext(openGui.getConfigurationSection("context"));
        }

        ConfigurationSection openCtx = clicks.getConfigurationSection("open-context");
        if (openCtx != null) {
          openGuiId = openCtx.getString("id", openGuiId);
          Map<String, String> ctx = parseContext(openCtx.getConfigurationSection("context"));
          if (!ctx.isEmpty()) {
            openContext = ctx;
          }
        }

        message = clicks.getString("message.message", null);
        close = clicks.isConfigurationSection("close-inventory");

        ConfigurationSection command = clicks.getConfigurationSection("command");
        if (command != null) {
          String cmd = command.getString("command", command.getString("cmd", ""));
          if (cmd != null && !cmd.isBlank()) {
            String as = command.getString("as", "player");
            boolean asConsole = "console".equalsIgnoreCase(as);
            commandAction = new MenuDef.CommandAction(cmd, asConsole);
          }
        }

        ConfigurationSection ace = clicks.getConfigurationSection("ace-trigger");
        if (ace != null) {
          List<String> script = ace.getStringList("script");
          if (script == null || script.isEmpty()) {
            String single = ace.getString("script", ace.getString("line", null));
            if (single != null && !single.isBlank()) {
              script = List.of(single);
            } else {
              script = ace.getStringList("lines");
            }
          }
          if (script != null && !script.isEmpty()) {
            Map<String, String> meta = parseContext(ace.getConfigurationSection("metadata"));
            aceAction = new MenuDef.AceAction(List.copyOf(script), meta);
          }
        }
      }

      items.put(slot, new MenuDef.MenuItemDef(
        slot,
        material,
        amount,
        name,
        new ArrayList<>(lore),
        flags,
        openGuiId,
        openContext,
        message,
        close,
        commandAction,
        aceAction
      ));
    }
    return items;
  }

  private static int clampRows(int rows) {
    return Math.max(1, Math.min(6, rows));
  }

  private static Material parseMaterial(String raw) {
    if (raw == null || raw.isBlank()) return Material.STONE;
    
    // Try matchMaterial first (handles many formats)
    Material mat = Material.matchMaterial(raw.trim());
    if (mat != null) return mat;
    
    // Fallback to valueOf with normalization
    try {
      return Material.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('.', '_'));
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
      items.put(slot, new MenuDef.MenuItemDef(
        slot,
        material,
        amount,
        name,
        new ArrayList<>(lore),
        flags,
        null,
        Map.of(),
        null,
        false,
        null,
        null
      ));
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

  private static Map<String, String> parseContext(ConfigurationSection section) {
    if (section == null) return Map.of();
    Map<String, String> out = new LinkedHashMap<>();
    for (String key : section.getKeys(false)) {
      if (key == null || key.isBlank()) continue;
      Object value = section.get(key);
      if (value == null) continue;
      out.put(key, String.valueOf(value));
    }
    return Map.copyOf(out);
  }
}
