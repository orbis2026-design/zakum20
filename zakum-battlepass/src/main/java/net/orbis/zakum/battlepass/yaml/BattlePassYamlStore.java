package net.orbis.zakum.battlepass.yaml;

import net.orbis.zakum.api.util.AtomicFiles;
import net.orbis.zakum.api.util.FileBackups;
import net.orbis.zakum.battlepass.QuestLoader;
import net.orbis.zakum.battlepass.rewards.RewardLoader;
import net.orbis.zakum.battlepass.rewards.RewardsTable;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
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
 * Small, bounded YAML safety layer for BattlePass editor operations.
 *
 * Goals:
 * - atomic writes (no partial config corruption)
 * - rotating backups (bounded disk usage)
 * - strict validation hooks (detect syntax errors)
 * - safe restore of last-known-good backups
 */
public final class BattlePassYamlStore {

  private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    .withZone(ZoneOffset.UTC);

  private final Plugin plugin;
  private final Path dataDir;
  private final Path backupsDir;
  private final int keep;
  private final boolean enabled;

  public BattlePassYamlStore(Plugin plugin) {
    this.plugin = plugin;
    this.dataDir = plugin.getDataFolder().toPath();
    this.backupsDir = dataDir.resolve("backups");
    this.enabled = plugin.getConfig().getBoolean("battlepass.yamlSafety.enabled", true);
    this.keep = Math.max(0, plugin.getConfig().getInt("battlepass.yamlSafety.keep", 10));
  }

  public Path questsFile() { return dataDir.resolve("quests.yml"); }
  public Path rewardsFile() { return dataDir.resolve("rewards.yml"); }

  public record Validation(boolean ok, String message) {}

  public Validation validateQuests() {
    return validate("quests", questsFile(), QuestLoader::isValidYaml);
  }

  public Validation validateRewards() {
    return validate("rewards", rewardsFile(), RewardLoader::isValidYaml);
  }

  private Validation validate(String kind, Path file, java.util.function.Predicate<YamlConfiguration> ok) {
    if (file == null) return new Validation(false, kind + ": file missing");
    if (!Files.exists(file)) return new Validation(false, kind + ": file missing");

    YamlConfiguration yaml = new YamlConfiguration();
    try {
      yaml.load(file.toFile()); // strict parse (throws on syntax issues)
    } catch (InvalidConfigurationException ex) {
      String msg = ex.getMessage();
      msg = (msg == null) ? "invalid YAML" : msg;
      return new Validation(false, kind + ": syntax error: " + msg);
    } catch (IOException ex) {
      return new Validation(false, kind + ": IO error: " + ex.getMessage());
    }

    try {
      if (!ok.test(yaml)) return new Validation(false, kind + ": no valid entries (check structure)");
    } catch (Throwable t) {
      return new Validation(false, kind + ": parse error: " + t.getClass().getSimpleName());
    }

    return new Validation(true, kind + ": OK");
  }

  /** Creates a rotating backup now. Returns the backup filename (not full path). */
  public String backupNow(String kind) throws IOException {
    if (!enabled) throw new IOException("yamlSafety disabled");
    kind = normalizeKind(kind);

    Path src = kind.equals("quests") ? questsFile() : rewardsFile();
    Path out = FileBackups.backup(src, backupsDir, kind, keep);
    return out == null ? null : out.getFileName().toString();
  }

  /** Lists backups for a kind (newest first). */
  public List<String> listBackups(String kind, int limit) {
    kind = normalizeKind(kind);
    int lim = Math.max(1, Math.min(50, limit));

    if (!Files.isDirectory(backupsDir)) return List.of();

    List<Path> matches = new ArrayList<>();
    try (var s = Files.list(backupsDir)) {
      s.filter(p -> {
        String n = p.getFileName().toString();
        return n.startsWith(kind + "-") && n.endsWith(".bak");
      }).forEach(matches::add);
    } catch (IOException ignored) {
      return List.of();
    }

    matches.sort(Comparator.comparingLong(BattlePassYamlStore::lastModifiedSafe).reversed());
    if (matches.size() > lim) matches = matches.subList(0, lim);

    List<String> out = new ArrayList<>(matches.size());
    for (Path p : matches) out.add(p.getFileName().toString());
    return List.copyOf(out);
  }

