package net.orbis.zakum.battlepass.leaderboard;

import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.api.db.DatabaseState;
import net.orbis.zakum.battlepass.BattlePassRuntime;
import net.orbis.zakum.api.db.Jdbc;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * BattlePass leaderboard cache (async refreshed).
 *
 * Design:
 * - refresh is async and bounded
 * - reads from DB only on interval
 * - provides pages from in-memory snapshot
 */
public final class BattlePassLeaderboard {

  public record Entry(UUID uuid, long points, int tier) {}

  private final ZakumApi zakum;
  private final Executor async;
  private final BattlePassRuntime runtime;

  private final int maxEntries;
  private final AtomicReference<List<Entry>> snapshot = new AtomicReference<>(List.of());

  public BattlePassLeaderboard(ZakumApi zakum, Executor async, BattlePassRuntime runtime, int maxEntries) {
    this.zakum = Objects.requireNonNull(zakum, "zakum");
    this.async = Objects.requireNonNull(async, "async");
    this.runtime = Objects.requireNonNull(runtime, "runtime");
    this.maxEntries = Math.max(10, Math.min(5000, maxEntries));
  }

  public void refreshAsync() {
    if (zakum.database().state() != DatabaseState.ONLINE) return;
    async.execute(this::refreshNow);
  }

  public void refreshNow() {
    if (zakum.database().state() != DatabaseState.ONLINE) return;

    String serverId = runtime.progressServerId();
    int season = runtime.season();

    Jdbc jdbc = zakum.database().jdbc();
    List<Entry> rows = jdbc.query(
      "SELECT uuid, points, tier FROM orbis_battlepass_progress WHERE server_id=? AND season=? ORDER BY points DESC LIMIT ?",
      rs -> {
        byte[] b = rs.getBytes(1);
        UUID u = fromBytes(b);
        long p = rs.getLong(2);
        int t = rs.getInt(3);
        return new Entry(u, p, t);
      },
      serverId, season, maxEntries
    );

    snapshot.set(List.copyOf(rows));
  }

  public List<Entry> top() {
    return snapshot.get();
  }

  public List<Entry> page(int page, int pageSize) {

    int p = Math.max(1, page);
    int size = Math.max(1, Math.min(50, pageSize));
    List<Entry> list = snapshot.get();
    int from = (p - 1) * size;
    if (from >= list.size()) return List.of();
    int to = Math.min(list.size(), from + size);
    return list.subList(from, to);
  }


  private static UUID fromBytes(byte[] b) {
    if (b == null || b.length != 16) return new UUID(0L, 0L);
    long msb = 0;
    long lsb = 0;
    for (int i = 0; i < 8; i++) msb = (msb << 8) | (b[i] & 0xffL);
    for (int i = 8; i < 16; i++) lsb = (lsb << 8) | (b[i] & 0xffL);
    return new UUID(msb, lsb);
  }
}
