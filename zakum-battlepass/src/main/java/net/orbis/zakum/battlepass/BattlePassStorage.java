package net.orbis.zakum.battlepass;

import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.api.db.Jdbc;
import net.orbis.zakum.api.util.UuidBytes;
import net.orbis.zakum.battlepass.model.QuestDef;
import net.orbis.zakum.battlepass.state.PlayerBpState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DB IO helpers for BattlePass.
 *
 * All calls must happen off the main thread.
 */
public final class BattlePassStorage {

  private BattlePassStorage() {}

  public static PlayerBpState loadPlayer(ZakumApi zakum, String serverId, int season, Map<String, QuestDef> quests, UUID uuid) {
    Jdbc jdbc = zakum.database().jdbc();
    byte[] ub = UuidBytes.toBytes(uuid);

    PlayerBpState st = new PlayerBpState();

    // Progress
    var rows = jdbc.query(
      "SELECT tier, points FROM orbis_battlepass_progress WHERE server_id=? AND season=? AND uuid=? LIMIT 1",
      rs -> new Row(rs.getInt(1), rs.getLong(2)),
      serverId, season, ub
    );
    if (!rows.isEmpty()) {
      st.seedProgress(rows.get(0).tier, rows.get(0).points);
    } else {
      st.seedProgress(0, 0);
    }

    // Steps
    var steps = jdbc.query(
      "SELECT quest_id, step_idx, progress FROM orbis_battlepass_step_progress WHERE server_id=? AND season=? AND uuid=?",
      rs -> new StepRow(rs.getString(1), rs.getInt(2), rs.getLong(3)),
      serverId, season, ub
    );

    for (var r : steps) {
      st.seedQuest(r.questId, r.stepIdx, r.progress);
    }

    // Claims
    var claims = jdbc.query(
      "SELECT tier, track FROM orbis_battlepass_claims WHERE server_id=? AND season=? AND uuid=?",
      rs -> new ClaimRow(rs.getInt(1), rs.getString(2)),
      serverId, season, ub
    );
    for (var c : claims) {
      boolean prem = c.track != null && c.track.equalsIgnoreCase("PREMIUM");
      st.seedClaim(prem, c.tier);
    }

    // Ensure every quest exists in memory (avoid missing state when quests.yml changes)
    for (String qid : quests.keySet()) {
      st.ensureQuest(qid);
    }

    return st;
  }

  public static void flushPlayerDelta(ZakumApi zakum, String serverId, int season, UUID uuid, PlayerBpState.DeltaSnapshot delta) {
    if (delta == null) return;

    Jdbc jdbc = zakum.database().jdbc();
    byte[] ub = UuidBytes.toBytes(uuid);

    if (delta.writeProgress()) {
      jdbc.update(
        "INSERT INTO orbis_battlepass_progress (server_id, season, uuid, tier, points) VALUES (?,?,?,?,?) " +
          "ON DUPLICATE KEY UPDATE tier=VALUES(tier), points=VALUES(points)",
        serverId, season, ub, delta.tier(), delta.points()
      );
    }

    // Steps: multi-row upsert, chunked.
    if (!delta.dirtySteps().isEmpty()) {
      List<Map.Entry<String, PlayerBpState.StepStateSnap>> entries = new ArrayList<>(delta.dirtySteps().entrySet());
      final int chunkSize = 100;

      for (int i = 0; i < entries.size(); i += chunkSize) {
        int end = Math.min(entries.size(), i + chunkSize);
        List<Object> params = new ArrayList<>((end - i) * 6);

        StringBuilder sb = new StringBuilder(256);
        sb.append("INSERT INTO orbis_battlepass_step_progress ")
          .append("(server_id, season, uuid, quest_id, step_idx, progress) VALUES ");

        for (int j = i; j < end; j++) {
          if (j > i) sb.append(',');
          sb.append("(?,?,?,?,?,?)");
        }

        sb.append(" ON DUPLICATE KEY UPDATE step_idx=VALUES(step_idx), progress=VALUES(progress)");

        for (int j = i; j < end; j++) {
          var e = entries.get(j);
          String questId = e.getKey();
          var ss = e.getValue();
          params.add(serverId);
          params.add(season);
          params.add(ub);
          params.add(questId);
          params.add(ss.stepIdx());
          params.add(ss.progress());
        }

        jdbc.update(sb.toString(), params.toArray());
      }
    }

    // Claims: insert-only, chunked.
    if (delta.dirtyClaims() != null && !delta.dirtyClaims().isEmpty()) {
      List<PlayerBpState.ClaimSnap> claims = new ArrayList<>(delta.dirtyClaims());
      final int chunk = 200;

      for (int i = 0; i < claims.size(); i += chunk) {
        int end = Math.min(claims.size(), i + chunk);
        List<Object> params = new ArrayList<>((end - i) * 5);

        StringBuilder sb = new StringBuilder(256);
        sb.append("INSERT IGNORE INTO orbis_battlepass_claims ")
          .append("(server_id, season, uuid, tier, track) VALUES ");

        for (int j = i; j < end; j++) {
          if (j > i) sb.append(',');
          sb.append("(?,?,?,?,?)");
        }

        for (int j = i; j < end; j++) {
          var c = claims.get(j);
          params.add(serverId);
          params.add(season);
          params.add(ub);
          params.add(c.tier());
          params.add(c.premium() ? "PREMIUM" : "FREE");
        }

        jdbc.update(sb.toString(), params.toArray());
      }
    }
  }

  public static PeriodState loadPeriod(ZakumApi zakum, String serverId, int season, UUID uuid) {
    Jdbc jdbc = zakum.database().jdbc();
    byte[] ub = UuidBytes.toBytes(uuid);

    var rows = jdbc.query(
      "SELECT daily_day, weekly_week FROM orbis_battlepass_periods WHERE server_id=? AND season=? AND uuid=? LIMIT 1",
      rs -> new PeriodState(rs.getLong(1), rs.getLong(2)),
      serverId, season, ub
    );
    if (rows.isEmpty()) return new PeriodState(0, 0);
    return rows.get(0);
  }

  public static void upsertPeriod(ZakumApi zakum, String serverId, int season, UUID uuid, long dailyDay, long weeklyWeek) {
    Jdbc jdbc = zakum.database().jdbc();
    byte[] ub = UuidBytes.toBytes(uuid);

    jdbc.update(
      "INSERT INTO orbis_battlepass_periods (server_id, season, uuid, daily_day, weekly_week) VALUES (?,?,?,?,?) " +
        "ON DUPLICATE KEY UPDATE daily_day=VALUES(daily_day), weekly_week=VALUES(weekly_week)",
      serverId, season, ub, dailyDay, weeklyWeek
    );
  }

  private record Row(int tier, long points) {}
  public record PeriodState(long dailyDay, long weeklyWeek) {}
  private record StepRow(String questId, int stepIdx, long progress) {}
  private record ClaimRow(int tier, String track) {}
}
