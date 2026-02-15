package net.orbis.zakum.core.ops;

import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.api.config.ZakumSettings;
import net.orbis.zakum.api.db.DatabaseState;
import net.orbis.zakum.api.db.Jdbc;
import net.orbis.zakum.core.metrics.MetricsMonitor;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Cross-module SQL health probes (schema + read/write capability).
 */
public final class ModuleDataHealthProber {

  private final ZakumApi api;
  private final ZakumSettings.Operations.DataHealthProbes cfg;
  private final MetricsMonitor metrics;
  private final Logger logger;
  private final List<ProbeSpec> probes;

  public ModuleDataHealthProber(
    ZakumApi api,
    ZakumSettings.Operations.DataHealthProbes cfg,
    MetricsMonitor metrics,
    Logger logger
  ) {
    this.api = Objects.requireNonNull(api, "api");
    this.cfg = Objects.requireNonNull(cfg, "cfg");
    this.metrics = metrics;
    this.logger = Objects.requireNonNull(logger, "logger");
    this.probes = buildProbes();
  }

  public Report run() {
    long started = System.currentTimeMillis();
    if (!cfg.enabled()) {
      return Report.disabled(started);
    }
    if (api.database().state() != DatabaseState.ONLINE) {
      return Report.offline(started, "database_offline");
    }

    Jdbc jdbc = api.database().jdbc();
    List<ProbeResult> results = new ArrayList<>();
    int pass = 0;
    int fail = 0;
    int skipped = 0;

    for (ProbeSpec spec : probes) {
      if (spec == null) continue;
      long probeStart = System.currentTimeMillis();
      String requiredPlugin = spec.requiredPlugin();
      if (requiredPlugin != null && !requiredPlugin.isBlank() && !Bukkit.getPluginManager().isPluginEnabled(requiredPlugin)) {
        skipped++;
        results.add(new ProbeResult(
          spec.key(),
          requiredPlugin,
          State.SKIPPED,
          "plugin_offline",
          System.currentTimeMillis() - probeStart
        ));
        continue;
      }

      try {
        ProbeOutcome outcome = spec.runner().run(jdbc, cfg.includeWriteProbe());
        if (outcome.ok()) {
          pass++;
          results.add(new ProbeResult(
            spec.key(),
            requiredPlugin,
            State.PASS,
            sanitizeDetail(outcome.detail()),
            System.currentTimeMillis() - probeStart
          ));
        } else {
          fail++;
          results.add(new ProbeResult(
            spec.key(),
            requiredPlugin,
            State.FAIL,
            sanitizeDetail(outcome.detail()),
            System.currentTimeMillis() - probeStart
          ));
        }
      } catch (Throwable error) {
        fail++;
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        results.add(new ProbeResult(
          spec.key(),
          requiredPlugin,
          State.FAIL,
          sanitizeDetail(message),
          System.currentTimeMillis() - probeStart
        ));
      }
    }

    if (metrics != null) {
      metrics.recordAction("module_data_probe_run");
      if (fail > 0) metrics.recordAction("module_data_probe_fail");
    }
    if (fail > 0) {
      logger.warning("Module data probes reported failures: fail=" + fail + " pass=" + pass + " skipped=" + skipped);
    }
    long now = System.currentTimeMillis();
    return new Report(
      cfg.enabled(),
      cfg.includeWriteProbe(),
      true,
      probes.size(),
      pass,
      fail,
      skipped,
      now,
      now - started,
      List.copyOf(results),
      ""
    );
  }

