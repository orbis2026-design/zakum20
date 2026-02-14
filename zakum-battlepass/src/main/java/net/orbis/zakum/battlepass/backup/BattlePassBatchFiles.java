package net.orbis.zakum.battlepass.backup;

import net.orbis.zakum.api.util.AtomicFiles;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Copies BattlePass YAML/config files into a per-backup batch folder.
 *
 * Goals:
 * - cheap, high-ROI safety net for "bad YAML" incidents
 * - bounded retention (keep-N)
 * - never blocks main thread when called from async completions
 */
public final class BattlePassBatchFiles {

  private BattlePassBatchFiles() {}

  public static void backupBatchFiles(JavaPlugin plugin, long batchId, int keep) {
    if (plugin == null) return;
    if (batchId <= 0) return;

    Path dataDir = plugin.getDataFolder().toPath();
    Path backupsDir = dataDir.resolve("backups");
    Path batchDir = backupsDir.resolve("batch-" + batchId);

    try {
      Files.createDirectories(batchDir);
      copyIfExists(dataDir.resolve("config.yml"), batchDir.resolve("config.yml"));
      copyIfExists(dataDir.resolve("quests.yml"), batchDir.resolve("quests.yml"));
      copyIfExists(dataDir.resolve("rewards.yml"), batchDir.resolve("rewards.yml"));
    } catch (IOException e) {
      plugin.getLogger().warning("Batch file backup failed: " + e.getMessage());
    }

    pruneOldBatches(plugin, keep);
  }

  public static boolean restoreBatchFiles(JavaPlugin plugin, long batchId) {
    if (plugin == null) return false;
    if (batchId <= 0) return false;

    Path dataDir = plugin.getDataFolder().toPath();
    Path batchDir = dataDir.resolve("backups").resolve("batch-" + batchId);
    if (!Files.isDirectory(batchDir)) return false;

    try {
      restoreOne(plugin, batchDir.resolve("config.yml"), dataDir.resolve("config.yml"), "config.yml");
      restoreOne(plugin, batchDir.resolve("quests.yml"), dataDir.resolve("quests.yml"), "quests.yml");
      restoreOne(plugin, batchDir.resolve("rewards.yml"), dataDir.resolve("rewards.yml"), "rewards.yml");
      return true;
    } catch (IOException e) {
      plugin.getLogger().warning("Batch file restore failed: " + e.getMessage());
      return false;
    }
  }

  public static boolean hasBatchFiles(JavaPlugin plugin, long batchId) {
    if (plugin == null) return false;
    if (batchId <= 0) return false;
    Path dataDir = plugin.getDataFolder().toPath();
    Path batchDir = dataDir.resolve("backups").resolve("batch-" + batchId);
    return Files.isDirectory(batchDir);
  }

  private static void restoreOne(JavaPlugin plugin, Path src, Path dst, String name) throws IOException {
    if (!Files.exists(src)) return;
    Files.createDirectories(dst.getParent());

    // Restore atomically to avoid half-written YAML on crash.
    Path tmp = dst.resolveSibling(name + ".tmp");
    Files.copy(src, tmp, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
    AtomicFiles.moveReplace(tmp, dst);
  }

  private static void copyIfExists(Path src, Path dst) throws IOException {
    if (!Files.exists(src)) return;
    Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
  }

  private static void pruneOldBatches(JavaPlugin plugin, int keep) {
    if (keep <= 0 || plugin == null) return;

    Path backupsDir = plugin.getDataFolder().toPath().resolve("backups");
    if (!Files.isDirectory(backupsDir)) return;

    try {
      List<Path> batches = new ArrayList<>();
      try (DirectoryStream<Path> ds = Files.newDirectoryStream(backupsDir, "batch-*") ) {
        for (Path p : ds) {
          if (Files.isDirectory(p)) batches.add(p);
        }
      }

      if (batches.size() <= keep) return;

      // Sort by last modified descending (keep most recent).
      batches.sort(Comparator.comparingLong(BattlePassBatchFiles::lastModified).reversed());

      for (int i = keep; i < batches.size(); i++) {
        deleteDirBestEffort(batches.get(i));
      }
    } catch (IOException e) {
      plugin.getLogger().warning("Batch file prune failed: " + e.getMessage());
    }
  }

  private static long lastModified(Path p) {
    try { return Files.getLastModifiedTime(p).toMillis(); }
    catch (IOException ignored) { return 0L; }
  }

  private static void deleteDirBestEffort(Path dir) {
    if (dir == null) return;
    try {
      if (!Files.exists(dir)) return;
      Files.walk(dir)
        .sorted(Comparator.reverseOrder())
        .forEach(path -> {
          try { Files.deleteIfExists(path); } catch (IOException ignored) {}
        });
    } catch (IOException ignored) {
      // Best-effort.
    }
  }
}
