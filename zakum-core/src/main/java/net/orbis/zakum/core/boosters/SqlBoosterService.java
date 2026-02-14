package net.orbis.zakum.core.boosters;

import net.orbis.zakum.api.boosters.BoosterKind;
import net.orbis.zakum.api.boosters.BoosterService;
import net.orbis.zakum.api.config.ZakumSettings;
import net.orbis.zakum.api.db.DatabaseState;
import net.orbis.zakum.api.db.ZakumDatabase;
import net.orbis.zakum.api.entitlements.EntitlementScope;
import net.orbis.zakum.core.util.UuidBytes;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Booster manager with:
 * - async DB writes
 * - in-memory multiplier cache for fast main-thread reads
 * - periodic purge + refresh
 */
public final class SqlBoosterService implements BoosterService {

  private static final double MAX_MULT = 100.0;

  private final Plugin plugin;
  private final ZakumDatabase db;
  private final Executor async;
  private final ZakumSettings.Boosters settings;

  // Swap whole maps to avoid lock contention on hot-path multiplier() calls.
  private volatile Map<Key, Double> allMult = Map.of();
  private volatile Map<PlayerKey, Double> playerMult = Map.of();

  private volatile long lastPurgeEpochSeconds = 0;

  private final AtomicInteger taskId = new AtomicInteger(-1);

