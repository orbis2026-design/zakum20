package net.orbis.zakum.core.perf;

import net.orbis.zakum.api.concurrent.ZakumScheduler;
import net.orbis.zakum.api.config.ZakumSettings;
import net.orbis.zakum.api.packets.PacketDirection;
import net.orbis.zakum.api.packets.PacketHook;
import net.orbis.zakum.api.packets.PacketHookPriority;
import net.orbis.zakum.api.packets.PacketService;
import net.orbis.zakum.core.metrics.MetricsMonitor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

/**
 * Packet-side visual culling kernel.
 *
 * Sampling is Folia-safe:
 * - density is read on each player's region thread
 * - packet hook only reads sampled values (no Bukkit world access on Netty)
 */
public final class PacketCullingKernel implements AutoCloseable {

  private final Plugin plugin;
  private final ZakumScheduler scheduler;
  private final ZakumSettings.Packets.Culling cfg;
  private final MetricsMonitor metrics;
  private final Logger logger;
  private final PlayerVisualModeService visualModeService;
  private final String bypassPermission;
  private final boolean respectPerfMode;
  private final PacketHook outboundHook;
  private final ConcurrentHashMap<UUID, DensitySample> densitySamples;
  private final AtomicInteger sampleCursor;
  private final LongAdder sampleRuns;
  private final LongAdder sampleUpdates;
  private final LongAdder packetsObserved;
  private final LongAdder packetsDropped;
  private final LongAdder serviceProbeRuns;
  private final LongAdder bypassSkips;
  private final LongAdder qualitySkips;

  private volatile int lastSampleBatch;
  private volatile int lastOnlineCount;

  private volatile PacketService packetService;
  private volatile boolean runtimeEnabled;
  private volatile boolean hookRegistered;
  private volatile int sampleTaskId;
  private volatile int probeTaskId;

