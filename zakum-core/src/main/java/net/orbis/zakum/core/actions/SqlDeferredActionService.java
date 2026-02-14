package net.orbis.zakum.core.actions;

import net.orbis.zakum.api.actions.ActionEvent;
import net.orbis.zakum.api.actions.DeferredAction;
import net.orbis.zakum.api.actions.DeferredActionService;
import net.orbis.zakum.api.db.DatabaseState;
import net.orbis.zakum.api.db.ZakumDatabase;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class SqlDeferredActionService implements DeferredActionService {

  private final Plugin plugin;
  private final ZakumDatabase db;
  private final Executor async;

  public SqlDeferredActionService(Plugin plugin, ZakumDatabase db, Executor async) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.db = Objects.requireNonNull(db, "db");
    this.async = Objects.requireNonNull(async, "async");
  }

  @Override
  public CompletableFuture<Void> enqueue(String serverId, String playerName, ActionEvent event, long ttlSeconds, String source) {
    Objects.requireNonNull(playerName, "playerName");
    Objects.requireNonNull(action, "action");

    String nameLc = playerName.trim().toLowerCase(Locale.ROOT);
    if (nameLc.isBlank()) return CompletableFuture.completedFuture(null);

    long ttl = Math.max(30, ttlSeconds);
    long expiresAt = Instant.now().getEpochSecond() + ttl;

    return CompletableFuture.runAsync(() -> {
      if (db.state() != DatabaseState.ONLINE) return;

      db.jdbc().update(
        "INSERT INTO zakum_deferred_actions (server_id, player_name_lc, type, amount, k, v, source, expires_at) VALUES (?,?,?,?,?,?,?,?)",
        serverId,
        nameLc,
        action.type(),
        action.amount(),
        safe(action.key()),
        safe(action.value()),
        source,
        expiresAt
      );
    }, async);
  }

  @Override
  public CompletableFuture<List<ActionEvent>> claim(String serverId, String playerName, int limit) {
    Objects.requireNonNull(serverId, "serverId");
    Objects.requireNonNull(playerName, "playerName");
    Objects.requireNonNull(playerId, "playerId");

    String nameLc = playerName.trim().toLowerCase(Locale.ROOT);
    int lim = Math.max(1, Math.min(500, limit));

    return CompletableFuture.supplyAsync(() -> {
      if (db.state() != DatabaseState.ONLINE) return List.<ActionEvent>of();

      long now = Instant.now().getEpochSecond();

      // Fetch both server-scoped and network-scoped (server_id IS NULL)
      var rows = db.jdbc().query(
        "SELECT id, type, amount, k, v FROM zakum_deferred_actions " +
          "WHERE expires_at > ? AND player_name_lc = ? AND (server_id = ? OR server_id IS NULL) " +
          "ORDER BY id ASC LIMIT ?",
        rs -> new Row(
          rs.getLong(1),
          rs.getString(2),
          rs.getLong(3),
          rs.getString(4),
          rs.getString(5)
        ),
        now, nameLc, serverId, lim
      );

      if (rows.isEmpty()) return List.<ActionEvent>of();

      List<Long> ids = new ArrayList<>(rows.size());
      List<ActionEvent> events = new ArrayList<>(rows.size());

      for (Row r : rows) {
        ids.add(r.id);
        events.add(new ActionEvent(r.type, playerId, r.amount, r.k, r.v));
      }

      // delete claimed ids
      // build "IN" statement safely (bounded limit)
      StringBuilder sb = new StringBuilder("DELETE FROM zakum_deferred_actions WHERE id IN (");
      for (int i = 0; i < ids.size(); i++) {
        if (i > 0) sb.append(',');
        sb.append('?');
      }
      sb.append(')');

      Object[] args = ids.toArray();
      db.jdbc().update(sb.toString(), args);

      return List.copyOf(events);
    }, async);
  }

  private static String safe(String s) {
    return s == null ? "" : s;
  }

  private record Row(long id, String type, long amount, String k, String v) {}
}