  public SqlBoosterService(Plugin plugin, ZakumDatabase db, Executor async, ZakumSettings.Boosters settings) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.db = Objects.requireNonNull(db, "db");
    this.async = Objects.requireNonNull(async, "async");
    this.settings = Objects.requireNonNull(settings, "settings");
  }

  public void start() {
    // Load active boosters once (async) and then keep a lightweight periodic refresh.
    async.execute(this::refreshFromDb);

    long ticks = Math.max(10, settings.refreshSeconds()) * 20L;
    int id = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refreshFromDb, ticks, ticks).getTaskId();
    taskId.set(id);
  }

  public void shutdown() {
    int id = taskId.getAndSet(-1);
    if (id != -1) Bukkit.getScheduler().cancelTask(id);

    allMult = Map.of();
    playerMult = Map.of();
  }

  /** Best-effort immediate refresh (admin/ops tooling). */
  public void refreshNowAsync() {
    async.execute(this::refreshFromDb);
  }

  @Override
  public double multiplier(UUID playerId, EntitlementScope scope, String serverId, BoosterKind kind) {
    Objects.requireNonNull(playerId, "playerId");
    Objects.requireNonNull(scope, "scope");
    Objects.requireNonNull(kind, "kind");

    String sId = (scope == EntitlementScope.NETWORK) ? null : Objects.requireNonNull(serverId, "serverId");

    // Snapshot volatile maps once per call.
    Map<Key, Double> a = this.allMult;
    Map<PlayerKey, Double> p = this.playerMult;

    double mult = 1.0;

    if (scope == EntitlementScope.NETWORK) {
      mult *= a.getOrDefault(new Key(EntitlementScope.NETWORK, null, kind.name()), 1.0);
      mult *= p.getOrDefault(new PlayerKey(playerId, EntitlementScope.NETWORK, null, kind.name()), 1.0);
    } else {
      // SERVER context includes NETWORK boosters (global boosters apply everywhere).
      mult *= a.getOrDefault(new Key(EntitlementScope.SERVER, sId, kind.name()), 1.0);
      mult *= p.getOrDefault(new PlayerKey(playerId, EntitlementScope.SERVER, sId, kind.name()), 1.0);

      mult *= a.getOrDefault(new Key(EntitlementScope.NETWORK, null, kind.name()), 1.0);
      mult *= p.getOrDefault(new PlayerKey(playerId, EntitlementScope.NETWORK, null, kind.name()), 1.0);
    }

    if (mult < 0.0) mult = 1.0;
    if (mult > MAX_MULT) mult = MAX_MULT;
    return mult;
  }

  @Override
  public CompletableFuture<Void> grantToAll(EntitlementScope scope, String serverId, BoosterKind kind, double multiplier, long durationSeconds) {
    Objects.requireNonNull(scope, "scope");
    Objects.requireNonNull(kind, "kind");

    String sId = (scope == EntitlementScope.NETWORK) ? null : Objects.requireNonNull(serverId, "serverId");

    return CompletableFuture.runAsync(() -> {
      ensureOnline();

      long expiresAt = Instant.now().getEpochSecond() + Math.max(1, durationSeconds);
      double mult = sanitize(multiplier);

      db.jdbc().update(
        "INSERT INTO zakum_boosters (scope, server_id, target, uuid, kind, multiplier, expires_at) VALUES (?,?,?,?,?,?,?)",
        scope.name(), sId, "ALL", null, kind.name(), mult, expiresAt
      );

      refreshFromDb();
    }, async);
  }

  @Override
  public CompletableFuture<Void> grantToPlayer(UUID playerId, EntitlementScope scope, String serverId, BoosterKind kind, double multiplier, long durationSeconds) {
    Objects.requireNonNull(playerId, "playerId");
    Objects.requireNonNull(scope, "scope");
    Objects.requireNonNull(kind, "kind");

    String sId = (scope == EntitlementScope.NETWORK) ? null : Objects.requireNonNull(serverId, "serverId");

    return CompletableFuture.runAsync(() -> {
      ensureOnline();

      long expiresAt = Instant.now().getEpochSecond() + Math.max(1, durationSeconds);
      double mult = sanitize(multiplier);

      db.jdbc().update(
        "INSERT INTO zakum_boosters (scope, server_id, target, uuid, kind, multiplier, expires_at) VALUES (?,?,?,?,?,?,?)",
        scope.name(), sId, "PLAYER", UuidBytes.toBytes(playerId), kind.name(), mult, expiresAt
      );

      refreshFromDb();
    }, async);
  }

  private void ensureOnline() {
    if (db.state() != DatabaseState.ONLINE) {
      throw new IllegalStateException("DB is offline");
    }
  }

  private static double sanitize(double multiplier) {
    if (Double.isNaN(multiplier) || Double.isInfinite(multiplier)) return 1.0;
    if (multiplier < 0.01) return 0.01;
    if (multiplier > MAX_MULT) return MAX_MULT;
    return multiplier;
  }

  private void refreshFromDb() {
    if (db.state() != DatabaseState.ONLINE) return;

    long now = Instant.now().getEpochSecond();

    // Prevent long-uptime table bloat.
    maybePurgeExpired(now);

    var rows = db.jdbc().query(
      "SELECT scope, server_id, target, uuid, kind, multiplier FROM zakum_boosters WHERE expires_at > ?",
      rs -> new Row(
        rs.getString(1),
        rs.getString(2),
        rs.getString(3),
        rs.getBytes(4),
        rs.getString(5),
        rs.getDouble(6)
      ),
      now
    );

    Map<Key, Double> newAll = new HashMap<>();
    Map<PlayerKey, Double> newPlayer = new HashMap<>();

    for (Row r : rows) {
      EntitlementScope scope = EntitlementScope.valueOf(r.scope);
      String sId = r.serverId; // null ok
      String kind = r.kind;

      if ("ALL".equalsIgnoreCase(r.target)) {
        var k = new Key(scope, sId, kind);
        newAll.put(k, product(newAll.get(k), r.multiplier));
      } else {
        if (r.uuid == null || r.uuid.length != 16) continue;
        UUID uuid = UuidBytes.fromBytes(r.uuid);

        var pk = new PlayerKey(uuid, scope, sId, kind);
        newPlayer.put(pk, product(newPlayer.get(pk), r.multiplier));
      }
    }

    this.allMult = Map.copyOf(newAll);
    this.playerMult = Map.copyOf(newPlayer);
  }

  // ------------------------------------------------------------
  // Admin/ops helpers (kept core-only for now)

  public record AdminRow(
    EntitlementScope scope,
    String serverId,
    String target,
    UUID uuid,
    BoosterKind kind,
    double multiplier,
    long expiresAtEpochSeconds,
    java.sql.Timestamp createdAt
  ) {}

  public CompletableFuture<List<AdminRow>> listActive(UUID playerFilter, BoosterKind kindFilter, int limit) {
    int lim = Math.max(1, Math.min(200, limit));
    return CompletableFuture.supplyAsync(() -> {
      ensureOnline();

      long now = Instant.now().getEpochSecond();
      StringBuilder sql = new StringBuilder(
        "SELECT scope, server_id, target, uuid, kind, multiplier, expires_at, created_at FROM zakum_boosters WHERE expires_at > ?"
      );
      List<Object> params = new java.util.ArrayList<>();
      params.add(now);

      if (kindFilter != null) {
        sql.append(" AND kind=?");
        params.add(kindFilter.name());
      }
      if (playerFilter != null) {
        sql.append(" AND (target='ALL' OR uuid=?)");
        params.add(UuidBytes.toBytes(playerFilter));
      }

      sql.append(" ORDER BY expires_at ASC LIMIT ").append(lim);

      return db.jdbc().query(sql.toString(), rs -> new AdminRow(
        EntitlementScope.valueOf(rs.getString(1)),
        rs.getString(2),
        rs.getString(3),
        rs.getBytes(4) == null ? null : UuidBytes.fromBytes(rs.getBytes(4)),
        BoosterKind.valueOf(rs.getString(5)),
        rs.getDouble(6),
        rs.getLong(7),
        rs.getTimestamp(8)
      ), params.toArray());
    }, async);
  }

  public CompletableFuture<Integer> clearAll(EntitlementScope scope, String serverId, BoosterKind kindFilter) {
    Objects.requireNonNull(scope, "scope");
    String sId = (scope == EntitlementScope.NETWORK) ? null : Objects.requireNonNull(serverId, "serverId");
    return CompletableFuture.supplyAsync(() -> {
      ensureOnline();

      StringBuilder sql = new StringBuilder("DELETE FROM zakum_boosters WHERE target='ALL' AND scope=?");
      List<Object> params = new java.util.ArrayList<>();
      params.add(scope.name());

      if (scope == EntitlementScope.NETWORK) {
        sql.append(" AND server_id IS NULL");
      } else {
        sql.append(" AND server_id=?");
        params.add(sId);
      }
      if (kindFilter != null) {
        sql.append(" AND kind=?");
        params.add(kindFilter.name());
      }

      int deleted = db.jdbc().update(sql.toString(), params.toArray());
      refreshFromDb();
      return deleted;
    }, async);
  }

  public CompletableFuture<Integer> clearPlayer(UUID playerId, EntitlementScope scope, String serverId, BoosterKind kindFilter) {
    Objects.requireNonNull(playerId, "playerId");
    Objects.requireNonNull(scope, "scope");
    String sId = (scope == EntitlementScope.NETWORK) ? null : Objects.requireNonNull(serverId, "serverId");
    return CompletableFuture.supplyAsync(() -> {
      ensureOnline();

      StringBuilder sql = new StringBuilder("DELETE FROM zakum_boosters WHERE target='PLAYER' AND uuid=? AND scope=?");
      List<Object> params = new java.util.ArrayList<>();
      params.add(UuidBytes.toBytes(playerId));
      params.add(scope.name());

      if (scope == EntitlementScope.NETWORK) {
        sql.append(" AND server_id IS NULL");
      } else {
        sql.append(" AND server_id=?");
        params.add(sId);
      }
      if (kindFilter != null) {
        sql.append(" AND kind=?");
        params.add(kindFilter.name());
      }

      int deleted = db.jdbc().update(sql.toString(), params.toArray());
      refreshFromDb();
      return deleted;
    }, async);
  }

  private void maybePurgeExpired(long nowEpochSeconds) {
    var purge = settings.purge();
    if (!purge.enabled()) return;

    long last = lastPurgeEpochSeconds;
    long interval = Math.max(60, purge.intervalSeconds());
    if (last != 0 && (nowEpochSeconds - last) < interval) return;

    // Best-effort; no need for a hard lock.
    lastPurgeEpochSeconds = nowEpochSeconds;
    purgeExpired(nowEpochSeconds, Math.max(100, purge.deleteLimit()));
  }

  private void purgeExpired(long nowEpochSeconds, int deleteLimit) {
    // Bound worst-case work: multiple small deletes are easier on InnoDB.
    final int maxLoops = 20;
    for (int i = 0; i < maxLoops; i++) {
      int deleted = db.jdbc().update(
        "DELETE FROM zakum_boosters WHERE expires_at <= ? LIMIT " + deleteLimit,
        nowEpochSeconds
      );
      if (deleted < deleteLimit) break;
    }
  }

  private static double product(Double a, double b) {
    double x = (a == null) ? 1.0 : a;
    x *= sanitize(b);
    if (x > MAX_MULT) x = MAX_MULT;
    return x;
  }

  private record Row(String scope, String serverId, String target, byte[] uuid, String kind, double multiplier) {}

  private record Key(EntitlementScope scope, String serverId, String kind) {}

  private record PlayerKey(UUID uuid, EntitlementScope scope, String serverId, String kind) {}
}
