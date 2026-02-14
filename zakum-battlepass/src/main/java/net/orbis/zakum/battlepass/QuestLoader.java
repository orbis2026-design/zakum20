package net.orbis.zakum.battlepass;

import net.orbis.zakum.api.util.AtomicFiles;
import net.orbis.zakum.api.util.FileBackups;
import net.orbis.zakum.battlepass.model.QuestCadence;
import net.orbis.zakum.battlepass.model.QuestDef;
import net.orbis.zakum.battlepass.model.QuestStep;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Loads quests.yml.
 *
 * Source of truth is the plugin data folder (operators can edit).
 * If missing, we copy defaults from the jar.
 */
public final class QuestLoader {

  private QuestLoader() {}

  private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    .withZone(ZoneOffset.UTC);

  public static Map<String, QuestDef> load(Plugin plugin) {
    ensureDefault(plugin);

    File file = new File(plugin.getDataFolder(), "quests.yml");

    // YAML safety: attempt strict parse + restore last known good if this file is broken.
    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
    Map<String, QuestDef> parsed = parseYaml(yaml);

    if (yamlSafetyEnabled(plugin) && parsed.isEmpty() && file.exists() && file.length() > 0) {
      boolean looksBroken = false;
      try {
        YamlConfiguration strict = new YamlConfiguration();
        strict.load(file);
        // If the file has content but no loadable keys or no quests section, treat as potentially broken.
        looksBroken = !strict.getKeys(true).isEmpty();
      } catch (InvalidConfigurationException ex) {
        looksBroken = true;
      } catch (Exception ignored) {
        looksBroken = true;
      }

      boolean restored = looksBroken && restoreLatestValid(plugin, file.toPath());
      if (restored) {
        yaml = YamlConfiguration.loadConfiguration(file);
        parsed = parseYaml(yaml);
      }
    }

    if (yamlSafetyEnabled(plugin) && autoBackupOnLoad(plugin) && !parsed.isEmpty()) {
      try {
        int keep = Math.max(0, plugin.getConfig().getInt("battlepass.yamlSafety.keep", 10));
        Path backupDir = plugin.getDataFolder().toPath().resolve("backups");
        FileBackups.backup(file.toPath(), backupDir, "quests", keep);
      } catch (Exception ignored) {}
    }

    return parsed;
  }

  /** Legacy: kept for compatibility. */
  public static Map<String, QuestDef> loadFromJar(Plugin plugin) {
    try (var in = plugin.getResource("quests.yml")) {
      if (in == null) throw new IllegalStateException("quests.yml missing");

      var yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
      ConfigurationSection root = yaml.getConfigurationSection("quests");
      if (root == null) return Map.of();

      return parseQuests(root);
    } catch (Exception e) {
      plugin.getLogger().warning("Failed to load quests from jar: " + e.getMessage());
      return Map.of();
    }
  }

  /** Parses quests from a YAML file that is already loaded. */
  public static Map<String, QuestDef> parseYaml(YamlConfiguration yaml) {
    if (yaml == null) return Map.of();
    ConfigurationSection root = yaml.getConfigurationSection("quests");
    if (root == null) return Map.of();
    return parseQuests(root);
  }

  /** Quick validity check used by editors / restore tooling. */
public static boolean isValidYaml(YamlConfiguration yaml) {
  if (yaml == null) return false;
  if (!parseYaml(yaml).isEmpty()) return true;

  // If parse produced no quests, only accept as "valid" when every quest is explicitly disabled.
  ConfigurationSection root = yaml.getConfigurationSection("quests");
  if (root == null) return false;
  if (root.getKeys(false).isEmpty()) return false;

  for (String questId : root.getKeys(false)) {
    ConfigurationSection q = root.getConfigurationSection(questId);
    if (q == null) continue;
    if (q.getBoolean("enabled", true)) return false;
  }
  return true;
}

