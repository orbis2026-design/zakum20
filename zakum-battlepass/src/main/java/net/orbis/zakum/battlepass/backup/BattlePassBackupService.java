package net.orbis.zakum.battlepass.backup;

import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.api.db.DatabaseState;
import net.orbis.zakum.api.db.Jdbc;
import net.orbis.zakum.battlepass.db.BattlePassSchema;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * BattlePass backup + purge tooling.
 *
 * Goals:
 * - ops safety for season rollovers
 * - bounded work (chunked reads/inserts/deletes)
 * - never blocks main thread
 */
public final class BattlePassBackupService {

  private BattlePassBackupService() {}

  public record BackupResult(
    boolean ok,
    long batchId,
    String message,
    int progressRows,
    int stepRows,
    int claimRows,
    int periodRows
  ) {}

  public record PurgeResult(
    boolean ok,
    String message,
    int progressDeleted,
    int stepDeleted,
    int claimDeleted,
    int periodDeleted
  ) {}

  public record RestoreResult(
    boolean ok,
    long batchId,
    String message,
    int progressRestored,
    int stepRestored,
    int claimRestored,
    int periodRestored
  ) {}

  public record BackupBatch(long batchId, int season, String status, Timestamp createdAt, String createdBy, String note) {}

  private record BatchInfo(long batchId, String serverId, int season, String status) {}

  public static CompletableFuture<BackupResult> backupSeasonAsync(
    ZakumApi zakum,
    String serverId,
    int season,
    String createdBy,
    String note,
    int chunkSize
  ) {
    Objects.requireNonNull(zakum, "zakum");
    String sid = (serverId == null || serverId.isBlank()) ? "unknown" : serverId;
    int s = Math.max(1, season);
    int chunk = clamp(chunkSize, 50, 500);

    return CompletableFuture.supplyAsync(() -> backupSeason(zakum, sid, s, createdBy, note, chunk), zakum.async());
  }

  public static CompletableFuture<PurgeResult> purgeSeasonAsync(
    ZakumApi zakum,
    String serverId,
    int season,
    int deleteLimit
  ) {
    Objects.requireNonNull(zakum, "zakum");
    String sid = (serverId == null || serverId.isBlank()) ? "unknown" : serverId;
    int s = Math.max(1, season);
    int limit = clamp(deleteLimit, 100, 50_000);

    return CompletableFuture.supplyAsync(() -> purgeSeason(zakum, sid, s, limit), zakum.async());
  }

  public static CompletableFuture<List<BackupBatch>> listBatchesAsync(
    ZakumApi zakum,
    String serverId,
    int limit
  ) {
    Objects.requireNonNull(zakum, "zakum");
    String sid = (serverId == null || serverId.isBlank()) ? "unknown" : serverId;
    int lim = clamp(limit, 1, 50);
    return CompletableFuture.supplyAsync(() -> listBatches(zakum, sid, lim), zakum.async());
  }

  /**
   * Restores a season snapshot from the archive tables back into live tables.
   *
   * Safety posture:
   * - refuses to restore if live tables already contain data for server_id+season unless overwrite=true
   * - bounded chunked inserts
   * - runs async (never blocks main thread)
   */
  public static CompletableFuture<RestoreResult> restoreBatchAsync(
    ZakumApi zakum,
    long batchId,
    boolean overwrite,
    int chunkSize,
    int deleteLimit
  ) {
    Objects.requireNonNull(zakum, "zakum");
    long bid = Math.max(1, batchId);
    int chunk = clamp(chunkSize, 50, 500);
    int limit = clamp(deleteLimit, 100, 50_000);
    return CompletableFuture.supplyAsync(() -> restoreBatch(zakum, bid, overwrite, chunk, limit), zakum.async());
  }

