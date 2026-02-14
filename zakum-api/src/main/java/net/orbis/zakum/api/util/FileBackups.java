package net.orbis.zakum.api.util;

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

/**
 * Simple rotating file backup helper.
 *
 * Intended for admin-driven config writes (commands, in-game editors) where a
 * "last known good" file copy is valuable, and disk usage should be bounded.
 */
public final class FileBackups {

  private FileBackups() {}

  private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    .withZone(ZoneOffset.UTC);

  /**
   * Creates a timestamped backup copy and optionally prunes older backups.
   *
   * @param source file to backup
   * @param backupDir directory to store backups in
   * @param prefix filename prefix (e.g. "config")
   * @param keep maximum backups to keep (<=0 disables pruning)
   * @return backup file path (or null if source missing)
   */
  public static Path backup(Path source, Path backupDir, String prefix, int keep) throws IOException {
    if (source == null || !Files.exists(source)) return null;
    if (backupDir == null) throw new IOException("backupDir missing");
    if (prefix == null || prefix.isBlank()) prefix = "backup";

    Files.createDirectories(backupDir);

    String ts = TS.format(Instant.now());
    Path out = backupDir.resolve(prefix + "-" + ts + ".bak");

    Files.copy(source, out, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);

    if (keep > 0) prune(backupDir, prefix, keep);
    return out;
  }

  private static void prune(Path backupDir, String prefix, int keep) {
    try {
      List<Path> matches = new ArrayList<>();
      try (var s = Files.list(backupDir)) {
        s.filter(p -> {
          String n = p.getFileName().toString();
          return n.startsWith(prefix + "-") && n.endsWith(".bak");
        }).forEach(matches::add);
      }

      matches.sort(Comparator.comparingLong(FileBackups::lastModifiedSafe).reversed());

      for (int i = keep; i < matches.size(); i++) {
        try { Files.deleteIfExists(matches.get(i)); } catch (IOException ignored) {}
      }
    } catch (Throwable ignored) {
      // Backups are best-effort. Never fail a command because pruning failed.
    }
  }

  private static long lastModifiedSafe(Path p) {
    try { return Files.getLastModifiedTime(p).toMillis(); }
    catch (IOException e) { return 0L; }
  }
}