  private static Map<String, QuestDef> parseQuests(ConfigurationSection root) {
    Map<String, QuestDef> out = new LinkedHashMap<>();

    for (String questId : root.getKeys(false)) {
      ConfigurationSection q = root.getConfigurationSection(questId);
      if (q == null) continue;

      String name = q.getString("name", questId);
      long points = q.getLong("points", 0);

      boolean enabled = q.getBoolean("enabled", true);
      if (!enabled) continue;

      boolean premiumOnly = q.getBoolean("premiumOnly", false);
      long premiumBonus = q.getLong("premiumBonusPoints", 0);

      QuestCadence cadence;
      String cadenceRaw = q.getString("cadence", "SEASON");
      try { cadence = QuestCadence.valueOf(cadenceRaw.trim().toUpperCase(Locale.ROOT)); }
      catch (IllegalArgumentException ex) { cadence = QuestCadence.SEASON; }

      List<Integer> weeks = q.getIntegerList("availableWeeks");
      List<Integer> availableWeeks = weeks == null || weeks.isEmpty() ? List.of() : List.copyOf(new ArrayList<>(new java.util.LinkedHashSet<>(weeks)));

      List<Map<?, ?>> stepsList = q.getMapList("steps");
      List<QuestStep> steps = new ArrayList<>();

      for (Map<?, ?> stepObj : stepsList) {
        String type = Objects.toString(stepObj.getOrDefault("type", ""), "").trim();
        String key = Objects.toString(stepObj.getOrDefault("key", ""), "").trim();
        String value = Objects.toString(stepObj.getOrDefault("value", ""), "").trim();
        long required = parseLong(stepObj.get("required"), 1);

        if (type.isBlank()) continue;
        if (required < 1) required = 1;

        steps.add(new QuestStep(type, key, value, required));
      }

      if (steps.isEmpty()) continue;

      out.put(questId, new QuestDef(
        questId,
        name,
        points,
        premiumOnly,
        premiumBonus,
        cadence,
        availableWeeks,
        List.copyOf(steps)
      ));
    }

    return Map.copyOf(out);
  }

  private static boolean yamlSafetyEnabled(Plugin plugin) {
    try { return plugin.getConfig().getBoolean("battlepass.yamlSafety.enabled", true); }
    catch (Throwable t) { return true; }
  }

  private static boolean autoBackupOnLoad(Plugin plugin) {
    try { return plugin.getConfig().getBoolean("battlepass.yamlSafety.autoBackupOnLoad", true); }
    catch (Throwable t) { return true; }
  }

  /**
   * Restores quests.yml from the newest valid backup if available.
   *
   * Failsafe posture:
   * - never deletes data
   * - moves broken file aside
   * - restores using atomic replace
   */
  private static boolean restoreLatestValid(Plugin plugin, Path questsFile) {
    try {
      Path backupDir = plugin.getDataFolder().toPath().resolve("backups");
      if (!Files.isDirectory(backupDir)) return false;

      List<Path> backups = new ArrayList<>();
      try (var s = Files.list(backupDir)) {
        s.filter(p -> {
          String n = p.getFileName().toString();
          return n.startsWith("quests-") && n.endsWith(".bak");
        }).forEach(backups::add);
      }
      backups.sort(Comparator.comparingLong(QuestLoader::lastModifiedSafe).reversed());

      for (Path b : backups) {
        YamlConfiguration y = new YamlConfiguration();
        try {
          y.load(b.toFile());
        } catch (Exception ignored) {
          continue;
        }

        if (!isValidYaml(y)) continue;

        // Move broken file aside.
        try {
          String ts = TS.format(Instant.now());
          Path broken = questsFile.resolveSibling("quests.yml.broken-" + ts);
          Files.move(questsFile, broken, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {}

        // Restore backup to quests.yml (atomic when possible).
        Path tmp = questsFile.resolveSibling("quests.yml.tmp");
        Files.copy(b, tmp, StandardCopyOption.REPLACE_EXISTING);
        AtomicFiles.moveReplace(tmp, questsFile);

        plugin.getLogger().warning("quests.yml was invalid; restored from backup: " + b.getFileName());
        return true;
      }
    } catch (Exception ignored) {}
    return false;
  }

  private static long lastModifiedSafe(Path p) {
    try { return Files.getLastModifiedTime(p).toMillis(); }
    catch (Exception e) { return 0L; }
  }

  private static void ensureDefault(Plugin plugin) {
    if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
    File f = new File(plugin.getDataFolder(), "quests.yml");
    if (f.exists()) return;

    try {
      plugin.saveResource("quests.yml", false);
    } catch (Throwable t) {
      // fallback: create minimal file
      YamlConfiguration y = new YamlConfiguration();
      y.set("quests.example.name", "Break 10 stone");
      y.set("quests.example.points", 100);
      y.set("quests.example.steps", List.of(Map.of("type", "BLOCK_BREAK", "key", "material", "value", "STONE", "required", 10)));
      try { y.save(f); } catch (Exception ignored) {}
    }
  }

  private static long parseLong(Object v, long def) {
    if (v == null) return def;
    if (v instanceof Number n) return n.longValue();
    try { return Long.parseLong(String.valueOf(v)); }
    catch (NumberFormatException ignored) { return def; }
  }
}