  /**
   * Restores quests.yml or rewards.yml from a backup file in backups/.
   * Overwrites the target file, moves the current file aside as *.broken-TS.
   */
  public boolean restore(String kind, String backupName) throws IOException {
    if (!enabled) throw new IOException("yamlSafety disabled");
    kind = normalizeKind(kind);

    if (backupName == null || backupName.isBlank()) throw new IOException("backup missing");
    backupName = backupName.trim();

    Path target = kind.equals("quests") ? questsFile() : rewardsFile();
    Files.createDirectories(backupsDir);

    Path backup;
    if (backupName.equalsIgnoreCase("latest")) {
      List<String> list = listBackups(kind, 1);
      if (list.isEmpty()) return false;
      backup = backupsDir.resolve(list.get(0));
    } else {
      // Refuse path traversal, only allow filenames within backupsDir.
      String clean = Path.of(backupName).getFileName().toString();
      backup = backupsDir.resolve(clean);
    }

    String bn = backup.getFileName().toString();
    if (!bn.toLowerCase(Locale.ROOT).startsWith(kind + "-") || !bn.endsWith(".bak")) {
      throw new IOException("invalid backup name");
    }
    if (!Files.exists(backup)) return false;

    // Validate backup strictly before touching target.
    YamlConfiguration y = new YamlConfiguration();
    try {
      y.load(backup.toFile());
    } catch (InvalidConfigurationException ex) {
      throw new IOException("backup YAML is invalid: " + ex.getMessage());
    }

    boolean ok = kind.equals("quests") ? QuestLoader.isValidYaml(y) : RewardLoader.isValidYaml(y);
    if (!ok) throw new IOException("backup parses but contains no valid entries");

    // Move current file aside (best-effort).
    try {
      if (Files.exists(target)) {
        String ts = TS.format(Instant.now());
        Path broken = target.resolveSibling(target.getFileName().toString() + ".broken-" + ts);
        Files.move(target, broken, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (Exception ignored) {}

    // Restore atomically.
    Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");
    Files.copy(backup, tmp, StandardCopyOption.REPLACE_EXISTING);
    AtomicFiles.moveReplace(tmp, target);
    return true;
  }

  /**
   * Atomic save for quest editor.
   *
   * Contract: caller provides a fully-populated YAML document.
   */
  public void saveQuestsAtomic(YamlConfiguration yaml) throws IOException {
    if (!enabled) throw new IOException("yamlSafety disabled");
    if (yaml == null) throw new IOException("yaml missing");
    if (!QuestLoader.isValidYaml(yaml)) throw new IOException("quests: no valid quests (refusing to write)");

    Path target = questsFile();
    Files.createDirectories(target.getParent());

    FileBackups.backup(target, backupsDir, "quests", keep);

    Path tmp = target.resolveSibling("quests.yml.tmp");
    try {
      yaml.save(tmp.toFile());
    } catch (Exception ex) {
      throw new IOException("quests: failed saving temp YAML: " + ex.getMessage());
    }
    AtomicFiles.moveReplace(tmp, target);
  }

  /** Atomic save for reward editor. */
  public void saveRewardsAtomic(YamlConfiguration yaml) throws IOException {
    if (!enabled) throw new IOException("yamlSafety disabled");
    if (yaml == null) throw new IOException("yaml missing");

    RewardsTable t = RewardLoader.parseYaml(yaml);
    if (t.all().isEmpty()) throw new IOException("rewards: no tiers (refusing to write)");

    Path target = rewardsFile();
    Files.createDirectories(target.getParent());

    FileBackups.backup(target, backupsDir, "rewards", keep);

    Path tmp = target.resolveSibling("rewards.yml.tmp");
    try {
      yaml.save(tmp.toFile());
    } catch (Exception ex) {
      throw new IOException("rewards: failed saving temp YAML: " + ex.getMessage());
    }
    AtomicFiles.moveReplace(tmp, target);
  }

  private static String normalizeKind(String kind) {
    if (kind == null) return "quests";
    String k = kind.trim().toLowerCase(Locale.ROOT);
    if (k.startsWith("q")) return "quests";
    if (k.startsWith("r")) return "rewards";
    return "quests";
  }

  private static long lastModifiedSafe(Path p) {
    try { return Files.getLastModifiedTime(p).toMillis(); }
    catch (Exception e) { return 0L; }
  }
}
