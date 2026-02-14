package net.orbis.zakum.core.ops;

import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.api.action.AceEngine;
import net.orbis.zakum.api.capability.ZakumCapabilities;
import net.orbis.zakum.api.concurrent.ZakumScheduler;
import net.orbis.zakum.api.config.ZakumSettings;
import net.orbis.zakum.api.vault.EconomyService;
import net.orbis.zakum.core.metrics.MetricsMonitor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

/**
 * Stress Harness v2: synthetic actor runner with ACE execution matrix and safety gates.
 */
public final class StressHarnessV2 implements AutoCloseable {

  private static final int TICKS_PER_SECOND = 20;

  private final Plugin plugin;
  private final ZakumApi api;
  private final ZakumScheduler scheduler;
  private final ZakumSettings.Operations.Stress cfg;
  private final MetricsMonitor metrics;
  private final Logger logger;

  private final List<Scenario> baseScenarios;
  private final Map<String, LongAdder> scenarioCounts;

  private final AtomicLong actorCursor;
  private final AtomicLong scheduledIterations;
  private final AtomicLong completedIterations;
  private final AtomicLong errorCount;
  private final AtomicLong skippedNoPlayer;
  private final AtomicInteger lowTpsTicks;

  private volatile List<VirtualActor> actors;
  private volatile List<Scenario> scenarios;
  private volatile int scenarioWeightTotal;
  private volatile List<Player> onlineSnapshot;

  private volatile boolean running;
  private volatile int taskId;
  private volatile long startedAtMs;
  private volatile long stopAtMs;
  private volatile long nextAllowedAtMs;
  private volatile String lastStopReason;
  private volatile String lastError;
  private volatile long lastErrorAtMs;
  private volatile int plannedIterations;
  private volatile int virtualPlayers;
  private volatile int iterationsPerTick;
  private volatile double lastTps;

  public StressHarnessV2(
    Plugin plugin,
    ZakumApi api,
    ZakumSettings.Operations.Stress cfg,
    MetricsMonitor metrics,
    Logger logger
  ) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.api = Objects.requireNonNull(api, "api");
    this.scheduler = Objects.requireNonNull(api.getScheduler(), "scheduler");
    this.cfg = Objects.requireNonNull(cfg, "cfg");
    this.metrics = metrics;
    this.logger = logger;
    this.baseScenarios = buildDefaultScenarios();
    this.scenarioCounts = new ConcurrentHashMap<>();

    this.actorCursor = new AtomicLong();
    this.scheduledIterations = new AtomicLong();
    this.completedIterations = new AtomicLong();
    this.errorCount = new AtomicLong();
    this.skippedNoPlayer = new AtomicLong();
    this.lowTpsTicks = new AtomicInteger();

    this.actors = List.of();
    this.scenarios = List.of();
    this.scenarioWeightTotal = 0;
    this.onlineSnapshot = List.of();

