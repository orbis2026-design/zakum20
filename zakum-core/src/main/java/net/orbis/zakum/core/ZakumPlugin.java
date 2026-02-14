package net.orbis.zakum.core;

import net.orbis.zakum.api.ServerIdentity;
import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.api.config.ZakumSettings;
import net.orbis.zakum.api.actions.DeferredActionService;
import net.orbis.zakum.core.actions.DeferredActionReplayListener;
import net.orbis.zakum.core.config.ZakumSettingsLoader;
import net.orbis.zakum.core.actions.SimpleActionBus;
import net.orbis.zakum.core.actions.SqlDeferredActionService;
import net.orbis.zakum.core.actions.emitters.*;
import net.orbis.zakum.core.boosters.SqlBoosterService;
import net.orbis.zakum.core.db.SqlManager;
import net.orbis.zakum.core.entitlements.SqlEntitlementService;
import net.orbis.zakum.core.net.HttpControlPlaneClient;
import net.orbis.zakum.core.obs.MetricsService;
import net.orbis.zakum.core.util.Async;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Locale;
import java.util.Arrays;
import org.bukkit.plugin.Plugin;

import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;

public final class ZakumPlugin extends JavaPlugin {

  private ZakumApiImpl api;
  private ZakumSettings settings;
  private MetricsService metrics;
  private SqlManager sql;
  private ExecutorService asyncPool;

  private SimpleActionBus actionBus;
  private DeferredActionService deferred;
  private SqlEntitlementService entitlements;
  private SqlBoosterService boosters;

  private MovementSampler movementSampler;

  @Override
  public void onEnable() {
    saveDefaultConfig();
    reloadConfig();

    this.settings = ZakumSettingsLoader.load(getConfig());
    var serverId = settings.server().id();
    if (serverId.isBlank()) {
      getLogger().severe("config.yml server.id is required. Disabling Zakum.");
      getServer().getPluginManager().disablePlugin(this);
      return;
    }

    var clock = Clock.systemUTC();
    this.asyncPool = Async.newSharedPool(getLogger());
    var async = this.asyncPool;

    this.metrics = new MetricsService(getLogger(), settings.observability().metrics(), async);
    this.metrics.start();

    this.sql = new SqlManager(this, async, clock, settings, metrics.registry());
    this.sql.start();

    var controlPlane = HttpControlPlaneClient.fromSettings(settings, async);

    this.actionBus = new SimpleActionBus();

    this.deferred = new SqlDeferredActionService(this, sql, async);

        long entCacheMax = settings.entitlements().cache().maximumSize();
    long entTtlSeconds = settings.entitlements().cache().ttlSeconds();
    this.entitlements = new SqlEntitlementService(sql, async, (int) entCacheMax, Duration.ofSeconds(entTtlSeconds));

    this.boosters = new SqlBoosterService(this, sql, async, settings.boosters());
    this.boosters.start();

    this.api = new ZakumApiImpl(
      this,
      new ServerIdentity(serverId),
      clock,
      async,
      sql,
      controlPlane,
      actionBus,
      entitlements,
      boosters,
      settings
    );

    Bukkit.getServicesManager().register(ZakumApi.class, api, this, ServicePriority.Highest);
    Bukkit.getServicesManager().register(net.orbis.zakum.api.actions.ActionBus.class, actionBus, this, ServicePriority.Highest);
    Bukkit.getServicesManager().register(net.orbis.zakum.api.entitlements.EntitlementService.class, entitlements, this, ServicePriority.Highest);
    Bukkit.getServicesManager().register(net.orbis.zakum.api.boosters.BoosterService.class, boosters, this, ServicePriority.Highest);

    Bukkit.getServicesManager().register(DeferredActionService.class, deferred, this, ServicePriority.Highest);

    registerCoreActionEmitters(clock);

    getLogger().info("Zakum enabled. server.id=" + serverId + " db=" + sql.state());
  }

