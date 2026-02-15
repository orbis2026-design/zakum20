package net.orbis.zakum.bridge.commandapi;

import dev.jorel.commandapi.CommandAPIBukkit;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.DoubleArgument;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.arguments.IntegerArgument;
import dev.jorel.commandapi.arguments.LongArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import dev.jorel.commandapi.executors.CommandExecutor;
import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.api.chat.ChatPacketBuffer;
import net.orbis.zakum.api.boosters.BoosterKind;
import net.orbis.zakum.api.cache.BurstCacheService;
import net.orbis.zakum.api.db.DatabaseState;
import net.orbis.zakum.api.entitlements.EntitlementScope;
import net.orbis.zakum.api.packets.PacketService;
import net.orbis.zakum.api.vault.EconomyService;
import net.orbis.zakum.core.ZakumPlugin;
import net.orbis.zakum.core.boosters.SqlBoosterService;
import net.orbis.zakum.core.cloud.SecureCloudClient;
import net.orbis.zakum.core.ops.StressHarnessV2;
import net.orbis.zakum.core.ops.SoakAutomationProfile;
import net.orbis.zakum.core.concurrent.ZakumSchedulerImpl;
import net.orbis.zakum.core.perf.PacketCullingKernel;
import net.orbis.zakum.core.perf.PlayerVisualModeService;
import net.orbis.zakum.core.perf.ThreadGuard;
import net.orbis.zakum.core.perf.VisualCircuitBreaker;
import net.orbis.zakum.core.social.ChatBufferCache;
import net.orbis.zakum.core.social.LocalizedChatPacketBuffer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.time.Instant;

/**
 * CommandAPI-powered command tree for Zakum.
 *
 * Notes:
 * - This is a bridge plugin. Install it only if you want CommandAPI UX.
 * - It overrides the vanilla plugin.yml command from zakum-core by unregistering it.
 */
public final class CommandApiBridgePlugin extends JavaPlugin {

  private ZakumApi api;
  private ZakumPlugin core;

  @Override
  public void onEnable() {
    this.api = Bukkit.getServicesManager().load(ZakumApi.class);
    if (api == null) {
      getLogger().severe("ZakumApi not found. Disabling ZakumBridgeCommandAPI.");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }
    this.core = api.plugin() instanceof ZakumPlugin plugin ? plugin : null;
    if (core == null) {
      getLogger().warning("ZakumPlugin instance not found. Core ops commands will be limited.");
    }

    // Remove the fallback /zakum command (registered by zakum-core plugin.yml) so we can own the node cleanly.
    try {
      CommandAPIBukkit.unregister("zakum", true, true);
      CommandAPIBukkit.unregister("perfmode", true, true);
    } catch (Throwable ignored) {
      // Best-effort.
    }

    api.getBridgeManager().registerBridge("commandapi");
    registerCommands();
    getLogger().info("ZakumBridgeCommandAPI enabled.");
  }

  @Override
  public void onDisable() {
    if (api != null) {
      api.getBridgeManager().unregisterBridge("commandapi");
    }
    // Best-effort unregister to avoid command ghosts during /reload.
    try {
      CommandAPIBukkit.unregister("zakum", true, true);
      CommandAPIBukkit.unregister("perfmode", true, true);
    } catch (Throwable ignored) {}
  }

  private void registerCommands() {
    CommandAPICommand root = new CommandAPICommand("zakum")
      .withPermission("zakum.admin");

    root.withSubcommand(new CommandAPICommand("status")
      .executes((CommandExecutor) (sender, args) -> cmdStatus(sender)));

    root.withSubcommand(entitlementsCommand());
    root.withSubcommand(boostersCommand());
    root.withSubcommand(packetsCommand());
    root.withSubcommand(cloudCommand());
    root.withSubcommand(controlPlaneCommand());
    root.withSubcommand(perfCommand());
    root.withSubcommand(stressCommand());
    root.withSubcommand(soakCommand());
    root.withSubcommand(aceCommand());
    root.withSubcommand(chatBufferCommand());
    root.withSubcommand(economyCommand());
    root.withSubcommand(packetCullCommand());
    root.withSubcommand(burstCacheCommand());
    root.withSubcommand(asyncCommand());
    root.withSubcommand(threadGuardCommand());
    root.withSubcommand(dataHealthCommand());
    root.withSubcommand(modulesCommand());
    root.withSubcommand(tasksCommand());

    root.register();
    registerPerfModeCommand();
  }