  private static BackupResult backupSeason(
    ZakumApi zakum,
    String serverId,
    int season,
    String createdBy,
    String note,
    int chunk
  ) {
    if (zakum.database().state() != DatabaseState.ONLINE) {
      return new BackupResult(false, 0, "DB offline", 0, 0, 0, 0);
    }

    Jdbc jdbc = zakum.database().jdbc();

    // Failsafe: if DB became online after plugin enable, schema might not have been created yet.
    BattlePassSchema.ensureTables(jdbc);

    long batchId = newBatchId();
    Timestamp now = Timestamp.from(Instant.now());

    try {
      jdbc.update(
        "INSERT INTO orbis_battlepass_backup_batches (batch_id, server_id, season, created_by, note, status) VALUES (?,?,?,?,?, 'RUNNING')",
        batchId, serverId, season, safe(createdBy, 64), safe(note, 255)
      );

      int progress = copyProgress(jdbc, batchId, now, serverId, season, chunk);
      int steps = copySteps(jdbc, batchId, now, serverId, season, chunk);
      int claims = copyClaims(jdbc, batchId, now, serverId, season, chunk);
      int periods = copyPeriods(jdbc, batchId, now, serverId, season, chunk);

      jdbc.update(
        "UPDATE orbis_battlepass_backup_batches SET status='OK' WHERE batch_id=?",
        batchId
      );

      return new BackupResult(true, batchId, "OK", progress, steps, claims, periods);

    } catch (Throwable t) {
      String msg = t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage());
      try {
        jdbc.update(
          "UPDATE orbis_battlepass_backup_batches SET status='FAILED', error=? WHERE batch_id=?",
          truncate(msg, 2000), batchId
        );
      } catch (Throwable ignored) {}

      return new BackupResult(false, batchId, msg, 0, 0, 0, 0);
    }
  }

  private static PurgeResult purgeSeason(ZakumApi zakum, String serverId, int season, int deleteLimit) {
    if (zakum.database().state() != DatabaseState.ONLINE) {
      return new PurgeResult(false, "DB offline", 0, 0, 0, 0);
    }

    Jdbc jdbc = zakum.database().jdbc();

    BattlePassSchema.ensureTables(jdbc);

    try {
      int prog = deleteLoop(jdbc, "DELETE FROM orbis_battlepass_progress WHERE server_id=? AND season=? LIMIT ?", serverId, season, deleteLimit);
      int steps = deleteLoop(jdbc, "DELETE FROM orbis_battlepass_step_progress WHERE server_id=? AND season=? LIMIT ?", serverId, season, deleteLimit);
      int claims = deleteLoop(jdbc, "DELETE FROM orbis_battlepass_claims WHERE server_id=? AND season=? LIMIT ?", serverId, season, deleteLimit);
      int periods = deleteLoop(jdbc, "DELETE FROM orbis_battlepass_periods WHERE server_id=? AND season=? LIMIT ?", serverId, season, deleteLimit);
      return new PurgeResult(true, "OK", prog, steps, claims, periods);
    } catch (Throwable t) {
      String msg = t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage());
      return new PurgeResult(false, msg, 0, 0, 0, 0);
    }
  }

  private static int deleteLoop(Jdbc jdbc, String sql, String serverId, int season, int limit) {
    int total = 0;
    while (true) {
      int n = jdbc.update(sql, serverId, season, limit);
      total += n;
      if (n < limit) break;
    }
    return total;
  }

  private static List<BackupBatch> listBatches(ZakumApi zakum, String serverId, int limit) {
    if (zakum.database().state() != DatabaseState.ONLINE) return List.of();
    Jdbc jdbc = zakum.database().jdbc();
    return jdbc.query(
      "SELECT batch_id, season, status, created_at, created_by, note FROM orbis_battlepass_backup_batches WHERE server_id=? ORDER BY created_at DESC LIMIT ?",
      rs -> new BackupBatch(
        rs.getLong(1),
        rs.getInt(2),
        rs.getString(3),
        rs.getTimestamp(4),
        rs.getString(5),
        rs.getString(6)
      ),
      serverId, limit
    );
  }

  private record ProgressRow(byte[] uuid, int tier, long points, Timestamp updatedAt) {}
  private record StepRow(byte[] uuid, String questId, int stepIdx, long progress, Timestamp updatedAt) {}
  private record ClaimRow(byte[] uuid, int tier, String track, Timestamp claimedAt) {}
  private record PeriodRow(byte[] uuid, long dailyDay, long weeklyWeek, Timestamp updatedAt) {}

  private record ArchProgress(long id, byte[] uuid, int tier, long points, Timestamp updatedAt) {}
  private record ArchStep(long id, byte[] uuid, String questId, int stepIdx, long progress, Timestamp updatedAt) {}
  private record ArchClaim(long id, byte[] uuid, int tier, String track, Timestamp claimedAt) {}
  private record ArchPeriod(long id, byte[] uuid, long dailyDay, long weeklyWeek, Timestamp updatedAt) {}

  private static int copyProgress(Jdbc jdbc, long batchId, Timestamp archivedAt, String serverId, int season, int chunk) {
    byte[] cursor = new byte[16];
    int total = 0;
    while (true) {
      List<ProgressRow> rows = jdbc.query(
        "SELECT uuid, tier, points, updated_at FROM orbis_battlepass_progress " +
          "WHERE server_id=? AND season=? AND uuid > ? ORDER BY uuid LIMIT ?",
        rs -> new ProgressRow(rs.getBytes(1), rs.getInt(2), rs.getLong(3), rs.getTimestamp(4)),
        serverId, season, cursor, chunk
      );
      if (rows.isEmpty()) break;

      total += insertProgress(jdbc, batchId, archivedAt, serverId, season, rows);
      cursor = rows.get(rows.size() - 1).uuid();

      if (rows.size() < chunk) break;
    }
    return total;
  }

  private static int insertProgress(Jdbc jdbc, long batchId, Timestamp archivedAt, String serverId, int season, List<ProgressRow> rows) {
    StringBuilder sb = new StringBuilder();
    List<Object> params = new ArrayList<>(rows.size() * 8);
    sb.append("INSERT INTO orbis_battlepass_progress_archive (batch_id, archived_at, server_id, season, uuid, tier, points, updated_at) VALUES ");
    for (int i = 0; i < rows.size(); i++) {
      if (i > 0) sb.append(',');
      sb.append("(?,?,?,?,?,?,?,?)");
      ProgressRow r = rows.get(i);
      params.add(batchId);
      params.add(archivedAt);
      params.add(serverId);
      params.add(season);
      params.add(r.uuid());
      params.add(r.tier());
      params.add(r.points());
      params.add(r.updatedAt());
    }
    return jdbc.update(sb.toString(), params.toArray());
  }

  private static int copySteps(Jdbc jdbc, long batchId, Timestamp archivedAt, String serverId, int season, int chunk) {
    byte[] cursorUuid = new byte[16];
    String cursorQuest = "";
    int total = 0;
    while (true) {
      List<StepRow> rows = jdbc.query(
        "SELECT uuid, quest_id, step_idx, progress, updated_at FROM orbis_battlepass_step_progress " +
          "WHERE server_id=? AND season=? AND (uuid > ? OR (uuid = ? AND quest_id > ?)) " +
          "ORDER BY uuid, quest_id LIMIT ?",
        rs -> new StepRow(rs.getBytes(1), rs.getString(2), rs.getInt(3), rs.getLong(4), rs.getTimestamp(5)),
        serverId, season, cursorUuid, cursorUuid, cursorQuest, chunk
      );
      if (rows.isEmpty()) break;
      total += insertSteps(jdbc, batchId, archivedAt, serverId, season, rows);
      StepRow last = rows.get(rows.size() - 1);
      cursorUuid = last.uuid();
      cursorQuest = last.questId();
      if (rows.size() < chunk) break;
    }
    return total;
  }

  private static int insertSteps(Jdbc jdbc, long batchId, Timestamp archivedAt, String serverId, int season, List<StepRow> rows) {
    StringBuilder sb = new StringBuilder();
    List<Object> params = new ArrayList<>(rows.size() * 9);
    sb.append("INSERT INTO orbis_battlepass_step_progress_archive (batch_id, archived_at, server_id, season, uuid, quest_id, step_idx, progress, updated_at) VALUES ");
    for (int i = 0; i < rows.size(); i++) {
      if (i > 0) sb.append(',');
      sb.append("(?,?,?,?,?,?,?,?,?)");
      StepRow r = rows.get(i);
      params.add(batchId);
      params.add(archivedAt);
      params.add(serverId);
      params.add(season);
      params.add(r.uuid());
      params.add(r.questId());
      params.add(r.stepIdx());
      params.add(r.progress());
      params.add(r.updatedAt());
    }
    return jdbc.update(sb.toString(), params.toArray());
  }

  private static int copyClaims(Jdbc jdbc, long batchId, Timestamp archivedAt, String serverId, int season, int chunk) {
    byte[] cursorUuid = new byte[16];
    int cursorTier = Integer.MIN_VALUE;
    String cursorTrack = "";
    int total = 0;
    while (true) {
      List<ClaimRow> rows = jdbc.query(
        "SELECT uuid, tier, track, claimed_at FROM orbis_battlepass_claims " +
          "WHERE server_id=? AND season=? AND (uuid > ? OR (uuid = ? AND (tier > ? OR (tier = ? AND track > ?)))) " +
          "ORDER BY uuid, tier, track LIMIT ?",
        rs -> new ClaimRow(rs.getBytes(1), rs.getInt(2), rs.getString(3), rs.getTimestamp(4)),
        serverId, season, cursorUuid, cursorUuid, cursorTier, cursorTier, cursorTrack, chunk
      );
      if (rows.isEmpty()) break;
      total += insertClaims(jdbc, batchId, archivedAt, serverId, season, rows);
      ClaimRow last = rows.get(rows.size() - 1);
      cursorUuid = last.uuid();
      cursorTier = last.tier();
      cursorTrack = last.track();
      if (rows.size() < chunk) break;
    }
    return total;
  }

  private static int insertClaims(Jdbc jdbc, long batchId, Timestamp archivedAt, String serverId, int season, List<ClaimRow> rows) {
    StringBuilder sb = new StringBuilder();
    List<Object> params = new ArrayList<>(rows.size() * 8);
    sb.append("INSERT INTO orbis_battlepass_claims_archive (batch_id, archived_at, server_id, season, uuid, tier, track, claimed_at) VALUES ");
    for (int i = 0; i < rows.size(); i++) {
      if (i > 0) sb.append(',');
      sb.append("(?,?,?,?,?,?,?,?)");
      ClaimRow r = rows.get(i);
      params.add(batchId);
      params.add(archivedAt);
      params.add(serverId);
      params.add(season);
      params.add(r.uuid());
      params.add(r.tier());
      params.add(r.track());
      params.add(r.claimedAt());
    }
    return jdbc.update(sb.toString(), params.toArray());
  }

  private static int copyPeriods(Jdbc jdbc, long batchId, Timestamp archivedAt, String serverId, int season, int chunk) {
    byte[] cursor = new byte[16];
    int total = 0;
    while (true) {
      List<PeriodRow> rows = jdbc.query(
        "SELECT uuid, daily_day, weekly_week, updated_at FROM orbis_battlepass_periods " +
          "WHERE server_id=? AND season=? AND uuid > ? ORDER BY uuid LIMIT ?",
        rs -> new PeriodRow(rs.getBytes(1), rs.getLong(2), rs.getLong(3), rs.getTimestamp(4)),
        serverId, season, cursor, chunk
      );
      if (rows.isEmpty()) break;
      total += insertPeriods(jdbc, batchId, archivedAt, serverId, season, rows);
      cursor = rows.get(rows.size() - 1).uuid();
      if (rows.size() < chunk) break;
    }
    return total;
  }

  private static int insertPeriods(Jdbc jdbc, long batchId, Timestamp archivedAt, String serverId, int season, List<PeriodRow> rows) {
    StringBuilder sb = new StringBuilder();
    List<Object> params = new ArrayList<>(rows.size() * 8);
    sb.append("INSERT INTO orbis_battlepass_periods_archive (batch_id, archived_at, server_id, season, uuid, daily_day, weekly_week, updated_at) VALUES ");
    for (int i = 0; i < rows.size(); i++) {
      if (i > 0) sb.append(',');
      sb.append("(?,?,?,?,?,?,?,?)");
      PeriodRow r = rows.get(i);
      params.add(batchId);
      params.add(archivedAt);
      params.add(serverId);
      params.add(season);
      params.add(r.uuid());
      params.add(r.dailyDay());
      params.add(r.weeklyWeek());
      params.add(r.updatedAt());
    }
    return jdbc.update(sb.toString(), params.toArray());
  }

  private static RestoreResult restoreBatch(ZakumApi zakum, long batchId, boolean overwrite, int chunk, int deleteLimit) {
    if (zakum.database().state() != DatabaseState.ONLINE) {
      return new RestoreResult(false, batchId, "DB offline", 0, 0, 0, 0);
    }

    Jdbc jdbc = zakum.database().jdbc();

    // Failsafe: schema might not exist yet.
    BattlePassSchema.ensureTables(jdbc);

    BatchInfo info = findBatch(jdbc, batchId);
    if (info == null) {
      return new RestoreResult(false, batchId, "unknown batch", 0, 0, 0, 0);
    }
    if (info.status() == null || !info.status().equalsIgnoreCase("OK")) {
      return new RestoreResult(false, batchId, "batch not OK (status=" + info.status() + ")", 0, 0, 0, 0);
    }

    String serverId = info.serverId();
    int season = info.season();

    // Refuse to overwrite live data unless explicitly requested.
    boolean hasLive = hasAny(jdbc, "orbis_battlepass_progress", serverId, season)
      || hasAny(jdbc, "orbis_battlepass_step_progress", serverId, season)
      || hasAny(jdbc, "orbis_battlepass_claims", serverId, season)
      || hasAny(jdbc, "orbis_battlepass_periods", serverId, season);

    if (hasLive && !overwrite) {
      return new RestoreResult(false, batchId, "target season already has data; use OVERWRITE", 0, 0, 0, 0);
    }

    if (hasLive) {
      // Purge first (bounded loop).
      purgeSeason(zakum, serverId, season, deleteLimit);
    }

    try {
      int p = restoreProgressFromArchive(jdbc, batchId, serverId, season, chunk);
      int s = restoreStepsFromArchive(jdbc, batchId, serverId, season, chunk);
      int c = restoreClaimsFromArchive(jdbc, batchId, serverId, season, chunk);
      int r = restorePeriodsFromArchive(jdbc, batchId, serverId, season, chunk);

      if (p == 0 && s == 0 && c == 0 && r == 0) {
        return new RestoreResult(false, batchId, "archive empty", 0, 0, 0, 0);
      }

      return new RestoreResult(true, batchId, "OK", p, s, c, r);
    } catch (Throwable t) {
      String msg = t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage());
      return new RestoreResult(false, batchId, msg, 0, 0, 0, 0);
    }
  }

  private static BatchInfo findBatch(Jdbc jdbc, long batchId) {
    List<BatchInfo> rows = jdbc.query(
      "SELECT batch_id, server_id, season, status FROM orbis_battlepass_backup_batches WHERE batch_id=? LIMIT 1",
      rs -> new BatchInfo(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getString(4)),
      batchId
    );
    if (rows == null || rows.isEmpty()) return null;
    return rows.get(0);
  }

  private static boolean hasAny(Jdbc jdbc, String table, String serverId, int season) {
    List<Integer> rows = jdbc.query(
      "SELECT 1 FROM " + table + " WHERE server_id=? AND season=? LIMIT 1",
      rs -> 1,
      serverId, season
    );
    return rows != null && !rows.isEmpty();
  }

  private static int restoreProgressFromArchive(Jdbc jdbc, long batchId, String serverId, int season, int chunk) {
    long cursor = 0L;
    int total = 0;
    while (true) {
      List<ArchProgress> rows = jdbc.query(
        "SELECT id, uuid, tier, points, updated_at FROM orbis_battlepass_progress_archive WHERE batch_id=? AND id > ? ORDER BY id LIMIT ?",
        rs -> new ArchProgress(rs.getLong(1), rs.getBytes(2), rs.getInt(3), rs.getLong(4), rs.getTimestamp(5)),
        batchId, cursor, chunk
      );
      if (rows == null || rows.isEmpty()) break;

      total += insertRestoredProgress(jdbc, serverId, season, rows);
      cursor = rows.get(rows.size() - 1).id();
      if (rows.size() < chunk) break;
    }
    return total;
  }

  private static int insertRestoredProgress(Jdbc jdbc, String serverId, int season, List<ArchProgress> rows) {
    StringBuilder sb = new StringBuilder();
    List<Object> params = new ArrayList<>(rows.size() * 6);
    sb.append("INSERT INTO orbis_battlepass_progress (server_id, season, uuid, tier, points, updated_at) VALUES ");
    for (int i = 0; i < rows.size(); i++) {
      if (i > 0) sb.append(',');
      sb.append("(?,?,?,?,?,?)");
      ArchProgress r = rows.get(i);
      params.add(serverId);
      params.add(season);
      params.add(r.uuid());
      params.add(r.tier());
      params.add(r.points());
      params.add(r.updatedAt());
    }
    sb.append(" AS new ON DUPLICATE KEY UPDATE tier=new.tier, points=new.points, updated_at=new.updated_at");
    return jdbc.update(sb.toString(), params.toArray());
  }

  private static int restoreStepsFromArchive(Jdbc jdbc, long batchId, String serverId, int season, int chunk) {
    long cursor = 0L;
    int total = 0;
    while (true) {
      List<ArchStep> rows = jdbc.query(
        "SELECT id, uuid, quest_id, step_idx, progress, updated_at FROM orbis_battlepass_step_progress_archive WHERE batch_id=? AND id > ? ORDER BY id LIMIT ?",
        rs -> new ArchStep(rs.getLong(1), rs.getBytes(2), rs.getString(3), rs.getInt(4), rs.getLong(5), rs.getTimestamp(6)),
        batchId, cursor, chunk
      );
      if (rows == null || rows.isEmpty()) break;
      total += insertRestoredSteps(jdbc, serverId, season, rows);
      cursor = rows.get(rows.size() - 1).id();
      if (rows.size() < chunk) break;
    }
    return total;
  }

  private static int insertRestoredSteps(Jdbc jdbc, String serverId, int season, List<ArchStep> rows) {
    StringBuilder sb = new StringBuilder();
    List<Object> params = new ArrayList<>(rows.size() * 7);
    sb.append("INSERT INTO orbis_battlepass_step_progress (server_id, season, uuid, quest_id, step_idx, progress, updated_at) VALUES ");
    for (int i = 0; i < rows.size(); i++) {
      if (i > 0) sb.append(',');
      sb.append("(?,?,?,?,?,?,?)");
      ArchStep r = rows.get(i);
      params.add(serverId);
      params.add(season);
      params.add(r.uuid());
      params.add(r.questId());
      params.add(r.stepIdx());
      params.add(r.progress());
      params.add(r.updatedAt());
    }
    sb.append(" AS new ON DUPLICATE KEY UPDATE quest_id=new.quest_id, step_idx=new.step_idx, progress=new.progress, updated_at=new.updated_at");
    return jdbc.update(sb.toString(), params.toArray());
  }

  private static int restoreClaimsFromArchive(Jdbc jdbc, long batchId, String serverId, int season, int chunk) {
    long cursor = 0L;
    int total = 0;
    while (true) {
      List<ArchClaim> rows = jdbc.query(
        "SELECT id, uuid, tier, track, claimed_at FROM orbis_battlepass_claims_archive WHERE batch_id=? AND id > ? ORDER BY id LIMIT ?",
        rs -> new ArchClaim(rs.getLong(1), rs.getBytes(2), rs.getInt(3), rs.getString(4), rs.getTimestamp(5)),
        batchId, cursor, chunk
      );
      if (rows == null || rows.isEmpty()) break;
      total += insertRestoredClaims(jdbc, serverId, season, rows);
      cursor = rows.get(rows.size() - 1).id();
      if (rows.size() < chunk) break;
    }
    return total;
  }

  private static int insertRestoredClaims(Jdbc jdbc, String serverId, int season, List<ArchClaim> rows) {
    StringBuilder sb = new StringBuilder();
    List<Object> params = new ArrayList<>(rows.size() * 6);
    sb.append("INSERT INTO orbis_battlepass_claims (server_id, season, uuid, tier, track, claimed_at) VALUES ");
    for (int i = 0; i < rows.size(); i++) {
      if (i > 0) sb.append(',');
      sb.append("(?,?,?,?,?,?)");
      ArchClaim r = rows.get(i);
      params.add(serverId);
      params.add(season);
      params.add(r.uuid());
      params.add(r.tier());
      params.add(r.track());
      params.add(r.claimedAt());
    }
    sb.append(" AS new ON DUPLICATE KEY UPDATE claimed_at=new.claimed_at");
    return jdbc.update(sb.toString(), params.toArray());
  }

  private static int restorePeriodsFromArchive(Jdbc jdbc, long batchId, String serverId, int season, int chunk) {
    long cursor = 0L;
    int total = 0;
    while (true) {
      List<ArchPeriod> rows = jdbc.query(
        "SELECT id, uuid, daily_day, weekly_week, updated_at FROM orbis_battlepass_periods_archive WHERE batch_id=? AND id > ? ORDER BY id LIMIT ?",
        rs -> new ArchPeriod(rs.getLong(1), rs.getBytes(2), rs.getLong(3), rs.getLong(4), rs.getTimestamp(5)),
        batchId, cursor, chunk
      );
      if (rows == null || rows.isEmpty()) break;
      total += insertRestoredPeriods(jdbc, serverId, season, rows);
      cursor = rows.get(rows.size() - 1).id();
      if (rows.size() < chunk) break;
    }
    return total;
  }

  private static int insertRestoredPeriods(Jdbc jdbc, String serverId, int season, List<ArchPeriod> rows) {
    StringBuilder sb = new StringBuilder();
    List<Object> params = new ArrayList<>(rows.size() * 6);
    sb.append("INSERT INTO orbis_battlepass_periods (server_id, season, uuid, daily_day, weekly_week, updated_at) VALUES ");
    for (int i = 0; i < rows.size(); i++) {
      if (i > 0) sb.append(',');
      sb.append("(?,?,?,?,?,?)");
      ArchPeriod r = rows.get(i);
      params.add(serverId);
      params.add(season);
      params.add(r.uuid());
      params.add(r.dailyDay());
      params.add(r.weeklyWeek());
      params.add(r.updatedAt());
    }
    sb.append(" AS new ON DUPLICATE KEY UPDATE daily_day=new.daily_day, weekly_week=new.weekly_week, updated_at=new.updated_at");
    return jdbc.update(sb.toString(), params.toArray());
  }

  private static long newBatchId() {
    long t = System.currentTimeMillis();
    int r = ThreadLocalRandom.current().nextInt(1 << 10);
    return (t << 10) | r;
  }

  private static int clamp(int v, int min, int max) {
    return Math.max(min, Math.min(max, v));
  }

  private static String safe(String s, int maxLen) {
    if (s == null) return null;
    String x = s.trim();
    if (x.isEmpty()) return null;
    return truncate(x, maxLen);
  }

  private static String truncate(String s, int maxLen) {
    if (s == null) return null;
    if (s.length() <= maxLen) return s;
    return s.substring(0, Math.max(0, maxLen));
  }
}