  public PacketCullingKernel(
    Plugin plugin,
    ZakumScheduler scheduler,
    ZakumSettings.Packets.Culling cfg,
    MetricsMonitor metrics,
    Logger logger,
    PlayerVisualModeService visualModeService
  ) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    this.cfg = Objects.requireNonNull(cfg, "cfg");
    this.metrics = metrics;
    this.logger = logger;
    this.visualModeService = visualModeService;
    this.bypassPermission = cfg.bypassPermission();
    this.respectPerfMode = cfg.respectPerfMode();
    this.outboundHook = new PacketHook(
      PacketDirection.OUTBOUND,
      PacketHookPriority.HIGH,
      cfg.packetNames(),
      this::handleOutbound
    );
    this.densitySamples = new ConcurrentHashMap<>();
    this.sampleCursor = new AtomicInteger();
    this.sampleRuns = new LongAdder();
    this.sampleUpdates = new LongAdder();
    this.packetsObserved = new LongAdder();
    this.packetsDropped = new LongAdder();
    this.serviceProbeRuns = new LongAdder();
    this.bypassSkips = new LongAdder();
    this.qualitySkips = new LongAdder();
    this.lastSampleBatch = 0;
    this.lastOnlineCount = 0;
    this.runtimeEnabled = cfg.enabled();
    this.hookRegistered = false;
    this.sampleTaskId = -1;
    this.probeTaskId = -1;
  }

  public void start() {
    // Keep sampler running even when disabled so runtime toggles have fresh data.
    if (sampleTaskId <= 0) {
      sampleTaskId = scheduler.runTaskTimer(
        plugin,
        this::sampleOnlinePlayers,
        Math.max(1L, cfg.sampleTicks()),
        Math.max(1L, cfg.sampleTicks())
      );
    }
    if (probeTaskId <= 0) {
      probeTaskId = scheduler.runTaskTimer(plugin, this::probePacketService, 20L, 100L);
    }
    scheduler.runGlobal(this::probePacketService);
  }

  public void setRuntimeEnabled(boolean enabled) {
    this.runtimeEnabled = enabled;
  }

  public boolean runtimeEnabled() {
    return runtimeEnabled;
  }

  public void requestSample(Player player) {
    if (player == null || scheduler == null) return;
    scheduler.runAtEntity(player, () -> samplePlayerDensity(player));
  }

  public SampleSnapshot sampleSnapshot(UUID playerId) {
    if (playerId == null) return null;
    DensitySample sample = densitySamples.get(playerId);
    if (sample == null) return null;
    return new SampleSnapshot(sample.density(), sample.sampledAtMs(), sample.mode(), sample.bypass());
  }

  public int thresholdFor(PlayerVisualModeService.Mode mode) {
    return cullThreshold(mode);
  }

  public Snapshot snapshot() {
    PacketService service = this.packetService;
    long observed = packetsObserved.sum();
    long dropped = packetsDropped.sum();
    double dropRate = observed <= 0L ? 0.0d : (double) dropped / (double) observed;
    return new Snapshot(
      runtimeEnabled,
      cfg.enabled(),
      hookRegistered,
      service == null ? "none" : service.backend(),
      service == null ? 0 : service.hookCount(),
      cfg.radius(),
      cfg.densityThreshold(),
      cfg.maxSampleAgeMs(),
      lastOnlineCount,
      lastSampleBatch,
      respectPerfMode,
      bypassPermission,
      densitySamples.size(),
      sampleRuns.sum(),
      sampleUpdates.sum(),
      serviceProbeRuns.sum(),
      bypassSkips.sum(),
      qualitySkips.sum(),
      observed,
      dropped,
      dropRate
    );
  }

  @Override
  public void close() {
    if (sampleTaskId > 0) {
      scheduler.cancelTask(sampleTaskId);
      sampleTaskId = -1;
    }
    if (probeTaskId > 0) {
      scheduler.cancelTask(probeTaskId);
      probeTaskId = -1;
    }
    unregisterHook();
    densitySamples.clear();
  }

  private void handleOutbound(net.orbis.zakum.api.packets.PacketContext ctx) {
    packetsObserved.increment();
    if (!runtimeEnabled) return;

    Player player = ctx.player();
    if (player == null) return;

    DensitySample sample = densitySamples.get(player.getUniqueId());
    if (sample == null) return;
    long ageMs = Math.max(0L, System.currentTimeMillis() - sample.sampledAtMs());
    if (ageMs > cfg.maxSampleAgeMs()) return;
    if (sample.bypass()) {
      bypassSkips.increment();
      return;
    }
    PlayerVisualModeService.Mode mode = sample.mode();
    if (respectPerfMode && mode == PlayerVisualModeService.Mode.QUALITY) {
      qualitySkips.increment();
      return;
    }

    int threshold = cullThreshold(mode);
    if (sample.density() < threshold) return;

    ctx.cancel();
    packetsDropped.increment();
    if (metrics != null) metrics.recordAction("packet_cull_drop");
  }

  private void sampleOnlinePlayers() {
    sampleRuns.increment();
    List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
    int onlineCount = players.size();
    lastOnlineCount = onlineCount;
    if (onlineCount == 0) {
      densitySamples.clear();
      lastSampleBatch = 0;
      return;
    }

    Set<UUID> online = new HashSet<>(onlineCount);
    for (Player player : players) {
      online.add(player.getUniqueId());
    }
    densitySamples.keySet().retainAll(online);

    int batch = computeSampleBatch(onlineCount);
    lastSampleBatch = batch;
    int start = Math.floorMod(sampleCursor.getAndAdd(batch), onlineCount);
    for (int i = 0; i < batch; i++) {
      Player player = players.get((start + i) % onlineCount);
      scheduler.runAtEntity(player, () -> samplePlayerDensity(player));
    }
  }

  private void samplePlayerDensity(Player player) {
    if (player == null || !player.isOnline()) return;
    int radius = cfg.radius();
    int nearby = player.getNearbyEntities(radius, radius, radius).size();
    densitySamples.put(
      player.getUniqueId(),
      new DensitySample(
        nearby + 1,
        System.currentTimeMillis(),
        resolveMode(player),
        hasBypassPermission(player)
      )
    );
    sampleUpdates.increment();
  }

  private void probePacketService() {
    serviceProbeRuns.increment();
    if (hookRegistered) return;

    PacketService service = Bukkit.getServicesManager().load(PacketService.class);
    if (service == null) return;
    try {
      service.registerHook(plugin, outboundHook);
      this.packetService = service;
      this.hookRegistered = true;
      if (metrics != null) metrics.recordAction("packet_cull_hook_online");
      if (logger != null) {
        logger.info("Packet culling kernel attached to backend=" + service.backend());
      }
      if (probeTaskId > 0) {
        scheduler.cancelTask(probeTaskId);
        probeTaskId = -1;
      }
    } catch (Throwable ex) {
      if (logger != null) {
        logger.warning("Packet culling hook registration failed: " + ex.getMessage());
      }
    }
  }

  private void unregisterHook() {
    PacketService service = this.packetService;
    if (service == null || !hookRegistered) {
      hookRegistered = false;
      packetService = null;
      return;
    }
    try {
      service.unregisterHooks(plugin);
    } catch (Throwable ignored) {
      // best effort
    }
    hookRegistered = false;
    packetService = null;
  }

  private int computeSampleBatch(int onlineCount) {
    long intervalMs = Math.max(1L, cfg.sampleTicks()) * 50L;
    long maxAgeMs = Math.max(1L, cfg.maxSampleAgeMs());
    long intervals = Math.max(1L, maxAgeMs / intervalMs);
    int required = (int) Math.ceil(onlineCount / (double) intervals);
    return Math.max(1, Math.min(onlineCount, required));
  }

  private int cullThreshold(PlayerVisualModeService.Mode mode) {
    int threshold = cfg.densityThreshold();
    if (respectPerfMode && mode == PlayerVisualModeService.Mode.PERFORMANCE) {
      threshold = Math.max(1, threshold / 2);
    }
    return threshold;
  }

  private PlayerVisualModeService.Mode resolveMode(Player player) {
    if (!respectPerfMode || visualModeService == null) {
      return PlayerVisualModeService.Mode.AUTO;
    }
    return visualModeService.mode(player);
  }

  private boolean hasBypassPermission(Player player) {
    if (bypassPermission == null || bypassPermission.isBlank()) return false;
    try {
      return player.hasPermission(bypassPermission);
    } catch (Throwable ignored) {
      return false;
    }
  }

  private record DensitySample(
    int density,
    long sampledAtMs,
    PlayerVisualModeService.Mode mode,
    boolean bypass
  ) {}

  public record SampleSnapshot(
    int density,
    long sampledAtMs,
    PlayerVisualModeService.Mode mode,
    boolean bypass
  ) {}

  public record Snapshot(
    boolean runtimeEnabled,
    boolean configuredEnabled,
    boolean hookRegistered,
    String backend,
    int hookCount,
    int radius,
    int densityThreshold,
    long maxSampleAgeMs,
    int lastOnlineCount,
    int lastSampleBatch,
    boolean respectPerfMode,
    String bypassPermission,
    int sampledPlayers,
    long sampleRuns,
    long sampleUpdates,
    long serviceProbeRuns,
    long bypassSkips,
    long qualitySkips,
    long packetsObserved,
    long packetsDropped,
    double dropRate
  ) {}
}