    this.running = false;
    this.taskId = -1;
    this.startedAtMs = 0L;
    this.stopAtMs = 0L;
    this.nextAllowedAtMs = 0L;
    this.lastStopReason = "never";
    this.lastError = null;
    this.lastErrorAtMs = 0L;
    this.plannedIterations = 0;
    this.virtualPlayers = 0;
    this.iterationsPerTick = 0;
    this.lastTps = 20.0d;
  }

  public StartResult start(int requestedIterations, Integer virtualPlayersOverride) {
    if (!cfg.enabled()) return new StartResult(false, "Stress harness is disabled in config.");
    if (running) return new StartResult(false, "Stress harness already running.");

    long now = System.currentTimeMillis();
    if (now < nextAllowedAtMs) {
      long waitMs = nextAllowedAtMs - now;
      return new StartResult(false, "Cooldown active. Wait " + Math.max(1L, (waitMs + 999L) / 1000L) + "s.");
    }

    List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
    if (online.size() < cfg.minOnlinePlayers()) {
      return new StartResult(false, "Need at least " + cfg.minOnlinePlayers() + " online players.");
    }

    int iterations = requestedIterations <= 0 ? cfg.defaultIterations() : requestedIterations;
    iterations = Math.max(1, Math.min(iterations, cfg.maxIterations()));

    int virtualCount = virtualPlayersOverride == null ? cfg.virtualPlayers() : Math.max(1, virtualPlayersOverride);
    if (virtualCount < online.size()) {
      virtualCount = online.size();
    }

    List<Scenario> activeScenarios = selectScenarios();
    if (activeScenarios.isEmpty()) {
      return new StartResult(false, "No stress scenarios available after filters.");
    }

    this.actors = buildActors(virtualCount);
    this.scenarios = activeScenarios;
    this.scenarioWeightTotal = activeScenarios.stream().mapToInt(Scenario::weight).sum();
    this.onlineSnapshot = List.copyOf(online);
    this.virtualPlayers = virtualCount;
    this.iterationsPerTick = cfg.iterationsPerTick();
    this.plannedIterations = iterations;
    this.actorCursor.set(0);
    this.scheduledIterations.set(0);
    this.completedIterations.set(0);
    this.errorCount.set(0);
    this.skippedNoPlayer.set(0);
    this.lowTpsTicks.set(0);
    this.lastError = null;
    this.lastErrorAtMs = 0L;
    this.startedAtMs = now;
    this.stopAtMs = now + Math.max(1L, cfg.maxDurationSeconds()) * 1000L;
    this.lastStopReason = "running";
    this.running = true;

    scenarioCounts.clear();
    for (Scenario scenario : activeScenarios) {
      scenarioCounts.put(scenario.name(), new LongAdder());
    }

    if (taskId > 0) scheduler.cancelTask(taskId);
    taskId = scheduler.runTaskTimer(plugin, this::tick, 1L, 1L);

    if (metrics != null) metrics.recordAction("stress_v2_start");
    if (logger != null) {
      logger.info("Stress v2 started iterations=" + iterations + " virtualPlayers=" + virtualCount + " perTick=" + iterationsPerTick);
    }
    return new StartResult(true, "Stress v2 started. iterations=" + iterations + " virtualPlayers=" + virtualCount);
  }

  public StopResult stop(String reason) {
    if (!running) return new StopResult(false, "Stress harness is not running.");
    String finalReason = reason == null || reason.isBlank() ? "stopped" : reason;
    endRun(finalReason);
    return new StopResult(true, "Stress v2 stopped (" + finalReason + ").");
  }

  public Snapshot snapshot() {
    long now = System.currentTimeMillis();
    long scheduled = scheduledIterations.get();
    long completed = completedIterations.get();
    Map<String, Long> scenarioStats = new LinkedHashMap<>();
    for (Map.Entry<String, LongAdder> entry : scenarioCounts.entrySet()) {
      scenarioStats.put(entry.getKey(), entry.getValue().sum());
    }
    return new Snapshot(
      running,
      plannedIterations,
      scheduled,
      completed,
      errorCount.get(),
      skippedNoPlayer.get(),
      virtualPlayers,
      iterationsPerTick,
      onlineSnapshot.size(),
      lastTps,
      startedAtMs,
      stopAtMs,
      lastStopReason,
      nextAllowedAtMs,
      scenarioStats,
      lastError,
      lastErrorAtMs,
      now
    );
  }

  @Override
  public void close() {
    if (running) {
      endRun("shutdown");
    }
  }

  private void tick() {
    if (!running) return;

    long now = System.currentTimeMillis();
    if (now >= stopAtMs) {
      endRun("timeout");
      return;
    }

    List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
    onlineSnapshot = List.copyOf(online);
    if (online.size() < cfg.minOnlinePlayers()) {
      endRun("offline");
      return;
    }

    lastTps = currentTps();
    if (lastTps < cfg.minTps()) {
      int ticks = lowTpsTicks.incrementAndGet();
      int maxTicks = Math.max(1, cfg.abortBelowTpsSeconds()) * TICKS_PER_SECOND;
      if (ticks >= maxTicks) {
        endRun("tps");
        return;
      }
    } else {
      lowTpsTicks.set(0);
    }

    if (cfg.maxErrors() > 0 && errorCount.get() >= cfg.maxErrors()) {
      endRun("errors");
      return;
    }

    long planned = plannedIterations;
    long completed = completedIterations.get();
    if (completed >= planned) {
      endRun("completed");
      return;
    }

    long scheduled = scheduledIterations.get();
    long remaining = planned - scheduled;
    if (remaining <= 0L) return;

    int toRun = (int) Math.min(iterationsPerTick, remaining);
    for (int i = 0; i < toRun; i++) {
      scheduleIteration(online);
    }

    if (metrics != null) metrics.recordAction("stress_v2_tick");
  }

  private void scheduleIteration(List<Player> online) {
    if (online == null || online.isEmpty()) {
      skippedNoPlayer.incrementAndGet();
      return;
    }

    long cursor = actorCursor.getAndIncrement();
    VirtualActor actor = actors.get((int) Math.floorMod(cursor, actors.size()));
    Player player = online.get((int) Math.floorMod(cursor, online.size()));
    if (player == null || !player.isOnline()) {
      skippedNoPlayer.incrementAndGet();
      return;
    }

    Scenario scenario = pickScenario(ThreadLocalRandom.current());
    if (scenario == null) return;

    scheduledIterations.incrementAndGet();
    scheduler.runAsync(() -> executeScenario(player, actor, scenario));
  }

  private void executeScenario(Player player, VirtualActor actor, Scenario scenario) {
    if (player == null || !player.isOnline()) {
      skippedNoPlayer.incrementAndGet();
      completedIterations.incrementAndGet();
      return;
    }

    AceEngine engine = api.getAceEngine();
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("actor", actor.name());
    metadata.put("actor_id", actor.id().toString());
    metadata.put("actor_index", actor.index());
    metadata.put("virtual_players", actors.size());
    metadata.put("iteration", scheduledIterations.get());

    try {
      engine.executeScript(scenario.script(), new AceEngine.ActionContext(player, Optional.empty(), metadata));
      LongAdder counter = scenarioCounts.get(scenario.name());
      if (counter != null) counter.increment();
      if (metrics != null) metrics.recordAction("stress_v2_script");
    } catch (Throwable t) {
      errorCount.incrementAndGet();
      lastError = t.getMessage();
      lastErrorAtMs = System.currentTimeMillis();
      if (metrics != null) metrics.recordAction("stress_v2_error");
    } finally {
      completedIterations.incrementAndGet();
    }
  }

  private Scenario pickScenario(ThreadLocalRandom random) {
    if (scenarios.isEmpty() || scenarioWeightTotal <= 0) return null;
    int roll = random.nextInt(scenarioWeightTotal);
    int acc = 0;
    for (Scenario scenario : scenarios) {
      acc += scenario.weight();
      if (roll < acc) return scenario;
    }
    return scenarios.get(0);
  }

  private void endRun(String reason) {
    if (!running) return;
    running = false;
    if (taskId > 0) {
      scheduler.cancelTask(taskId);
      taskId = -1;
    }
    long now = System.currentTimeMillis();
    lastStopReason = reason == null ? "stopped" : reason;
    nextAllowedAtMs = now + Math.max(0L, cfg.cooldownSeconds()) * 1000L;

    if (metrics != null) metrics.recordAction("stress_v2_stop_" + lastStopReason);
    if (logger != null) {
      long completed = completedIterations.get();
      long planned = plannedIterations;
      long errors = errorCount.get();
      Duration elapsed = Duration.ofMillis(Math.max(0L, now - startedAtMs));
      logger.info("Stress v2 stopped reason=" + lastStopReason
        + " completed=" + completed + "/" + planned
        + " errors=" + errors
        + " elapsed=" + formatDuration(elapsed));
    }
  }

  private List<Scenario> selectScenarios() {
    boolean economyAvailable = api.capability(ZakumCapabilities.ECONOMY)
      .map(EconomyService::available)
      .orElse(false);
    List<Scenario> out = new ArrayList<>();
    for (Scenario scenario : baseScenarios) {
      if (!cfg.allowEconomy() && scenario.tags().contains(Tag.ECONOMY)) continue;
      if (!cfg.allowRtp() && scenario.tags().contains(Tag.RTP)) continue;
      if (!cfg.allowChat() && scenario.tags().contains(Tag.CHAT)) continue;
      if (!cfg.allowVisuals() && scenario.tags().contains(Tag.VISUAL)) continue;
      if (!economyAvailable && scenario.tags().contains(Tag.ECONOMY)) continue;
      out.add(scenario);
    }
    return List.copyOf(out);
  }

  private static List<VirtualActor> buildActors(int count) {
    int total = Math.max(1, count);
    List<VirtualActor> out = new ArrayList<>(total);
    for (int i = 0; i < total; i++) {
      int index = i + 1;
      String name = "stress_" + index;
      UUID id = UUID.nameUUIDFromBytes(("stress-" + index).getBytes(StandardCharsets.UTF_8));
      out.add(new VirtualActor(index, id, name));
    }
    return List.copyOf(out);
  }

  private static List<Scenario> buildDefaultScenarios() {
    List<Scenario> out = new ArrayList<>();
    out.add(new Scenario(
      "chat_key",
      6,
      List.of("[MESSAGE_KEY] key=battlepass_points_gain points=1"),
      tags(Tag.CHAT)
    ));
    out.add(new Scenario(
      "actionbar_key",
      6,
      List.of("[ACTION_BAR_KEY] key=battlepass_points_gain points=1"),
      tags(Tag.CHAT)
    ));
    out.add(new Scenario(
      "visual_pulse",
      4,
      List.of(
        "[PARTICLE] VILLAGER_HAPPY {count=6 dx=0.2 dy=0.4 dz=0.2 speed=0.01}",
        "[SOUND] ENTITY_EXPERIENCE_ORB_PICKUP {volume=0.5 pitch=1.2}"
      ),
      tags(Tag.VISUAL)
    ));
    out.add(new Scenario(
      "aoe_particles",
      3,
      List.of("[PARTICLE] FLAME @NEARBY {radius=5 count=8 dx=0.2 dy=0.3 dz=0.2 speed=0.01}"),
      tags(Tag.VISUAL)
    ));
    out.add(new Scenario(
      "economy_tick",
      3,
      List.of("[GIVE_MONEY] amount=1"),
      tags(Tag.ECONOMY)
    ));
    out.add(new Scenario(
      "xp_tick",
      3,
      List.of("[GIVE_XP] amount=2"),
      tags()
    ));
    out.add(new Scenario(
      "rtp_economy_combo",
      1,
      List.of(
        "[GIVE_MONEY] amount=1",
        "[RTP] @SELF {min=64 max=384}",
        "[ACTION_BAR_KEY] key=battlepass_points_gain points=1"
      ),
      tags(Tag.ECONOMY, Tag.RTP, Tag.CHAT)
    ));
    return List.copyOf(out);
  }

  private static Set<Tag> tags(Tag... values) {
    if (values == null || values.length == 0) return EnumSet.noneOf(Tag.class);
    EnumSet<Tag> set = EnumSet.noneOf(Tag.class);
    for (Tag value : values) {
      if (value != null) set.add(value);
    }
    return set;
  }

  private static double currentTps() {
    try {
      double[] tps = Bukkit.getTPS();
      if (tps == null || tps.length == 0) return 20.0d;
      double oneMinute = tps[0];
      if (Double.isFinite(oneMinute) && oneMinute > 0.0d) {
        return oneMinute;
      }
    } catch (Throwable ignored) {
      // Fallback to healthy default.
    }
    return 20.0d;
  }

  private static String formatDuration(Duration duration) {
    if (duration == null) return "0s";
    long seconds = Math.max(0L, duration.getSeconds());
    long minutes = seconds / 60L;
    long rem = seconds % 60L;
    if (minutes <= 0L) return rem + "s";
    return minutes + "m" + rem + "s";
  }

  public record StartResult(boolean started, String message) {}

  public record StopResult(boolean stopped, String message) {}

  public record Snapshot(
    boolean running,
    int plannedIterations,
    long scheduledIterations,
    long completedIterations,
    long errorCount,
    long skippedNoPlayer,
    int virtualPlayers,
    int iterationsPerTick,
    int onlinePlayers,
    double lastTps,
    long startedAtMs,
    long stopAtMs,
    String stopReason,
    long nextAllowedAtMs,
    Map<String, Long> scenarioCounts,
    String lastError,
    long lastErrorAtMs,
    long snapshotAtMs
  ) {}

  private enum Tag {
    RTP,
    ECONOMY,
    CHAT,
    VISUAL
  }

  private record Scenario(
    String name,
    int weight,
    List<String> script,
    Set<Tag> tags
  ) {
    private Scenario {
      String safeName = name == null || name.isBlank() ? "scenario" : name.trim();
      name = safeName;
      weight = Math.max(1, weight);
      script = script == null ? List.of() : List.copyOf(script);
      tags = tags == null ? Set.of() : Set.copyOf(tags);
    }
  }

  private record VirtualActor(int index, UUID id, String name) {}
}