  private void cmdStatus(CommandSender sender) {
    var s = api.settings();
    sender.sendMessage(ChatColor.AQUA + "Zakum " + ChatColor.GRAY + "(serverId=" + s.server().id() + ")");
    sender.sendMessage(ChatColor.GRAY + "DB: " + colorDb(api.database().state()) + api.database().state());
    sender.sendMessage(ChatColor.GRAY + "ControlPlane: " + (s.controlPlane().enabled() ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled"));
    sender.sendMessage(ChatColor.GRAY + "Metrics: " + (s.observability().metrics().enabled() ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled"));

    PacketService ps = Bukkit.getServicesManager().load(PacketService.class);
    if (ps == null) {
      sender.sendMessage(ChatColor.GRAY + "Packets: " + ChatColor.DARK_GRAY + "not installed");
    } else {
      sender.sendMessage(ChatColor.GRAY + "Packets: " + ChatColor.GREEN + ps.backend() + ChatColor.GRAY + " (hooks=" + ps.hookCount() + ")");
    }
  }

  private CommandAPICommand packetsCommand() {
    return new CommandAPICommand("packets")
      .withSubcommand(new CommandAPICommand("status")
        .executes((CommandExecutor) (sender, args) -> {
          PacketService ps = Bukkit.getServicesManager().load(PacketService.class);
          if (ps == null) {
            sender.sendMessage(ChatColor.RED + "PacketService not available (install ZakumPackets + PacketEvents, and enable packets.*).");
            return;
          }
          sender.sendMessage(ChatColor.AQUA + "Packets backend: " + ChatColor.GRAY + ps.backend());
          sender.sendMessage(ChatColor.AQUA + "Registered hooks: " + ChatColor.GRAY + ps.hookCount());
        })
      );
  }

  private CommandAPICommand cloudCommand() {
    return new CommandAPICommand("cloud")
      .withSubcommand(new CommandAPICommand("status")
        .executes((CommandExecutor) (sender, args) -> cmdCloudStatus(sender))
      )
      .withSubcommand(new CommandAPICommand("flush")
        .executes((CommandExecutor) (sender, args) -> cmdCloudFlush(sender))
      );
  }

  private CommandAPICommand controlPlaneCommand() {
    return new CommandAPICommand("controlplane")
      .withSubcommand(new CommandAPICommand("status")
        .executes((CommandExecutor) (sender, args) -> {
          ZakumPlugin corePlugin = requireCore(sender);
          if (corePlugin == null) return;
          for (String line : corePlugin.controlPlaneStatusLines()) {
            sender.sendMessage(line);
          }
        })
      );
  }

  private void cmdCloudStatus(CommandSender sender) {
    ZakumPlugin corePlugin = requireCore(sender);
    if (corePlugin == null) return;

    var settings = api.settings();
    sender.sendMessage("Zakum Cloud Status");
    sender.sendMessage("enabled=" + settings.cloud().enabled());
    sender.sendMessage("configuredServerId=" + settings.cloud().serverId());
    sender.sendMessage("pollTaskId=" + corePlugin.getCloudPollTaskId());

    SecureCloudClient cloud = corePlugin.getCloudClient();
    if (cloud == null) {
      sender.sendMessage("client=offline (cloud disabled or not configured)");
      return;
    }

    var snap = cloud.statusSnapshot();
    sender.sendMessage("client=online");
    sender.sendMessage("baseUrl=" + snap.baseUrl());
    sender.sendMessage("serverId=" + snap.serverId());
    sender.sendMessage("lastPollAttempt=" + formatEpochMillis(snap.lastPollAttemptMs()));
    sender.sendMessage("lastPollSuccess=" + formatEpochMillis(snap.lastPollSuccessMs()));
    sender.sendMessage("lastHttpStatus=" + snap.lastHttpStatus());
    sender.sendMessage("lastBatchSize=" + snap.lastBatchSize());
    sender.sendMessage("totalQueueActions=" + snap.totalQueueActions());
    sender.sendMessage("duplicateQueueSkips=" + snap.duplicateQueueSkips());
    sender.sendMessage("inflightQueueSkips=" + snap.inflightQueueSkips());
    sender.sendMessage("offlineQueueSkips=" + snap.offlineQueueSkips());
    sender.sendMessage("invalidQueueSkips=" + snap.invalidQueueSkips());
    sender.sendMessage("queueFailures=" + snap.queueFailures());
    sender.sendMessage("processedIds=" + snap.processedIds());
    sender.sendMessage("inflightIds=" + snap.inflightIds());
    sender.sendMessage("dedupe.persist.enabled=" + snap.dedupePersistEnabled());
    sender.sendMessage("dedupe.persist.file=" + snap.dedupePersistFile());
    sender.sendMessage("dedupe.persist.flushSeconds=" + snap.dedupePersistFlushSeconds());
    sender.sendMessage("dedupe.persist.lastLoad=" + formatEpochMillis(snap.lastDedupeLoadMs()));
    sender.sendMessage("dedupe.persist.lastSave=" + formatEpochMillis(snap.lastDedupePersistMs()));
    sender.sendMessage("dedupe.persist.errors=" + snap.dedupePersistErrors());
    String dedupeErr = snap.lastDedupePersistError();
    sender.sendMessage("dedupe.persist.lastError=" + (dedupeErr == null || dedupeErr.isBlank() ? "none" : dedupeErr));
    sender.sendMessage("ack.enabled=" + snap.ackEnabled());
    sender.sendMessage("ack.disabled=" + snap.ackDisabled());
    sender.sendMessage("ack.pending=" + snap.pendingAcks());
    sender.sendMessage("ack.queued=" + snap.ackQueued());
    sender.sendMessage("ack.sent=" + snap.ackSent());
    sender.sendMessage("ack.failed=" + snap.ackFailed());
    sender.sendMessage("ack.retried=" + snap.ackRetried());
    sender.sendMessage("ack.dropped=" + snap.ackDropped());
    sender.sendMessage("ack.lastAttempt=" + formatEpochMillis(snap.lastAckAttemptMs()));
    sender.sendMessage("ack.lastSuccess=" + formatEpochMillis(snap.lastAckSuccessMs()));
    sender.sendMessage("ack.lastStatus=" + snap.lastAckStatus());
    String ackErr = snap.lastAckError();
    sender.sendMessage("ack.lastError=" + (ackErr == null || ackErr.isBlank() ? "none" : ackErr));
    String err = snap.lastError();
    sender.sendMessage("lastError=" + (err == null || err.isBlank() ? "none" : err));
  }

  private void cmdCloudFlush(CommandSender sender) {
    ZakumPlugin corePlugin = requireCore(sender);
    if (corePlugin == null) return;
    SecureCloudClient cloud = corePlugin.getCloudClient();
    if (cloud == null) {
      sender.sendMessage("Cloud client is offline.");
      return;
    }
    boolean ack = cloud.requestAckFlush();
    boolean dedupe = cloud.requestDedupePersist();
    sender.sendMessage("Cloud flush queued (ack=" + ack + ", dedupe=" + dedupe + ").");
  }

  private CommandAPICommand perfCommand() {
    return new CommandAPICommand("perf")
      .withSubcommand(new CommandAPICommand("status")
        .executes((CommandExecutor) (sender, args) -> cmdPerfStatus(sender))
      );
  }

  private void cmdPerfStatus(CommandSender sender) {
    ZakumPlugin corePlugin = requireCore(sender);
    if (corePlugin == null) return;

    sender.sendMessage("Zakum Performance Status");
    var breakerCfg = api.settings().operations().circuitBreaker();
    sender.sendMessage("circuit.enabled=" + breakerCfg.enabled());

    VisualCircuitBreaker breaker = corePlugin.getVisualCircuitBreaker();
    if (breaker == null) {
      sender.sendMessage("circuit=offline");
      return;
    }

    var snap = breaker.snapshot();
    sender.sendMessage("circuit.open=" + snap.open());
    sender.sendMessage("circuit.lastTps=" + String.format(java.util.Locale.ROOT, "%.2f", snap.lastTps()));
    sender.sendMessage("circuit.reason=" + (snap.reason() == null || snap.reason().isBlank() ? "none" : snap.reason()));
    sender.sendMessage("circuit.changedAt=" + formatEpochMillis(snap.changedAtMs()));
    sender.sendMessage("circuit.taskId=" + snap.taskId());
  }

  private CommandAPICommand stressCommand() {
    int maxIterations = Math.max(1, api.settings().operations().stress().maxIterations());
    return new CommandAPICommand("stress")
      .withSubcommand(new CommandAPICommand("start")
        .withOptionalArguments(new IntegerArgument("iterations", 1, maxIterations))
        .withOptionalArguments(new IntegerArgument("virtualPlayers", 1, 50_000))
        .executes((CommandExecutor) (sender, args) -> {
          ZakumPlugin corePlugin = requireCore(sender);
          if (corePlugin == null) return;
          StressHarnessV2 harness = corePlugin.getStressHarness();
          if (harness == null) {
            sender.sendMessage("Stress harness is not available.");
            return;
          }

          Integer iterations = (Integer) args.getOptional("iterations").orElse(0);
          Integer virtualPlayers = (Integer) args.getOptional("virtualPlayers").orElse(null);
          var result = harness.start(iterations == null ? 0 : iterations, virtualPlayers);
          sender.sendMessage(result.message());
        })
      )
      .withSubcommand(new CommandAPICommand("stop")
        .executes((CommandExecutor) (sender, args) -> {
          ZakumPlugin corePlugin = requireCore(sender);
          if (corePlugin == null) return;
          StressHarnessV2 harness = corePlugin.getStressHarness();
          if (harness == null) {
            sender.sendMessage("Stress harness is not available.");
            return;
          }
          sender.sendMessage(harness.stop("command").message());
        })
      )
      .withSubcommand(new CommandAPICommand("status")
        .executes((CommandExecutor) (sender, args) -> cmdStressStatus(sender))
      )
      .withSubcommand(new CommandAPICommand("report")
        .withOptionalArguments(new StringArgument("label"))
        .executes((CommandExecutor) (sender, args) -> {
          ZakumPlugin corePlugin = requireCore(sender);
          if (corePlugin == null) return;
          StressHarnessV2 harness = corePlugin.getStressHarness();
          if (harness == null) {
            sender.sendMessage("Stress harness is not available.");
            return;
          }
          String label = (String) args.getOptional("label").orElse(null);
          sender.sendMessage("Generating stress report...");
          api.getScheduler().runAsync(() -> {
            StressHarnessV2.ReportResult result = harness.writeReport(label);
            api.getScheduler().runGlobal(() -> sender.sendMessage(result.message()));
          });
        })
      );
  }

  private CommandAPICommand soakCommand() {
    return new CommandAPICommand("soak")
      .withSubcommand(new CommandAPICommand("start")
        .withOptionalArguments(new IntegerArgument("durationMinutes", 1, 24 * 60))
        .executes((CommandExecutor) (sender, args) -> {
          ZakumPlugin corePlugin = requireCore(sender);
          if (corePlugin == null) return;
          SoakAutomationProfile soak = corePlugin.getSoakProfile();
          if (soak == null) {
            sender.sendMessage("Soak profile is not available.");
            return;
          }
          Integer duration = (Integer) args.getOptional("durationMinutes").orElse(null);
          sender.sendMessage(soak.start(duration).message());
        })
      )
      .withSubcommand(new CommandAPICommand("stop")
        .withOptionalArguments(new StringArgument("reason"))
        .executes((CommandExecutor) (sender, args) -> {
          ZakumPlugin corePlugin = requireCore(sender);
          if (corePlugin == null) return;
          SoakAutomationProfile soak = corePlugin.getSoakProfile();
          if (soak == null) {
            sender.sendMessage("Soak profile is not available.");
            return;
          }
          String reason = (String) args.getOptional("reason").orElse("manual");
          sender.sendMessage(soak.stop(reason).message());
        })
      )
      .withSubcommand(new CommandAPICommand("status")
        .executes((CommandExecutor) (sender, args) -> {
          ZakumPlugin corePlugin = requireCore(sender);
          if (corePlugin == null) return;
          SoakAutomationProfile soak = corePlugin.getSoakProfile();
          if (soak == null) {
            sender.sendMessage("Soak profile is not available.");
            return;
          }
          var snap = soak.snapshot();
          sender.sendMessage("Zakum Soak Profile");
          sender.sendMessage("configuredEnabled=" + snap.configuredEnabled());
          sender.sendMessage("running=" + snap.running());
          sender.sendMessage("runId=" + snap.runId());
          sender.sendMessage("taskId=" + snap.taskId());
          sender.sendMessage("startedAt=" + formatEpochMillis(snap.startedAtMs()));
          sender.sendMessage("stopAt=" + formatEpochMillis(snap.stopAtMs()));
          sender.sendMessage("lastSampleAt=" + formatEpochMillis(snap.lastSampleAtMs()));
          sender.sendMessage("durationMinutes=" + snap.durationMinutes());
          sender.sendMessage("sampleIntervalSeconds=" + snap.sampleIntervalSeconds());
          sender.sendMessage("minTps=" + String.format(java.util.Locale.ROOT, "%.2f", snap.minTps()));
          sender.sendMessage("lastTps=" + String.format(java.util.Locale.ROOT, "%.2f", snap.lastTps()));
          sender.sendMessage("samples=" + snap.sampleCount());
          sender.sendMessage("lowTpsConsecutive=" + snap.lowTpsConsecutive());
          sender.sendMessage("assertionFailures=" + snap.assertionFailures());
          sender.sendMessage("abortOnAssertionFailure=" + snap.abortOnAssertionFailure());
          sender.sendMessage("maxConsecutiveLowTpsSamples=" + snap.maxConsecutiveLowTpsSamples());
          sender.sendMessage("maxThreadGuardViolationDelta=" + snap.maxThreadGuardViolationDelta());
          sender.sendMessage("maxAsyncRejectedDelta=" + snap.maxAsyncRejectedDelta());
          sender.sendMessage("maxStressErrorDelta=" + snap.maxStressErrorDelta());
          sender.sendMessage("autoStressStarted=" + snap.autoStressStarted());
          sender.sendMessage("threadGuardDelta=" + snap.deltaThreadGuardViolations());
          sender.sendMessage("asyncRejectedDelta=" + snap.deltaAsyncRejected());
          sender.sendMessage("stressErrorDelta=" + snap.deltaStressErrors());
          String assertion = snap.lastAssertion();
          sender.sendMessage("lastAssertion=" + (assertion == null || assertion.isBlank() ? "none" : assertion));
          String reason = snap.lastStopReason();
          sender.sendMessage("lastStopReason=" + (reason == null || reason.isBlank() ? "none" : reason));
        })
      );
  }

  private CommandAPICommand aceCommand() {
    return new CommandAPICommand("ace")
      .withSubcommand(new CommandAPICommand("status")
        .executes((CommandExecutor) (sender, args) -> {
          ZakumPlugin corePlugin = requireCore(sender);
          if (corePlugin == null) return;
          for (String line : corePlugin.aceDiagnosticsStatusLines()) {
            sender.sendMessage(line);
          }
        })
      )
      .withSubcommand(new CommandAPICommand("errors")
        .withOptionalArguments(new IntegerArgument("limit", 1, 100))
        .executes((CommandExecutor) (sender, args) -> {
          ZakumPlugin corePlugin = requireCore(sender);
          if (corePlugin == null) return;
          Integer limit = (Integer) args.getOptional("limit").orElse(10);
          for (String line : corePlugin.aceDiagnosticsErrorLines(limit == null ? 10 : limit)) {
            sender.sendMessage(line);
          }
        })
      )
      .withSubcommand(new CommandAPICommand("clear")
        .executes((CommandExecutor) (sender, args) -> {
          ZakumPlugin corePlugin = requireCore(sender);
          if (corePlugin == null) return;
          var diagnostics = corePlugin.getAceDiagnostics();
          if (diagnostics == null) {
            sender.sendMessage("ACE diagnostics are offline.");
            return;
          }
          diagnostics.clear();
          sender.sendMessage("ACE diagnostics cleared.");
        })
      )
      .withSubcommand(new CommandAPICommand("enable")
        .executes((CommandExecutor) (sender, args) -> {
          ZakumPlugin corePlugin = requireCore(sender);
          if (corePlugin == null) return;
          var diagnostics = corePlugin.getAceDiagnostics();
          if (diagnostics == null) {
            sender.sendMessage("ACE diagnostics are offline.");
            return;
          }
          diagnostics.setRuntimeEnabled(true);
          sender.sendMessage("ACE diagnostics runtime enabled.");
        })
      )
      .withSubcommand(new CommandAPICommand("disable")
        .executes((CommandExecutor) (sender, args) -> {
          ZakumPlugin corePlugin = requireCore(sender);
          if (corePlugin == null) return;
          var diagnostics = corePlugin.getAceDiagnostics();
          if (diagnostics == null) {
            sender.sendMessage("ACE diagnostics are offline.");
            return;
          }
          diagnostics.setRuntimeEnabled(false);
          sender.sendMessage("ACE diagnostics runtime disabled.");
        })
      );
  }

  private void cmdStressStatus(CommandSender sender) {
    ZakumPlugin corePlugin = requireCore(sender);
    if (corePlugin == null) return;
    StressHarnessV2 harness = corePlugin.getStressHarness();
    if (harness == null) {
      sender.sendMessage("Stress harness is not available.");
      return;
    }
    var snap = harness.snapshot();
    sender.sendMessage("Zakum Stress Harness v2");
    sender.sendMessage("running=" + snap.running());
    sender.sendMessage("plannedIterations=" + snap.plannedIterations());
    sender.sendMessage("scheduledIterations=" + snap.scheduledIterations());
    sender.sendMessage("completedIterations=" + snap.completedIterations());
    sender.sendMessage("errors=" + snap.errorCount());
    sender.sendMessage("skippedNoPlayer=" + snap.skippedNoPlayer());
    sender.sendMessage("virtualPlayers=" + snap.virtualPlayers());
    sender.sendMessage("onlinePlayers=" + snap.onlinePlayers());
    sender.sendMessage("iterationsPerTick=" + snap.iterationsPerTick());
    sender.sendMessage("lastTps=" + String.format(java.util.Locale.ROOT, "%.2f", snap.lastTps()));
    sender.sendMessage("stopReason=" + snap.stopReason());
    sender.sendMessage("startedAt=" + formatEpochMillis(snap.startedAtMs()));
    sender.sendMessage("stopAt=" + formatEpochMillis(snap.stopAtMs()));
    sender.sendMessage("nextAllowedAt=" + formatEpochMillis(snap.nextAllowedAtMs()));

    String lastErr = snap.lastError();
    sender.sendMessage("lastError=" + (lastErr == null || lastErr.isBlank() ? "none" : lastErr));
    if (snap.lastErrorAtMs() > 0L) {
      sender.sendMessage("lastErrorAt=" + formatEpochMillis(snap.lastErrorAtMs()));
    }

    if (snap.scenarioCounts() != null && !snap.scenarioCounts().isEmpty()) {
      sender.sendMessage("scenarioCounts=" + snap.scenarioCounts());
    }
  }

  private CommandAPICommand chatBufferCommand() {
    return new CommandAPICommand("chatbuffer")
      .withSubcommand(new CommandAPICommand("status")
        .executes((CommandExecutor) (sender, args) -> cmdChatBufferStatus(sender))
      )
      .withSubcommand(new CommandAPICommand("warmup")
        .executes((CommandExecutor) (sender, args) -> cmdChatBufferWarmup(sender))
      );
  }

  private void cmdChatBufferStatus(CommandSender sender) {
    ZakumPlugin corePlugin = requireCore(sender);
    if (corePlugin == null) return;

    sender.sendMessage("Zakum Chat Buffer Status");
    ChatBufferCache cache = corePlugin.getChatBufferCache();
    if (cache != null) {
      var parse = cache.stats();
      sender.sendMessage("parse.enabled=" + parse.enabled());
      sender.sendMessage("parse.requests=" + parse.requests());
      sender.sendMessage("parse.hits=" + parse.hits());
      sender.sendMessage("parse.misses=" + parse.misses());
      sender.sendMessage("parse.estimatedSize=" + parse.estimatedSize());
    } else {
      sender.sendMessage("parse.cache=offline");
    }

    ChatPacketBuffer buffer = corePlugin.getChatPacketBuffer();
    if (buffer instanceof LocalizedChatPacketBuffer localized) {
      var prepared = localized.stats();
      sender.sendMessage("prepared.templateKeys=" + prepared.templateKeys());
      sender.sendMessage("prepared.cacheSize=" + prepared.preparedCacheSize());
      sender.sendMessage("prepared.resolveRequests=" + prepared.resolveRequests());
      sender.sendMessage("prepared.resolveHits=" + prepared.resolveHits());
      sender.sendMessage("prepared.resolveMisses=" + prepared.resolveMisses());
      sender.sendMessage("prepared.sends=" + prepared.sends());
      sender.sendMessage("prepared.packetDispatchEnabled=" + prepared.packetDispatchEnabled());
      sender.sendMessage("prepared.packetDispatchAvailable=" + prepared.packetDispatchAvailable());
      sender.sendMessage("prepared.packetSends=" + prepared.packetSends());
      sender.sendMessage("prepared.fallbackSends=" + prepared.fallbackSends());
      sender.sendMessage("prepared.packetFailures=" + prepared.packetFailures());
      return;
    }
    sender.sendMessage("prepared.buffer=not-localized");
  }

  private void cmdChatBufferWarmup(CommandSender sender) {
    ZakumPlugin corePlugin = requireCore(sender);
    if (corePlugin == null) return;
    ChatPacketBuffer buffer = corePlugin.getChatPacketBuffer();
    if (buffer == null) {
      sender.sendMessage("Chat buffer is not available.");
      return;
    }
    api.getScheduler().runAsync(() -> {
      buffer.warmup();
      api.getScheduler().runGlobal(() -> sender.sendMessage("Chat buffer warmup finished."));
    });
    sender.sendMessage("Chat buffer warmup started.");
  }

  private CommandAPICommand economyCommand() {
    CommandAPICommand economy = new CommandAPICommand("economy");

    economy.withSubcommand(new CommandAPICommand("status")
      .executes((CommandExecutor) (sender, args) -> {
        EconomyService eco = Bukkit.getServicesManager().load(EconomyService.class);
        if (eco == null) {
          sender.sendMessage("Economy capability is not registered.");
          return;
        }
        sender.sendMessage("Economy status");
        sender.sendMessage("available=" + eco.available());
      })
    );

    economy.withSubcommand(new CommandAPICommand("balance")
      .withArguments(new StringArgument("player"))
      .executes((CommandExecutor) (sender, args) -> {
        EconomyService eco = Bukkit.getServicesManager().load(EconomyService.class);
        if (eco == null) {
          sender.sendMessage("Economy capability is not registered.");
          return;
        }
        String token = (String) args.get("player");
        UUID playerId = resolveUuid(sender, token);
        if (playerId == null) return;
        double balance = eco.balance(playerId);
        sender.sendMessage("balance[" + playerId + "]=" + formatMoney(balance));
      })
    );

    economy.withSubcommand(new CommandAPICommand("set")
      .withArguments(new StringArgument("player"))
      .withArguments(new DoubleArgument("amount", 0.0d, 1.0e12d))
      .executes((CommandExecutor) (sender, args) -> {
        EconomyService eco = Bukkit.getServicesManager().load(EconomyService.class);
        if (eco == null) {
          sender.sendMessage("Economy capability is not registered.");
          return;
        }
        String token = (String) args.get("player");
        UUID playerId = resolveUuid(sender, token);
        if (playerId == null) return;
        double amount = (double) args.get("amount");
        if (!Double.isFinite(amount) || amount < 0.0d) {
          sender.sendMessage("Amount must be a non-negative number.");
          return;
        }

        double current = eco.balance(playerId);
        double target = Math.max(0.0d, amount);
        if (target > current) {
          var result = eco.deposit(playerId, target - current);
          if (!result.success()) {
            sender.sendMessage("Set failed: " + result.error());
            return;
          }
        } else if (target < current) {
          var result = eco.withdraw(playerId, current - target);
          if (!result.success()) {
            sender.sendMessage("Set failed: " + result.error());
            return;
          }
        }
        sender.sendMessage("balance[" + playerId + "]=" + formatMoney(target));
      })
    );

    economy.withSubcommand(new CommandAPICommand("add")
      .withArguments(new StringArgument("player"))
      .withArguments(new DoubleArgument("amount", 0.0d, 1.0e12d))
      .executes((CommandExecutor) (sender, args) -> {
        EconomyService eco = Bukkit.getServicesManager().load(EconomyService.class);
        if (eco == null) {
          sender.sendMessage("Economy capability is not registered.");
          return;
        }
        String token = (String) args.get("player");
        UUID playerId = resolveUuid(sender, token);
        if (playerId == null) return;
        double amount = (double) args.get("amount");
        var result = eco.deposit(playerId, amount);
        if (!result.success()) {
          sender.sendMessage("Operation failed: " + result.error());
          return;
        }
        sender.sendMessage("balance[" + playerId + "]=" + formatMoney(result.newBalance()));
      })
    );

    economy.withSubcommand(new CommandAPICommand("take")
      .withArguments(new StringArgument("player"))
      .withArguments(new DoubleArgument("amount", 0.0d, 1.0e12d))
      .executes((CommandExecutor) (sender, args) -> {
        EconomyService eco = Bukkit.getServicesManager().load(EconomyService.class);
        if (eco == null) {
          sender.sendMessage("Economy capability is not registered.");
          return;
        }
        String token = (String) args.get("player");
        UUID playerId = resolveUuid(sender, token);
        if (playerId == null) return;
        double amount = (double) args.get("amount");
        var result = eco.withdraw(playerId, amount);
        if (!result.success()) {
          sender.sendMessage("Operation failed: " + result.error());
          return;
        }
        sender.sendMessage("balance[" + playerId + "]=" + formatMoney(result.newBalance()));
      })
    );

    return economy;
  }

  private CommandAPICommand packetCullCommand() {
    return new CommandAPICommand("packetcull")
      .withSubcommand(new CommandAPICommand("status")
        .executes((CommandExecutor) (sender, args) -> cmdPacketCullStatus(sender))
      )
      .withSubcommand(new CommandAPICommand("enable")
        .executes((CommandExecutor) (sender, args) -> {
          ZakumPlugin corePlugin = requireCore(sender);
          if (corePlugin == null) return;
          PacketCullingKernel kernel = corePlugin.getPacketCullingKernel();
          if (kernel == null) {
            sender.sendMessage("Packet culling kernel is offline.");
            return;
          }
          kernel.setRuntimeEnabled(true);
          sender.sendMessage("Packet culling runtime enabled.");
        })
      )
      .withSubcommand(new CommandAPICommand("disable")
        .executes((CommandExecutor) (sender, args) -> {
          ZakumPlugin corePlugin = requireCore(sender);
          if (corePlugin == null) return;
          PacketCullingKernel kernel = corePlugin.getPacketCullingKernel();
          if (kernel == null) {
            sender.sendMessage("Packet culling kernel is offline.");
            return;
          }
          kernel.setRuntimeEnabled(false);
          sender.sendMessage("Packet culling runtime disabled.");
        })
      )
      .withSubcommand(new CommandAPICommand("refresh")
        .withOptionalArguments(new EntitySelectorArgument.OnePlayer("player"))
        .executes((CommandExecutor) (sender, args) -> {
          ZakumPlugin corePlugin = requireCore(sender);
          if (corePlugin == null) return;
          PacketCullingKernel kernel = corePlugin.getPacketCullingKernel();
          if (kernel == null) {
            sender.sendMessage("Packet culling kernel is offline.");
            return;
          }
          Player target = resolvePacketCullTarget(sender, args);
          if (target == null) return;
          kernel.requestSample(target);
          sender.sendMessage("Packet culling sample queued for " + target.getName() + ".");
        })
      )
      .withSubcommand(new CommandAPICommand("sample")
        .withOptionalArguments(new EntitySelectorArgument.OnePlayer("player"))
        .executes((CommandExecutor) (sender, args) -> {
          ZakumPlugin corePlugin = requireCore(sender);
          if (corePlugin == null) return;
          PacketCullingKernel kernel = corePlugin.getPacketCullingKernel();
          if (kernel == null) {
            sender.sendMessage("Packet culling kernel is offline.");
            return;
          }
          Player target = resolvePacketCullTarget(sender, args);
          if (target == null) return;
          var sample = kernel.sampleSnapshot(target.getUniqueId());
          if (sample == null) {
            kernel.requestSample(target);
            sender.sendMessage("No sample yet for " + target.getName() + ". Sample queued; run again shortly.");
            return;
          }

          long ageMs = Math.max(0L, System.currentTimeMillis() - sample.sampledAtMs());
          int threshold = kernel.thresholdFor(sample.mode());
          boolean runtimeEnabled = kernel.runtimeEnabled();
          boolean configuredEnabled = api.settings().packets().culling().enabled();
          boolean wouldDrop = runtimeEnabled
            && configuredEnabled
            && !sample.bypass()
            && (!api.settings().packets().culling().respectPerfMode() || sample.mode() != PlayerVisualModeService.Mode.QUALITY)
            && ageMs <= api.settings().packets().culling().maxSampleAgeMs()
            && sample.density() >= threshold;

          sender.sendMessage("Packet cull sample: " + target.getName());
          sender.sendMessage("density=" + sample.density());
          sender.sendMessage("ageMs=" + ageMs);
          sender.sendMessage("mode=" + sample.mode().displayName());
          sender.sendMessage("bypass=" + sample.bypass());
          sender.sendMessage("threshold=" + threshold);
          sender.sendMessage("configuredEnabled=" + configuredEnabled);
          sender.sendMessage("runtimeEnabled=" + runtimeEnabled);
          sender.sendMessage("wouldDrop=" + wouldDrop);
        })
      );
  }

  private CommandAPICommand burstCacheCommand() {
    return new CommandAPICommand("burstcache")
      .withSubcommand(new CommandAPICommand("status")
        .executes((CommandExecutor) (sender, args) -> {
          ZakumPlugin corePlugin = requireCore(sender);
          if (corePlugin == null) return;
          BurstCacheService cache = corePlugin.getBurstCacheService();
          if (cache == null) {
            sender.sendMessage("Burst cache service is offline.");
            return;
          }
          var snap = cache.snapshot();
          sender.sendMessage("Zakum Burst Cache");
          sender.sendMessage("configuredEnabled=" + snap.configuredEnabled());
          sender.sendMessage("runtimeEnabled=" + snap.runtimeEnabled());
          sender.sendMessage("available=" + snap.available());
          sender.sendMessage("redisUri=" + snap.redisUri());
          sender.sendMessage("keyPrefix=" + snap.keyPrefix());
          sender.sendMessage("defaultTtlSeconds=" + snap.defaultTtlSeconds());
          sender.sendMessage("maximumLocalEntries=" + snap.maximumLocalEntries());
          sender.sendMessage("gets=" + snap.gets());
          sender.sendMessage("puts=" + snap.puts());
          sender.sendMessage("increments=" + snap.increments());
          sender.sendMessage("removes=" + snap.removes());
          sender.sendMessage("redisHits=" + snap.redisHits());
          sender.sendMessage("localHits=" + snap.localHits());
          sender.sendMessage("redisFailures=" + snap.redisFailures());
          sender.sendMessage("lastFailureAt=" + formatEpochMillis(snap.lastFailureAtMs()));
          String err = snap.lastError();
          sender.sendMessage("lastError=" + (err == null || err.isBlank() ? "none" : err));
        })
      )
      .withSubcommand(new CommandAPICommand("enable")
        .executes((CommandExecutor) (sender, args) -> {
          ZakumPlugin corePlugin = requireCore(sender);
          if (corePlugin == null) return;
          BurstCacheService cache = corePlugin.getBurstCacheService();
          if (cache == null) {
            sender.sendMessage("Burst cache service is offline.");
            return;
          }
          cache.setRuntimeEnabled(true);
          sender.sendMessage("Burst cache runtime enabled.");
        })
      )
      .withSubcommand(new CommandAPICommand("disable")
        .executes((CommandExecutor) (sender, args) -> {
          ZakumPlugin corePlugin = requireCore(sender);
          if (corePlugin == null) return;
          BurstCacheService cache = corePlugin.getBurstCacheService();
          if (cache == null) {
            sender.sendMessage("Burst cache service is offline.");
            return;
          }
          cache.setRuntimeEnabled(false);
          sender.sendMessage("Burst cache runtime disabled.");
        })
      );
  }

  private CommandAPICommand threadGuardCommand() {
    return new CommandAPICommand("threadguard")
      .withSubcommand(new CommandAPICommand("status")
        .executes((CommandExecutor) (sender, args) -> {
          ZakumPlugin corePlugin = requireCore(sender);
          if (corePlugin == null) return;
          ThreadGuard guard = corePlugin.getThreadGuard();
          if (guard == null) {
            sender.sendMessage("Thread guard is offline.");
            return;
          }
          var snap = guard.snapshot();
          sender.sendMessage("Zakum Thread Guard");
          sender.sendMessage("configuredEnabled=" + snap.configuredEnabled());
          sender.sendMessage("runtimeEnabled=" + snap.runtimeEnabled());
          sender.sendMessage("enabled=" + snap.enabled());
          sender.sendMessage("failOnViolation=" + snap.failOnViolation());
          sender.sendMessage("maxReportsPerMinute=" + snap.maxReportsPerMinute());
          sender.sendMessage("violations=" + snap.violations());
          sender.sendMessage("lastViolation=" + (snap.lastViolation() == null || snap.lastViolation().isBlank() ? "none" : snap.lastViolation()));
          sender.sendMessage("lastViolationAt=" + formatEpochMillis(snap.lastViolationAtMs()));
          if (snap.topOperations() != null && !snap.topOperations().isEmpty()) {
            sender.sendMessage("topOperations=" + snap.topOperations());
          }
        })
      )
      .withSubcommand(new CommandAPICommand("enable")
        .executes((CommandExecutor) (sender, args) -> {
          ZakumPlugin corePlugin = requireCore(sender);
          if (corePlugin == null) return;
          ThreadGuard guard = corePlugin.getThreadGuard();
          if (guard == null) {
            sender.sendMessage("Thread guard is offline.");
            return;
          }
          guard.setRuntimeEnabled(true);
          sender.sendMessage("Thread guard runtime enabled.");
        })
      )
      .withSubcommand(new CommandAPICommand("disable")
        .executes((CommandExecutor) (sender, args) -> {
          ZakumPlugin corePlugin = requireCore(sender);
          if (corePlugin == null) return;
          ThreadGuard guard = corePlugin.getThreadGuard();
          if (guard == null) {
            sender.sendMessage("Thread guard is offline.");
            return;
          }
          guard.setRuntimeEnabled(false);
          sender.sendMessage("Thread guard runtime disabled.");
        })
      );
  }

  private CommandAPICommand asyncCommand() {
    return new CommandAPICommand("async")
      .withSubcommand(new CommandAPICommand("status")
        .executes((CommandExecutor) (sender, args) -> {
          ZakumPlugin corePlugin = requireCore(sender);
          if (corePlugin == null) return;
          ZakumSchedulerImpl scheduler = corePlugin.getSchedulerRuntime();
          if (scheduler == null) {
            sender.sendMessage("Scheduler is offline.");
            return;
          }
          var snap = scheduler.asyncSnapshot();
          sender.sendMessage("Zakum Async Backpressure");
          sender.sendMessage("configuredEnabled=" + snap.configuredEnabled());
          sender.sendMessage("runtimeEnabled=" + snap.runtimeEnabled());
          sender.sendMessage("enabled=" + snap.enabled());
          sender.sendMessage("maxInFlight=" + snap.maxInFlight());
          sender.sendMessage("maxQueue=" + snap.maxQueue());
          sender.sendMessage("callerRunsOffMainThread=" + snap.callerRunsOffMainThread());
          sender.sendMessage("inFlight=" + snap.inFlight());
          sender.sendMessage("queued=" + snap.queued());
          sender.sendMessage("submitted=" + snap.submitted());
          sender.sendMessage("executed=" + snap.executed());
          sender.sendMessage("queuedTasks=" + snap.queuedTasks());
          sender.sendMessage("rejected=" + snap.rejected());
          sender.sendMessage("callerRuns=" + snap.callerRuns());
          sender.sendMessage("drainRuns=" + snap.drainRuns());
          sender.sendMessage("lastQueueAt=" + formatEpochMillis(snap.lastQueueAtMs()));
          sender.sendMessage("lastRejectAt=" + formatEpochMillis(snap.lastRejectAtMs()));
          sender.sendMessage("lastCallerRunAt=" + formatEpochMillis(snap.lastCallerRunAtMs()));
        })
      )
      .withSubcommand(new CommandAPICommand("enable")
        .executes((CommandExecutor) (sender, args) -> {
          ZakumPlugin corePlugin = requireCore(sender);
          if (corePlugin == null) return;
          ZakumSchedulerImpl scheduler = corePlugin.getSchedulerRuntime();
          if (scheduler == null) {
            sender.sendMessage("Scheduler is offline.");
            return;
          }
          scheduler.setAsyncRuntimeEnabled(true);
          sender.sendMessage("Async backpressure runtime enabled.");
        })
      )
      .withSubcommand(new CommandAPICommand("disable")
        .executes((CommandExecutor) (sender, args) -> {
          ZakumPlugin corePlugin = requireCore(sender);
          if (corePlugin == null) return;
          ZakumSchedulerImpl scheduler = corePlugin.getSchedulerRuntime();
          if (scheduler == null) {
            sender.sendMessage("Scheduler is offline.");
            return;
          }
          scheduler.setAsyncRuntimeEnabled(false);
          sender.sendMessage("Async backpressure runtime disabled.");
        })
      );
  }

  private CommandAPICommand dataHealthCommand() {
    return new CommandAPICommand("datahealth")
      .withSubcommand(new CommandAPICommand("status")
        .executes((CommandExecutor) (sender, args) -> {
          ZakumPlugin corePlugin = requireCore(sender);
          if (corePlugin == null) return;
          sender.sendMessage("Collecting data health...");
          corePlugin.collectDataHealth().whenComplete((snap, ex) -> {
            Runnable send = () -> {
              if (ex != null) {
                sender.sendMessage("Data health failed: " + ex.getMessage());
                return;
              }
              for (String line : corePlugin.dataHealthLines(snap)) {
                sender.sendMessage(line);
              }
            };
            api.getScheduler().runGlobal(send);
          });
        })
      )
      .withSubcommand(new CommandAPICommand("modules")
        .executes((CommandExecutor) (sender, args) -> {
          ZakumPlugin corePlugin = requireCore(sender);
          if (corePlugin == null) return;
          sender.sendMessage("Collecting module data health...");
          corePlugin.collectModuleDataHealthReport().whenComplete((report, ex) -> {
            Runnable send = () -> {
              if (ex != null) {
                sender.sendMessage("Module data health failed: " + ex.getMessage());
                return;
              }
              for (String line : corePlugin.moduleDataHealthLines(report)) {
                sender.sendMessage(line);
              }
            };
            api.getScheduler().runGlobal(send);
          });
        })
      );
  }

  private CommandAPICommand tasksCommand() {
    return new CommandAPICommand("tasks")
      .withSubcommand(new CommandAPICommand("status")
        .executes((CommandExecutor) (sender, args) -> {
          ZakumPlugin corePlugin = requireCore(sender);
          if (corePlugin == null) return;
          for (String line : corePlugin.taskRegistryLines()) {
            sender.sendMessage(line);
          }
        })
      );
  }

  private CommandAPICommand modulesCommand() {
    return new CommandAPICommand("modules")
      .withSubcommand(new CommandAPICommand("status")
        .executes((CommandExecutor) (sender, args) -> {
          ZakumPlugin corePlugin = requireCore(sender);
          if (corePlugin == null) return;
          for (String line : corePlugin.moduleValidatorLines(false)) {
            sender.sendMessage(line);
          }
        })
      )
      .withSubcommand(new CommandAPICommand("validate")
        .executes((CommandExecutor) (sender, args) -> {
          ZakumPlugin corePlugin = requireCore(sender);
          if (corePlugin == null) return;
          for (String line : corePlugin.moduleValidatorLines(true)) {
            sender.sendMessage(line);
          }
        })
      );
  }

  private void cmdPacketCullStatus(CommandSender sender) {
    ZakumPlugin corePlugin = requireCore(sender);
    if (corePlugin == null) return;
    PacketCullingKernel kernel = corePlugin.getPacketCullingKernel();
    if (kernel == null) {
      sender.sendMessage("Packet culling kernel is offline.");
      return;
    }
    var snap = kernel.snapshot();
    sender.sendMessage("Zakum Packet Culling");
    sender.sendMessage("configuredEnabled=" + snap.configuredEnabled());
    sender.sendMessage("runtimeEnabled=" + snap.runtimeEnabled());
    sender.sendMessage("hookRegistered=" + snap.hookRegistered());
    sender.sendMessage("backend=" + snap.backend());
    sender.sendMessage("hookCount=" + snap.hookCount());
    sender.sendMessage("probeIntervalTicks=" + snap.probeIntervalTicks());
    sender.sendMessage("sampleTaskId=" + snap.sampleTaskId());
    sender.sendMessage("probeTaskId=" + snap.probeTaskId());
    sender.sendMessage("hookLastChanged=" + formatEpochMillis(snap.hookLastChangedMs()));
    sender.sendMessage("radius=" + snap.radius());
    sender.sendMessage("densityThreshold=" + snap.densityThreshold());
    sender.sendMessage("maxSampleAgeMs=" + snap.maxSampleAgeMs());
    sender.sendMessage("lastOnlineCount=" + snap.lastOnlineCount());
    sender.sendMessage("lastSampleBatch=" + snap.lastSampleBatch());
    sender.sendMessage("respectPerfMode=" + snap.respectPerfMode());
    String bypass = snap.bypassPermission();
    sender.sendMessage("bypassPermission=" + (bypass == null || bypass.isBlank() ? "none" : bypass));
    sender.sendMessage("sampledPlayers=" + snap.sampledPlayers());
    sender.sendMessage("sampleRuns=" + snap.sampleRuns());
    sender.sendMessage("sampleUpdates=" + snap.sampleUpdates());
    sender.sendMessage("serviceProbeRuns=" + snap.serviceProbeRuns());
    sender.sendMessage("disabledSkips=" + snap.disabledSkips());
    sender.sendMessage("noPlayerSkips=" + snap.noPlayerSkips());
    sender.sendMessage("noSampleSkips=" + snap.noSampleSkips());
    sender.sendMessage("staleSkips=" + snap.staleSkips());
    sender.sendMessage("belowThresholdSkips=" + snap.belowThresholdSkips());
    sender.sendMessage("bypassSkips=" + snap.bypassSkips());
    sender.sendMessage("qualitySkips=" + snap.qualitySkips());
    sender.sendMessage("packetsObserved=" + snap.packetsObserved());
    sender.sendMessage("packetsDropped=" + snap.packetsDropped());
    sender.sendMessage("dropRate=" + String.format(java.util.Locale.ROOT, "%.2f%%", snap.dropRate() * 100.0d));
  }

  private Player resolvePacketCullTarget(CommandSender sender, dev.jorel.commandapi.executors.CommandArguments args) {
    Player target = (Player) args.getOptional("player").orElse(null);
    if (target != null) return target;
    if (sender instanceof Player player) return player;
    sender.sendMessage("Console usage: /zakum packetcull <sample|refresh> <player>");
    return null;
  }

  private void registerPerfModeCommand() {
    new CommandAPICommand("perfmode")
      .withPermission("zakum.perfmode")
      .withArguments(new StringArgument("mode")
        .replaceSuggestions((info, builder) -> {
          builder.suggest("auto");
          builder.suggest("on");
          builder.suggest("off");
          return builder.buildFuture();
        }))
      .withOptionalArguments(new EntitySelectorArgument.OnePlayer("player"))
      .executes((CommandExecutor) (sender, args) -> {
        ZakumPlugin corePlugin = requireCore(sender);
        if (corePlugin == null) return;
        PlayerVisualModeService visualModes = corePlugin.getVisualModeService();
        if (visualModes == null) {
          sender.sendMessage("Performance mode service is not available.");
          return;
        }

        String rawMode = (String) args.get("mode");
        PlayerVisualModeService.Mode mode = PlayerVisualModeService.Mode.fromInput(rawMode);
        Player target = (Player) args.getOptional("player").orElse(null);
        if (target != null) {
          if (!sender.hasPermission("zakum.admin")) {
            sender.sendMessage("No permission.");
            return;
          }
        } else if (sender instanceof Player player) {
          target = player;
        } else {
          sender.sendMessage("Console usage: /perfmode <auto|on|off> <player>");
          return;
        }

        visualModes.setMode(target.getUniqueId(), mode);
        sender.sendMessage("Performance mode for " + target.getName() + " set to " + mode.displayName() + ".");
        if (!target.equals(sender)) {
          target.sendMessage("Your performance mode is now " + mode.displayName() + ".");
        }
      })
      .register();
  }

  private CommandAPICommand entitlementsCommand() {
    CommandAPICommand ent = new CommandAPICommand("entitlements");

    ent.withSubcommand(new CommandAPICommand("check")
      .withArguments(new EntitySelectorArgument.OnePlayer("player"))
      .withArguments(scopeArg())
      .withArguments(new StringArgument("key"))
      .withOptionalArguments(new StringArgument("serverId"))
      .executes((CommandExecutor) (sender, args) -> {
        Player p = (Player) args.get("player");
        EntitlementScope scope = parseScope((String) args.get("scope"));
        String key = (String) args.get("key");
        String serverId = (String) args.getOptional("serverId").orElse(null);

        String effectiveServer = (scope == EntitlementScope.NETWORK) ? null : (serverId != null ? serverId : api.settings().server().id());
        UUID uuid = p.getUniqueId();

        api.entitlements().has(uuid, scope, effectiveServer, key).whenComplete((ok, err) -> {
          ZakumApi.get().getScheduler().runTask(this, () -> {
            if (err != null) {
              sender.sendMessage(ChatColor.RED + "Error: " + err.getMessage());
              return;
            }
            sender.sendMessage(ChatColor.AQUA + "Entitlement " + ChatColor.GRAY + key + ChatColor.AQUA + " = " + (ok ? ChatColor.GREEN + "true" : ChatColor.RED + "false"));
          });
        });
      }));

    ent.withSubcommand(new CommandAPICommand("grant")
      .withArguments(new EntitySelectorArgument.OnePlayer("player"))
      .withArguments(scopeArg())
      .withArguments(new StringArgument("key"))
      .withOptionalArguments(new StringArgument("serverId"))
      .withOptionalArguments(new LongArgument("expiresAtEpochSeconds", 0, Long.MAX_VALUE))
      .executes((CommandExecutor) (sender, args) -> {
        Player p = (Player) args.get("player");
        EntitlementScope scope = parseScope((String) args.get("scope"));
        String key = (String) args.get("key");
        String serverId = (String) args.getOptional("serverId").orElse(null);
        Long expires = (Long) args.getOptional("expiresAtEpochSeconds").orElse(null);

        String effectiveServer = (scope == EntitlementScope.NETWORK) ? null : (serverId != null ? serverId : api.settings().server().id());
        api.entitlements().grant(p.getUniqueId(), scope, effectiveServer, key, expires).whenComplete((v, err) -> {
          ZakumApi.get().getScheduler().runTask(this, () -> {
            if (err != null) sender.sendMessage(ChatColor.RED + "Error: " + err.getMessage());
            else sender.sendMessage(ChatColor.GREEN + "Granted " + key + " to " + p.getName());
          });
        });
      }));

    ent.withSubcommand(new CommandAPICommand("revoke")
      .withArguments(new EntitySelectorArgument.OnePlayer("player"))
      .withArguments(scopeArg())
      .withArguments(new StringArgument("key"))
      .withOptionalArguments(new StringArgument("serverId"))
      .executes((CommandExecutor) (sender, args) -> {
        Player p = (Player) args.get("player");
        EntitlementScope scope = parseScope((String) args.get("scope"));
        String key = (String) args.get("key");
        String serverId = (String) args.getOptional("serverId").orElse(null);

        String effectiveServer = (scope == EntitlementScope.NETWORK) ? null : (serverId != null ? serverId : api.settings().server().id());
        api.entitlements().revoke(p.getUniqueId(), scope, effectiveServer, key).whenComplete((v, err) -> {
          ZakumApi.get().getScheduler().runTask(this, () -> {
            if (err != null) sender.sendMessage(ChatColor.RED + "Error: " + err.getMessage());
            else sender.sendMessage(ChatColor.GREEN + "Revoked " + key + " from " + p.getName());
          });
        });
      }));

    ent.withSubcommand(new CommandAPICommand("invalidate")
      .withArguments(new EntitySelectorArgument.OnePlayer("player"))
      .executes((CommandExecutor) (sender, args) -> {
        Player p = (Player) args.get("player");
        api.entitlements().invalidate(p.getUniqueId());
        sender.sendMessage(ChatColor.GREEN + "Invalidated entitlement cache for " + p.getName());
      }));

    return ent;
  }

  private CommandAPICommand boostersCommand() {
    CommandAPICommand b = new CommandAPICommand("boosters");

    b.withSubcommand(new CommandAPICommand("multiplier")
      .withArguments(new EntitySelectorArgument.OnePlayer("player"))
      .withArguments(scopeArg())
      .withArguments(kindArg())
      .withOptionalArguments(new StringArgument("serverId"))
      .executes((CommandExecutor) (sender, args) -> {
        Player p = (Player) args.get("player");
        EntitlementScope scope = parseScope((String) args.get("scope"));
        BoosterKind kind = parseKind((String) args.get("kind"));
        String serverId = (String) args.getOptional("serverId").orElse(null);

        String effectiveServer = (scope == EntitlementScope.NETWORK) ? null : (serverId != null ? serverId : api.settings().server().id());
        double mult = api.boosters().multiplier(p.getUniqueId(), scope, effectiveServer, kind);

        sender.sendMessage(ChatColor.AQUA + "Multiplier " + ChatColor.GRAY + kind + ChatColor.AQUA + " = " + ChatColor.WHITE + mult);
      }));

    b.withSubcommand(new CommandAPICommand("grant_all")
      .withArguments(scopeArg())
      .withArguments(kindArg())
      .withArguments(new DoubleArgument("multiplier", 0.0, 1000.0))
      .withArguments(new LongArgument("durationSeconds", 1, 365L * 24 * 60 * 60))
      .withOptionalArguments(new StringArgument("serverId"))
      .executes((CommandExecutor) (sender, args) -> {
        EntitlementScope scope = parseScope((String) args.get("scope"));
        BoosterKind kind = parseKind((String) args.get("kind"));
        double mult = (double) args.get("multiplier");
        long duration = (long) args.get("durationSeconds");
        String serverId = (String) args.getOptional("serverId").orElse(null);

        String effectiveServer = (scope == EntitlementScope.NETWORK) ? null : (serverId != null ? serverId : api.settings().server().id());

        api.boosters().grantToAll(scope, effectiveServer, kind, mult, duration).whenComplete((v, err) -> {
          ZakumApi.get().getScheduler().runTask(this, () -> {
            if (err != null) sender.sendMessage(ChatColor.RED + "Error: " + err.getMessage());
            else sender.sendMessage(ChatColor.GREEN + "Granted ALL booster " + kind + " x" + mult + " for " + duration + "s");
          });
        });
      }));

    b.withSubcommand(new CommandAPICommand("grant_player")
      .withArguments(new EntitySelectorArgument.OnePlayer("player"))
      .withArguments(scopeArg())
      .withArguments(kindArg())
      .withArguments(new DoubleArgument("multiplier", 0.0, 1000.0))
      .withArguments(new LongArgument("durationSeconds", 1, 365L * 24 * 60 * 60))
      .withOptionalArguments(new StringArgument("serverId"))
      .executes((CommandExecutor) (sender, args) -> {
        Player p = (Player) args.get("player");
        EntitlementScope scope = parseScope((String) args.get("scope"));
        BoosterKind kind = parseKind((String) args.get("kind"));
        double mult = (double) args.get("multiplier");
        long duration = (long) args.get("durationSeconds");
        String serverId = (String) args.getOptional("serverId").orElse(null);

        String effectiveServer = (scope == EntitlementScope.NETWORK) ? null : (serverId != null ? serverId : api.settings().server().id());

        api.boosters().grantToPlayer(p.getUniqueId(), scope, effectiveServer, kind, mult, duration).whenComplete((v, err) -> {
          ZakumApi.get().getScheduler().runTask(this, () -> {
            if (err != null) sender.sendMessage(ChatColor.RED + "Error: " + err.getMessage());
            else sender.sendMessage(ChatColor.GREEN + "Granted booster " + kind + " to " + p.getName() + " x" + mult + " for " + duration + "s");
          });
        });
      }));

    // --- Ops tools ---
    b.withSubcommand(new CommandAPICommand("list_all")
      .withArguments(scopeArg())
      .withArguments(kindArg())
      .withOptionalArguments(new StringArgument("serverId"))
      .executes((CommandExecutor) (sender, args) -> {
        EntitlementScope scope = parseScope((String) args.get("scope"));
        BoosterKind kind = parseKind((String) args.get("kind"));
        String serverId = (String) args.getOptional("serverId").orElse(null);
        String effectiveServer = (scope == EntitlementScope.NETWORK) ? null : (serverId != null ? serverId : api.settings().server().id());

        listBoosters(sender, null, scope, effectiveServer, kind, true);
      }));

    b.withSubcommand(new CommandAPICommand("list_player")
      .withArguments(new EntitySelectorArgument.OnePlayer("player"))
      .withArguments(scopeArg())
      .withArguments(kindArg())
      .withOptionalArguments(new StringArgument("serverId"))
      .executes((CommandExecutor) (sender, args) -> {
        Player p = (Player) args.get("player");
        EntitlementScope scope = parseScope((String) args.get("scope"));
        BoosterKind kind = parseKind((String) args.get("kind"));
        String serverId = (String) args.getOptional("serverId").orElse(null);
        String effectiveServer = (scope == EntitlementScope.NETWORK) ? null : (serverId != null ? serverId : api.settings().server().id());

        listBoosters(sender, p.getUniqueId(), scope, effectiveServer, kind, false);
      }));

    b.withSubcommand(new CommandAPICommand("clear_all")
      .withArguments(scopeArg())
      .withArguments(kindArg())
      .withOptionalArguments(new StringArgument("serverId"))
      .executes((CommandExecutor) (sender, args) -> {
        EntitlementScope scope = parseScope((String) args.get("scope"));
        BoosterKind kind = parseKind((String) args.get("kind"));
        String serverId = (String) args.getOptional("serverId").orElse(null);
        String effectiveServer = (scope == EntitlementScope.NETWORK) ? null : (serverId != null ? serverId : api.settings().server().id());

        clearBoosters(sender, null, scope, effectiveServer, kind, true);
      }));

    b.withSubcommand(new CommandAPICommand("clear_player")
      .withArguments(new EntitySelectorArgument.OnePlayer("player"))
      .withArguments(scopeArg())
      .withArguments(kindArg())
      .withOptionalArguments(new StringArgument("serverId"))
      .executes((CommandExecutor) (sender, args) -> {
        Player p = (Player) args.get("player");
        EntitlementScope scope = parseScope((String) args.get("scope"));
        BoosterKind kind = parseKind((String) args.get("kind"));
        String serverId = (String) args.getOptional("serverId").orElse(null);
        String effectiveServer = (scope == EntitlementScope.NETWORK) ? null : (serverId != null ? serverId : api.settings().server().id());

        clearBoosters(sender, p.getUniqueId(), scope, effectiveServer, kind, false);
      }));

    b.withSubcommand(new CommandAPICommand("purge")
      .executes((CommandExecutor) (sender, args) -> purgeExpiredBoosters(sender)));

    return b;
  }

  private void listBoosters(CommandSender sender, UUID playerId, EntitlementScope scope, String serverId, BoosterKind kind, boolean allTarget) {
    if (api.database().state() != DatabaseState.ONLINE) {
      sender.sendMessage(ChatColor.RED + "DB is offline.");
      return;
    }

    api.async().execute(() -> {
      long now = java.time.Instant.now().getEpochSecond();
      var jdbc = api.database().jdbc();

      String target = allTarget ? "ALL" : "PLAYER";

      StringBuilder sql = new StringBuilder();
      sql.append("SELECT multiplier, expires_at FROM zakum_boosters WHERE expires_at > ? AND target=? AND scope=? AND kind=?");
      var params = new java.util.ArrayList<Object>();
      params.add(now);
      params.add(target);
      params.add(scope.name());
      params.add(kind.name());

      if (scope == EntitlementScope.NETWORK) {
        sql.append(" AND server_id IS NULL");
      } else {
        sql.append(" AND server_id=?");
        params.add(serverId);
      }

      if (!allTarget) {
        sql.append(" AND uuid=?");
        params.add(net.orbis.zakum.core.util.UuidBytes.toBytes(playerId));
      }

      sql.append(" ORDER BY expires_at DESC LIMIT 20");

      var rows = jdbc.query(sql.toString(), rs -> new BoosterRow(rs.getDouble(1), rs.getLong(2)), params.toArray());

      ZakumApi.get().getScheduler().runTask(this, () -> {
        if (rows.isEmpty()) {
          sender.sendMessage(ChatColor.GRAY + "No active boosters found.");
          return;
        }
        sender.sendMessage(ChatColor.AQUA + "Active boosters " + ChatColor.GRAY + "(" + target + ") " + ChatColor.WHITE + kind + ChatColor.GRAY + " scope=" + scope + (scope == EntitlementScope.SERVER ? (" serverId=" + serverId) : ""));
        long now2 = java.time.Instant.now().getEpochSecond();
        for (BoosterRow r : rows) {
          long in = Math.max(0, r.expiresAt() - now2);
          sender.sendMessage(ChatColor.GRAY + "- x" + r.multiplier() + " expiresAt=" + r.expiresAt() + " (in " + in + "s)");
        }
      });
    });
  }

  private record BoosterRow(double multiplier, long expiresAt) {}

  private void clearBoosters(CommandSender sender, UUID playerId, EntitlementScope scope, String serverId, BoosterKind kind, boolean allTarget) {
    if (api.database().state() != DatabaseState.ONLINE) {
      sender.sendMessage(ChatColor.RED + "DB is offline.");
      return;
    }

    api.async().execute(() -> {
      long now = java.time.Instant.now().getEpochSecond();
      var jdbc = api.database().jdbc();

      String target = allTarget ? "ALL" : "PLAYER";

      StringBuilder sql = new StringBuilder();
      sql.append("DELETE FROM zakum_boosters WHERE expires_at > ? AND target=? AND scope=? AND kind=?");
      var params = new java.util.ArrayList<Object>();
      params.add(now);
      params.add(target);
      params.add(scope.name());
      params.add(kind.name());

      if (scope == EntitlementScope.NETWORK) {
        sql.append(" AND server_id IS NULL");
      } else {
        sql.append(" AND server_id=?");
        params.add(serverId);
      }

      if (!allTarget) {
        sql.append(" AND uuid=?");
        params.add(net.orbis.zakum.core.util.UuidBytes.toBytes(playerId));
      }

      int deleted = jdbc.update(sql.toString(), params.toArray());

      // Ensure caches refresh quickly.
      if (api.boosters() instanceof SqlBoosterService s) {
        s.refreshNowAsync();
      }

      ZakumApi.get().getScheduler().runTask(this, () -> sender.sendMessage(ChatColor.GREEN + "Cleared " + deleted + " booster row(s)."));
    });
  }

  private void purgeExpiredBoosters(CommandSender sender) {
    if (api.database().state() != DatabaseState.ONLINE) {
      sender.sendMessage(ChatColor.RED + "DB is offline.");
      return;
    }

    api.async().execute(() -> {
      long now = java.time.Instant.now().getEpochSecond();
      var jdbc = api.database().jdbc();

      int total = 0;
      final int limit = 5000;
      final int maxLoops = 20;
      for (int i = 0; i < maxLoops; i++) {
        int n = jdbc.update("DELETE FROM zakum_boosters WHERE expires_at <= ? LIMIT " + limit, now);
        total += n;
        if (n < limit) break;
      }

      if (api.boosters() instanceof SqlBoosterService s) {
        s.refreshNowAsync();
      }

      int done = total;
      ZakumApi.get().getScheduler().runTask(this, () -> sender.sendMessage(ChatColor.GREEN + "Purged " + done + " expired booster row(s)."));
    });
  }

  private ZakumPlugin requireCore(CommandSender sender) {
    if (core == null) {
      sender.sendMessage(ChatColor.RED + "Core ops unavailable (ZakumPlugin missing).");
    }
    return core;
  }

  private static String formatEpochMillis(long epochMillis) {
    if (epochMillis <= 0L) return "never";
    return Instant.ofEpochMilli(epochMillis).toString();
  }

  private static UUID resolveUuid(CommandSender sender, String token) {
    if (token == null || token.isBlank()) return null;
    if (token.contains("-")) {
      try { return UUID.fromString(token); } catch (Exception ignored) {}
    }
    OfflinePlayer op = Bukkit.getOfflinePlayer(token);
    UUID id = op.getUniqueId();
    if (id == null) sender.sendMessage("Unknown player: " + token);
    return id;
  }

  private static String formatMoney(double value) {
    if (!Double.isFinite(value)) return "0.00";
    return String.format(java.util.Locale.ROOT, "%.2f", value);
  }

  private static String colorDb(DatabaseState state) {
    if (state == DatabaseState.ONLINE) return ChatColor.GREEN.toString();
    if (state == DatabaseState.OFFLINE) return ChatColor.RED.toString();
    return ChatColor.YELLOW.toString();
  }

  private static Argument<String> scopeArg() {
    return new StringArgument("scope")
      .replaceSuggestions((info, builder) -> {
        builder.suggest("SERVER");
        builder.suggest("NETWORK");
        return builder.buildFuture();
      });
  }

  private static Argument<String> kindArg() {
    return new StringArgument("kind")
      .replaceSuggestions((info, builder) -> {
        for (BoosterKind k : BoosterKind.values()) builder.suggest(k.name());
        return builder.buildFuture();
      });
  }

  private static EntitlementScope parseScope(String s) {
    Objects.requireNonNull(s, "scope");
    String u = s.trim().toUpperCase(Locale.ROOT);
    if (u.equals("NETWORK") || u.equals("GLOBAL")) return EntitlementScope.NETWORK;
    return EntitlementScope.SERVER;
  }

  private static BoosterKind parseKind(String s) {
    Objects.requireNonNull(s, "kind");
    return BoosterKind.valueOf(s.trim().toUpperCase(Locale.ROOT));
  }
}

