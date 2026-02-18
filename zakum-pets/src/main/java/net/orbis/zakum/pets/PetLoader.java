package net.orbis.zakum.pets;

import net.orbis.zakum.pets.model.FollowMode;
import net.orbis.zakum.pets.model.PetDef;
import net.orbis.zakum.pets.util.Colors;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PetLoader {

  private PetLoader() {}

  public static Map<String, PetDef> load(Plugin plugin) {
    ConfigurationSection root = plugin.getConfig().getConfigurationSection("pets");
    if (root == null) return Map.of();

    Map<String, PetDef> out = new LinkedHashMap<>();

    for (String id : root.getKeys(false)) {
      ConfigurationSection s = root.getConfigurationSection(id);
      if (s == null) continue;

      String name = Colors.color(s.getString("name", id));
      String entityStr = s.getString("entity", "WOLF").toUpperCase(Locale.ROOT);
      EntityType type;
      try {
        type = EntityType.valueOf(entityStr);
      } catch (IllegalArgumentException ex) {
        // Fallback to Registry for modern Paper API with NamespacedKey
        try {
          String key = entityStr.toLowerCase().replace("_", "");
          type = org.bukkit.Registry.ENTITY_TYPE.get(org.bukkit.NamespacedKey.minecraft(key));
          if (type == null) throw new IllegalArgumentException();
        } catch (Exception e) {
          plugin.getLogger().warning("Invalid entity type '" + entityStr + "' for pet '" + id + "', using WOLF");
          type = EntityType.WOLF;
        }
      }

      FollowMode mode;
      try {
        mode = FollowMode.valueOf(s.getString("followMode", "AI").toUpperCase(Locale.ROOT));
      } catch (Exception e) {
        mode = FollowMode.AI;
      }

      int xp = Math.max(0, s.getInt("xpPerMobKill", 10));
      List<String> summonScript = scriptList(s, "summonScript[]", "summonScript");
      List<String> dismissScript = scriptList(s, "dismissScript[]", "dismissScript");
      List<String> levelUpScript = scriptList(s, "levelUpScript[]", "levelUpScript");

      out.put(id, new PetDef(id, name, type, mode, xp, summonScript, dismissScript, levelUpScript));
    }

    return Map.copyOf(out);
  }

  private static List<String> scriptList(ConfigurationSection section, String arrayKey, String fallbackKey) {
    if (section == null) return List.of();
    List<String> out = section.getStringList(arrayKey);
    if (!out.isEmpty()) return List.copyOf(out);
    out = section.getStringList(fallbackKey);
    if (!out.isEmpty()) return List.copyOf(out);
    String single = section.getString(fallbackKey);
    if (single == null || single.isBlank()) return List.of();
    return List.of(single);
  }
}