  @Override
  public void onDisable() {
    var sm = Bukkit.getServicesManager();

    if (api != null) sm.unregister(ZakumApi.class, api);
    if (actionBus != null) sm.unregister(net.orbis.zakum.api.actions.ActionBus.class, actionBus);
    if (entitlements != null) sm.unregister(net.orbis.zakum.api.entitlements.EntitlementService.class, entitlements);
    if (boosters != null) sm.unregister(net.orbis.zakum.api.boosters.BoosterService.class, boosters);
    if (deferred != null) sm.unregister(DeferredActionService.class, deferred);

    if (movementSampler != null) {
      movementSampler.stop();
      movementSampler = null;
    }

    if (metrics != null) metrics.stop();

    if (boosters != null) boosters.shutdown();

    if (sql != null) sql.shutdown();
    if (asyncPool != null) asyncPool.shutdownNow();

    api = null;
    settings = null;
    metrics = null;
    sql = null;
    asyncPool = null;
    actionBus = null;
    entitlements = null;
    boosters = null;
    deferred = null;
  }

  private boolean isEnabled(String name) {
    Plugin p = getServer().getPluginManager().getPlugin(name);
    return p != null && p.isEnabled();
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!command.getName().equalsIgnoreCase("zakum")) return false;

    if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
      sender.sendMessage("§bZakum§7: server=" + api.server().serverId()
        + " db=" + sql.state()
        + " poolActive=" + sql.poolStats().active()
        + " poolIdle=" + sql.poolStats().idle()
      );
      return true;
    }

    if (args[0].equalsIgnoreCase("capabilities") || args[0].equalsIgnoreCase("caps")) {
      var sb = new StringBuilder();
      sb.append("§bZakum§7 capabilities\n");
      sb.append("§7Core: §fserver=").append(api.server().serverId())
        .append(" §7db=").append(sql.state()).append("\n");
      sb.append("§7Packets: §f").append(settings.packets().enabled() ? settings.packets().backend().name() : "DISABLED")
        .append(" §7PacketEvents=").append(isEnabled("PacketEvents")).append("\n");

      // Orbis feature plugins
      String[] features = { "OrbisBattlePass", "OrbisCrates", "OrbisPets", "OrbisMiniPets", "OrbisEssentials" };
      sb.append("§7Features: ");
      for (int i = 0; i < features.length; i++) {
        String n = features[i];
        sb.append(isEnabled(n) ? "§a" : "§c").append(n);
        if (i + 1 < features.length) sb.append("§7, ");
      }
      sb.append("\n");

      // Bridges
      String[] bridges = { "OrbisBridgeLuckPerms", "OrbisBridgeVault", "OrbisBridgePlaceholderAPI", "OrbisBridgeCitizens",
        "OrbisBridgeMythicMobs", "OrbisBridgeJobs", "OrbisBridgeSuperiorSkyblock2", "OrbisBridgeVotifier", "OrbisBridgeEssentialsX", "OrbisBridgeCommandAPI" };
      sb.append("§7Bridges: ");
      for (int i = 0; i < bridges.length; i++) {
        String n = bridges[i];
        sb.append(isEnabled(n) ? "§a" : "§c").append(n);
        if (i + 1 < bridges.length) sb.append("§7, ");
      }
      sb.append("\n");

      // External deps (presence)
      String[] ext = { "LuckPerms", "Vault", "PlaceholderAPI", "Citizens", "MythicMobs", "Jobs", "SuperiorSkyblock2", "Votifier", "NuVotifier", "Essentials", "CommandAPI" };
      sb.append("§7External: ");
      for (int i = 0; i < ext.length; i++) {
        String n = ext[i];
        sb.append(isEnabled(n) ? "§a" : "§8").append(n);
        if (i + 1 < ext.length) sb.append("§7, ");
      }
      sb.append("\n");

      // Action emitters
      var a = settings.actions();
      sb.append("§7Actions: §f").append(a.enabled()).append("§7 movement=").append(a.movement().enabled()).append("\n");
      var e = a.emitters();
      sb.append("§7Emitters: §f")
        .append("joinQuit=").append(e.joinQuit()).append(" ")
        .append("onlineTime=").append(e.onlineTime()).append(" ")
        .append("blockBreak=").append(e.blockBreak()).append(" ")
        .append("blockPlace=").append(e.blockPlace()).append(" ")
        .append("mobKill=").append(e.mobKill()).append(" ")
        .append("playerDeath=").append(e.playerDeath()).append(" ")
        .append("playerKill=").append(e.playerKill()).append(" ")
        .append("xpGain=").append(e.xpGain()).append(" ")
        .append("levelChange=").append(e.levelChange()).append(" ")
        .append("craft=").append(e.itemCraft()).append(" ")
        .append("smelt=").append(e.smeltExtract()).append(" ")
        .append("fish=").append(e.fishCatch()).append(" ")
        .append("enchant=").append(e.itemEnchant()).append(" ")
        .append("consume=").append(e.itemConsume()).append(" ")
        .append("advancement=").append(e.advancement()).append(" ")
        .append("commandUse=").append(e.commandUse().enabled())
        .append("\n");

      sender.sendMessage(sb.toString());
      return true;
    }

    if (args[0].equalsIgnoreCase("reconnect")) {
      sql.requestReconnectNow();
      sender.sendMessage("§eZakum§7: reconnect requested.");
      return true;
    }

    if (args[0].equalsIgnoreCase("booster") || args[0].equalsIgnoreCase("boosters")) {
      String[] subArgs = java.util.Arrays.copyOfRange(args, 1, args.length);
      return handleBoosterCommand(sender, subArgs);
    }

    sender.sendMessage("§cUsage: /zakum status | /zakum reconnect | /zakum capabilities | /zakum booster");
    return true;
  }

  private boolean handleBoosterCommand(CommandSender sender, String[] args) {
    if (sql.state() != net.orbis.zakum.api.db.DatabaseState.ONLINE) {
      sender.sendMessage("§cZakum§7: DB is not online; boosters admin is unavailable.");
      return true;
    }
    if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
      sender.sendMessage("§bZakum§7 boosters\n" +
        "§7- §f/zakum booster list [player <name|uuid>] [kind]\n" +
        "§7- §f/zakum booster grant all <kind> <multiplier> <duration> [scope] [serverId]\n" +
        "§7- §f/zakum booster grant player <name|uuid> <kind> <multiplier> <duration> [scope] [serverId]\n" +
        "§7- §f/zakum booster clear all <kind|*> [scope] [serverId]\n" +
        "§7- §f/zakum booster clear player <name|uuid> <kind|*> [scope] [serverId]\n" +
        "§8duration examples: 3600, 30m, 2h, 7d    scope: SERVER or NETWORK");
      return true;
    }

    String sub = args[0].toLowerCase(java.util.Locale.ROOT);
    if (sub.equals("list")) {
      UUID player = null;
      net.orbis.zakum.api.boosters.BoosterKind kind = null;
      int idx = 1;

      if (args.length >= 3 && args[1].equalsIgnoreCase("player")) {
        player = resolveUuid(sender, args[2]);
        idx = 3;
      }

      if (args.length > idx) {
        kind = parseBoosterKind(sender, args[idx]);
        if (kind == null) return true;
      }

      boosters.listActive(player, kind, 50).whenComplete((rows, err) -> {
        Bukkit.getScheduler().runTask(this, () -> {
          if (err != null) {
            sender.sendMessage("§cZakum§7: list failed: " + err.getClass().getSimpleName());
            return;
          }
          if (rows == null || rows.isEmpty()) {
            sender.sendMessage("§7No active boosters.");
            return;
          }
          sender.sendMessage("§bActive Boosters§7 (" + rows.size() + ")");
          for (var r : rows) {
            String sid = (r.serverId() == null ? "(network)" : r.serverId());
            String who = r.target().equals("ALL") ? "ALL" : (r.uuid() == null ? "?" : r.uuid().toString());
            long secondsLeft = Math.max(0L, r.expiresAtEpochSeconds() - java.time.Instant.now().getEpochSecond());
            sender.sendMessage("§7- §f" + r.kind() + " §7x§f" + r.multiplier() + " §7target=§f" + who +
              " §7scope=§f" + r.scope() + " §7server=§f" + sid + " §7left=§f" + formatDuration(secondsLeft));
          }
        });
      });
      return true;
    }

    if (sub.equals("grant")) {
      if (args.length < 5) {
        sender.sendMessage("§cUsage: /zakum booster grant all|player ... (see /zakum booster help)");
        return true;
      }

      String target = args[1].toLowerCase(java.util.Locale.ROOT);
      int i = 2;
      UUID player = null;
      if (target.equals("player")) {
        if (args.length < 6) {
          sender.sendMessage("§cUsage: /zakum booster grant player <name|uuid> <kind> <multiplier> <duration> [scope] [serverId]");
          return true;
        }
        player = resolveUuid(sender, args[i++]);
      } else if (!target.equals("all")) {
        sender.sendMessage("§cTarget must be 'all' or 'player'.");
        return true;
      }

      var kind = parseBoosterKind(sender, args[i++]);
      if (kind == null) return true;

      Double mult = parseDouble(sender, args[i++], "multiplier");
      if (mult == null) return true;

      Long dur = parseDurationSeconds(sender, args[i++]);
      if (dur == null) return true;

      net.orbis.zakum.api.entitlements.EntitlementScope scope = net.orbis.zakum.api.entitlements.EntitlementScope.SERVER;
      if (args.length > i) {
        scope = parseScope(sender, args[i]);
        if (scope == null) return true;
        i++;
      }

      String serverId = api.server().serverId();
      if (scope == net.orbis.zakum.api.entitlements.EntitlementScope.SERVER && args.length > i) {
        serverId = args[i];
      }

      String createdBy = sender.getName();
      java.util.concurrent.CompletableFuture<?> fut = (player == null)
        ? boosters.grantToAllAsync(kind, mult, dur, scope, serverId, createdBy)
        : boosters.grantToPlayerAsync(player, kind, mult, dur, scope, serverId, createdBy);

      fut.whenComplete((ignored, err) -> Bukkit.getScheduler().runTask(this, () -> {
        if (err != null) {
          sender.sendMessage("§cZakum§7: grant failed: " + err.getClass().getSimpleName());
          return;
        }
        sender.sendMessage("§aZakum§7: booster granted." );
      }));
      return true;
    }

    if (sub.equals("clear")) {
      if (args.length < 3) {
        sender.sendMessage("§cUsage: /zakum booster clear all|player ... (see /zakum booster help)");
        return true;
      }

      String target = args[1].toLowerCase(java.util.Locale.ROOT);
      int i = 2;
      UUID player = null;
      if (target.equals("player")) {
        if (args.length < 4) {
          sender.sendMessage("§cUsage: /zakum booster clear player <name|uuid> <kind|*> [scope] [serverId]");
          return true;
        }
        player = resolveUuid(sender, args[i++]);
      } else if (!target.equals("all")) {
        sender.sendMessage("§cTarget must be 'all' or 'player'.");
        return true;
      }

      net.orbis.zakum.api.boosters.BoosterKind kind = null;
      if (args.length > i && !args[i].equals("*") && !args[i].equalsIgnoreCase("all")) {
        kind = parseBoosterKind(sender, args[i]);
        if (kind == null) return true;
      }
      i++;

      net.orbis.zakum.api.entitlements.EntitlementScope scope = net.orbis.zakum.api.entitlements.EntitlementScope.SERVER;
      if (args.length > i) {
        scope = parseScope(sender, args[i]);
        if (scope == null) return true;
        i++;
      }

      String serverId = api.server().serverId();
      if (scope == net.orbis.zakum.api.entitlements.EntitlementScope.SERVER && args.length > i) {
        serverId = args[i];
      }

      java.util.concurrent.CompletableFuture<Integer> fut = (player == null)
        ? boosters.clearAllAsync(scope, serverId, kind)
        : boosters.clearPlayerAsync(player, scope, serverId, kind);

      fut.whenComplete((count, err) -> Bukkit.getScheduler().runTask(this, () -> {
        if (err != null) {
          sender.sendMessage("§cZakum§7: clear failed: " + err.getClass().getSimpleName());
          return;
        }
        sender.sendMessage("§aZakum§7: cleared " + (count == null ? 0 : count) + " booster row(s)." );
      }));
      return true;
    }

    sender.sendMessage("§cUsage: /zakum booster help");
    return true;
  }

  private static net.orbis.zakum.api.boosters.BoosterKind parseBoosterKind(CommandSender sender, String s) {
    try {
      return net.orbis.zakum.api.boosters.BoosterKind.valueOf(s.toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      sender.sendMessage("§cUnknown kind. Valid: "+ java.util.Arrays.toString(net.orbis.zakum.api.boosters.BoosterKind.values()));
      return null;
    }
  }

  private static net.orbis.zakum.api.entitlements.EntitlementScope parseScope(CommandSender sender, String s) {
    String up = s.toUpperCase(java.util.Locale.ROOT);
    if (up.equals("GLOBAL")) up = "NETWORK";
    try {
      return net.orbis.zakum.api.entitlements.EntitlementScope.valueOf(up);
    } catch (IllegalArgumentException ex) {
      sender.sendMessage("§cUnknown scope. Use SERVER or NETWORK.");
      return null;
    }
  }

  private static Double parseDouble(CommandSender sender, String s, String label) {
    try {
      return Double.parseDouble(s);
    } catch (NumberFormatException ex) {
      sender.sendMessage("§cInvalid " + label + ": " + s);
      return null;
    }
  }

  private static Long parseDurationSeconds(CommandSender sender, String s) {
    try {
      s = s.trim().toLowerCase(java.util.Locale.ROOT);
      long mul = 1;
      if (s.endsWith("m")) { mul = 60; s = s.substring(0, s.length()-1); }
      else if (s.endsWith("h")) { mul = 3600; s = s.substring(0, s.length()-1); }
      else if (s.endsWith("d")) { mul = 86400; s = s.substring(0, s.length()-1); }
      else if (s.endsWith("s")) { mul = 1; s = s.substring(0, s.length()-1); }

      long v = Long.parseLong(s);
      if (v <= 0) {
        sender.sendMessage("§cDuration must be > 0.");
        return null;
      }
      return v * mul;
    } catch (Exception ex) {
      sender.sendMessage("§cInvalid duration: " + s + " (use 3600, 30m, 2h, 7d)");
      return null;
    }
  }

  private static String formatDuration(long seconds) {
    long s = seconds;
    long d = s / 86400; s %= 86400;
    long h = s / 3600; s %= 3600;
    long m = s / 60; s %= 60;
    StringBuilder sb = new StringBuilder();
    if (d > 0) sb.append(d).append("d");
    if (h > 0) sb.append(h).append("h");
    if (m > 0) sb.append(m).append("m");
    if (sb.length() == 0) sb.append(s).append("s");
    return sb.toString();
  }

  private static UUID resolveUuid(CommandSender sender, String token) {
    try {
      if (token.contains("-")) return UUID.fromString(token);
    } catch (IllegalArgumentException ignored) {}
    org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(token);
    UUID id = op.getUniqueId();
    if (id == null) {
      sender.sendMessage("§cUnknown player: " + token);
    }
    return id;
  }

  private void registerCoreActionEmitters(Clock clock) {
    var pm = getServer().getPluginManager();
    var a = settings.actions();

    if (a.deferredReplay().enabled()) {
      int lim = a.deferredReplay().claimLimit();
      pm.registerEvents(new DeferredActionReplayListener(this, api.server(), deferred, actionBus, lim), this);
    }

    if (!a.enabled()) return;

    var e = a.emitters();

    if (e.joinQuit()) pm.registerEvents(new PlayerLifecycleEmitter(actionBus), this);
    if (e.onlineTime()) pm.registerEvents(new OnlineTimeEmitter(actionBus, clock), this);
    if (e.blockBreak()) pm.registerEvents(new BlockBreakEmitter(actionBus), this);
    if (e.blockPlace()) pm.registerEvents(new BlockPlaceEmitter(actionBus), this);
    if (e.mobKill()) pm.registerEvents(new MobKillEmitter(actionBus), this);

    if (e.playerDeath() || e.playerKill()) {
      // both emitted by the same listener
      pm.registerEvents(new PlayerDeathEmitter(actionBus), this);
    }

    if (e.xpGain()) pm.registerEvents(new XpGainEmitter(actionBus), this);
    if (e.levelChange()) pm.registerEvents(new LevelChangeEmitter(actionBus), this);
    if (e.itemCraft()) pm.registerEvents(new CraftEmitter(actionBus), this);
    if (e.smeltExtract()) pm.registerEvents(new SmeltExtractEmitter(actionBus), this);
    if (e.fishCatch()) pm.registerEvents(new FishCatchEmitter(actionBus), this);
    if (e.itemEnchant()) pm.registerEvents(new EnchantEmitter(actionBus), this);
    if (e.itemConsume()) pm.registerEvents(new ConsumeEmitter(actionBus), this);
    if (e.advancement()) pm.registerEvents(new AdvancementEmitter(actionBus), this);

    if (e.commandUse().enabled()) {
      pm.registerEvents(new CommandUseEmitter(actionBus, e.commandUse().allowlist()), this);
    }

    if (a.movement().enabled()) {
      int ticks = a.movement().sampleTicks();
      long maxCm = a.movement().maxCmPerSample();

      this.movementSampler = new MovementSampler(this, actionBus, ticks, maxCm);
      pm.registerEvents(movementSampler, this);
      movementSampler.start();
    }
  }
}