  private List<ProbeSpec> buildProbes() {
    List<ProbeSpec> out = new ArrayList<>();
    out.add(new ProbeSpec(
      "core.flyway_schema",
      "Zakum",
      (jdbc, includeWrite) -> {
        Integer rank = jdbc.queryOne(
          "SELECT COALESCE(MAX(installed_rank), 0) FROM flyway_schema_history",
          rs -> rs.getInt(1)
        );
        String detail = "maxInstalledRank=" + (rank == null ? 0 : rank);
        return ProbeOutcome.pass(detail);
      }
    ));
    out.add(tableProbe("core.deferred_actions", "Zakum", "zakum_deferred_actions", "id"));
    out.add(tableProbe("core.entitlements", "Zakum", "zakum_entitlements", "expires_at"));
    out.add(tableProbe("core.boosters", "Zakum", "zakum_boosters", "expires_at"));
    out.add(tableProbe("battlepass.progress", "OrbisBattlePass", "orbis_battlepass_progress", "points"));
    out.add(tableProbe("crates.keys", "OrbisCrates", "orbis_crates_keys", "quantity"));
    out.add(tableProbe("pets.player", "OrbisPets", "orbis_pets_player", "lvl"));
    out.add(tableProbe("minipets.player", "OrbisMiniPets", "orbis_minipets_player", "ride"));
    out.add(tableProbe("essentials.users", "OrbisEssentials", "orbis_ess_users", "last_x"));
    return List.copyOf(out);
  }

  private static ProbeSpec tableProbe(String key, String plugin, String table, String writableColumn) {
    return new ProbeSpec(
      key,
      plugin,
      (jdbc, includeWrite) -> {
        boolean hasRows = !jdbc.query("SELECT 1 FROM " + table + " LIMIT 1", rs -> 1).isEmpty();
        if (includeWrite && writableColumn != null && !writableColumn.isBlank()) {
          jdbc.update("UPDATE " + table + " SET " + writableColumn + "=" + writableColumn + " WHERE 1=0");
        }
        String detail = "read=ok hasRows=" + hasRows + " write=" + (includeWrite ? "ok" : "skipped");
        return ProbeOutcome.pass(detail);
      }
    );
  }

  private static String sanitizeDetail(String detail) {
    if (detail == null || detail.isBlank()) return "";
    String clean = detail.trim().replace('\n', ' ').replace('\r', ' ');
    if (clean.length() <= 280) return clean;
    return clean.substring(0, 280) + "...";
  }

  public enum State {
    PASS,
    FAIL,
    SKIPPED
  }

  public record ProbeResult(
    String key,
    String requiredPlugin,
    State state,
    String detail,
    long latencyMs
  ) {
    public ProbeResult {
      key = key == null ? "unknown" : key.trim().toLowerCase(Locale.ROOT);
      requiredPlugin = requiredPlugin == null ? "" : requiredPlugin.trim();
      state = state == null ? State.FAIL : state;
      detail = detail == null ? "" : detail.trim();
      latencyMs = Math.max(0L, latencyMs);
    }
  }

  public record Report(
    boolean enabled,
    boolean includeWriteProbe,
    boolean databaseOnline,
    int total,
    int pass,
    int fail,
    int skipped,
    long generatedAtMs,
    long durationMs,
    List<ProbeResult> results,
    String error
  ) {
    public Report {
      total = Math.max(0, total);
      pass = Math.max(0, pass);
      fail = Math.max(0, fail);
      skipped = Math.max(0, skipped);
      durationMs = Math.max(0L, durationMs);
      results = results == null ? List.of() : List.copyOf(results);
      error = error == null ? "" : error.trim();
    }

    public static Report disabled(long generatedAtMs) {
      return new Report(false, false, false, 0, 0, 0, 0, generatedAtMs, 0L, List.of(), "");
    }

    public static Report offline(long generatedAtMs, String error) {
      return new Report(true, false, false, 0, 0, 0, 0, generatedAtMs, 0L, List.of(), error);
    }
  }

  private record ProbeSpec(
    String key,
    String requiredPlugin,
    ProbeRunner runner
  ) {}

  @FunctionalInterface
  private interface ProbeRunner {
    ProbeOutcome run(Jdbc jdbc, boolean includeWriteProbe);
  }

  private record ProbeOutcome(
    boolean ok,
    String detail
  ) {
    static ProbeOutcome pass(String detail) {
      return new ProbeOutcome(true, detail);
    }
  }
}
