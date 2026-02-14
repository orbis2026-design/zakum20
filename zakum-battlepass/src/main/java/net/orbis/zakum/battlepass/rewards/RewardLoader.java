package net.orbis.zakum.battlepass.rewards;

import net.orbis.zakum.api.util.AtomicFiles;
import net.orbis.zakum.api.util.FileBackups;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Loads rewards.yml from the plugin data folder.
 *
 * If missing, it is copied from jar defaults.
 */
public final class RewardLoader {

  private RewardLoader() {}

  private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    .withZone(ZoneOffset.UTC);

  public static RewardsTable load(Plugin plugin) {
    ensureDefault(plugin);

    File file = new File(plugin.getDataFolder(), "rewards.yml");
    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
    RewardsTable parsed = parseYaml(yaml);

    // YAML safety: attempt strict parse + restore last known good if this file is broken.
    if (yamlSafetyEnabled(plugin) && parsed.all().isEmpty() && file.exists() && file.length() > 0) {
      boolean looksBroken = false;
      try {
        YamlConfiguration strict = new YamlConfiguration();
        strict.load(file);
        // If the file has content but no loadable keys or no tiers section, treat as potentially broken.
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

    if (yamlSafetyEnabled(plugin) && autoBackupOnLoad(plugin) && !parsed.all().isEmpty()) {
      try {
        int keep = Math.max(0, plugin.getConfig().getInt("battlepass.yamlSafety.keep", 10));
        Path backupDir = plugin.getDataFolder().toPath().resolve("backups");
        FileBackups.backup(file.toPath(), backupDir, "rewards", keep);
      } catch (Exception ignored) {}
    }

    return parsed;
  }

  /** Parses rewards from a YAML file that is already loaded. */
  public static RewardsTable parseYaml(YamlConfiguration yaml) {
    if (yaml == null) return new RewardsTable(List.of());

    ConfigurationSection tiersSec = yaml.getConfigurationSection("tiers");
    if (tiersSec == null) return new RewardsTable(List.of());

    List<TierRewards> tiers = new ArrayList<>();

    for (String key : tiersSec.getKeys(false)) {
      int tier;
      try { tier = Integer.parseInt(key); }
      catch (NumberFormatException ignored) { continue; }

      ConfigurationSection t = tiersSec.getConfigurationSection(key);
      if (t == null) continue;

      long required = t.getLong("pointsRequired", Math.max(0, tier) * 100L);

      List<RewardDef> free = loadRewardsList(t.getConfigurationSection("free"));
      List<RewardDef> premium = loadRewardsList(t.getConfigurationSection("premium"));

      tiers.add(new TierRewards(tier, required, free, premium));
    }

    return new RewardsTable(tiers);
  }

  /** Quick validity check used by editors / restore tooling. */
  public static boolean isValidYaml(YamlConfiguration yaml) {
    return !parseYaml(yaml).all().isEmpty();
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
   * Restores rewards.yml from the newest valid backup if available.
   *
   * Failsafe posture:
   * - never deletes data
   * - moves broken file aside
   * - restores using atomic replace
   */
  private static boolean restoreLatestValid(Plugin plugin, Path rewardsFile) {
    try {
      Path backupDir = plugin.getDataFolder().toPath().resolve("backups");
      if (!Files.isDirectory(backupDir)) return false;

      List<Path> backups = new ArrayList<>();
      try (var s = Files.list(backupDir)) {
        s.filter(p -> {
          String n = p.getFileName().toString();
          return n.startsWith("rewards-") && n.endsWith(".bak");
        }).forEach(backups::add);
      }
      backups.sort(Comparator.comparingLong(RewardLoader::lastModifiedSafe).reversed());

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
          Path broken = rewardsFile.resolveSibling("rewards.yml.broken-" + ts);
          Files.move(rewardsFile, broken, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {}

        // Restore backup to rewards.yml (atomic when possible).
        Path tmp = rewardsFile.resolveSibling("rewards.yml.tmp");
        Files.copy(b, tmp, StandardCopyOption.REPLACE_EXISTING);
        AtomicFiles.moveReplace(tmp, rewardsFile);

        plugin.getLogger().warning("rewards.yml was invalid; restored from backup: " + b.getFileName());
        return true;
      }
    } catch (Exception ignored) {}
    return false;
  }

  private static long lastModifiedSafe(Path p) {
    try { return Files.getLastModifiedTime(p).toMillis(); }
    catch (Exception e) { return 0L; }
  }

  private static List<RewardDef> loadRewardsList(ConfigurationSection sec) {
    if (sec == null) return List.of();

    List<RewardDef> out = new ArrayList<>();
    for (String id : sec.getKeys(false)) {
      ConfigurationSection r = sec.getConfigurationSection(id);
      if (r == null) continue;

      String typeRaw = r.getString("type", "COMMAND").trim().toUpperCase(Locale.ROOT);
      RewardType type;
      try { type = RewardType.valueOf(typeRaw); }
      catch (IllegalArgumentException ex) { type = RewardType.COMMAND; }

      if (type == RewardType.COMMAND) {
        List<String> cmds = r.getStringList("commands");
        out.add(new RewardDef(type, List.copyOf(cmds)));
      }
    }
    return List.copyOf(out);
  }

  private static void ensureDefault(Plugin plugin) {
    if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
    File f = new File(plugin.getDataFolder(), "rewards.yml");
    if (f.exists()) return;

    try {
      plugin.saveResource("rewards.yml", false);
    } catch (Throwable t) {
      // If jar doesn't include defaults, create a minimal file.
      YamlConfiguration y = new YamlConfiguration();
      y.set("tiers.1.pointsRequired", 100);
      y.set("tiers.1.free.r1.type", "COMMAND");
      y.set("tiers.1.free.r1.commands", List.of("give %player% diamond 1"));
      try { y.save(f); } catch (Exception ignored) {}
    }
  }
}
