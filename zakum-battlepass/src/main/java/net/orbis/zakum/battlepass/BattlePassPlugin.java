package net.orbis.zakum.battlepass;

import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.api.actions.ActionEvent;
import net.orbis.zakum.api.actions.ActionSubscription;
import net.orbis.zakum.api.db.DatabaseState;
import net.orbis.zakum.api.util.AtomicFiles;
import net.orbis.zakum.api.util.FileBackups;
import net.orbis.zakum.battlepass.backup.BattlePassBackupService;
import net.orbis.zakum.battlepass.backup.BattlePassBatchFiles;
import net.orbis.zakum.battlepass.db.BattlePassSchema;
import net.orbis.zakum.battlepass.leaderboard.BattlePassLeaderboard;
import net.orbis.zakum.battlepass.listener.BpPlayerListener;
import net.orbis.zakum.battlepass.papi.OrbisBattlePassExpansion;
import net.orbis.zakum.battlepass.rewards.RewardTrack;
import net.orbis.zakum.battlepass.ui.BattlePassMenuListener;
import net.orbis.zakum.battlepass.ui.BattlePassMenus;
import net.orbis.zakum.battlepass.editor.BattlePassEditor;
import net.orbis.zakum.battlepass.editor.BattlePassEditorChatListener;
import net.orbis.zakum.battlepass.editor.BattlePassEditorListener;
import net.orbis.zakum.battlepass.editor.EditorSessionManager;

import net.orbis.zakum.battlepass.ui.NameCache;
import net.orbis.zakum.battlepass.yaml.BattlePassYamlStore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BattlePassPlugin extends JavaPlugin {

  private ZakumApi zakum;
  private BattlePassRuntime runtime;

  private NameCache names;
  private BattlePassLeaderboard leaderboard;
  private BattlePassMenus menus;

  private BpPlayerListener playerListener;
  private BattlePassMenuListener menuListener;
// Admin YAML editors (event-driven, no refresh loops)
private BattlePassEditor editor;
private BattlePassEditorListener editorListener;
private BattlePassEditorChatListener editorChatListener;
private EditorSessionManager editorSessions;


  private OrbisBattlePassExpansion expansion;

  private ActionSubscription npcSub;
  private final java.util.concurrent.ConcurrentHashMap<java.util.UUID, Long> npcOpenCooldownMs = new java.util.concurrent.ConcurrentHashMap<>();

  private int flushTaskId = -1;
  private int premiumTaskId = -1;
  private int leaderboardTaskId = -1;

  // Long-uptime safety: prevent overlapping destructive admin jobs.
  private final java.util.concurrent.atomic.AtomicBoolean adminJobRunning = new java.util.concurrent.atomic.AtomicBoolean(false);

  // Maintenance mode: suppress periodic tasks/flushes while running destructive operations.
  private final java.util.concurrent.atomic.AtomicBoolean maintenanceMode = new java.util.concurrent.atomic.AtomicBoolean(false);

  private volatile boolean schemaEnsured = false;
  private int schemaTaskId = -1;

  @Override
  public void onEnable() {
    saveDefaultConfig();

    this.zakum = Bukkit.getServicesManager().load(ZakumApi.class);
    if (zakum == null) {
      getLogger().severe("ZakumApi not found. Did Zakum load? Disabling OrbisBattlePass.");
      getServer().getPluginManager().disablePlugin(this);
      return;
    }

    if (zakum.database().state() == DatabaseState.ONLINE) {
      BattlePassSchema.ensureTables(zakum.database().jdbc());
      schemaEnsured = true;
    } else {
      getLogger().warning("Zakum DB is offline. BattlePass will be limited until DB is back.");
      scheduleSchemaEnsure();
    }

    startRuntime();

    // Flush deltas (sync scheduler triggers async flush).
    int flushSeconds = Math.max(2, getConfig().getInt("battlepass.flush.intervalSeconds", 5));
    long flushTicks = flushSeconds * 20L;
    this.flushTaskId = getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
      if (runtime != null && !maintenanceMode.get()) runtime.flushAllAsync();
    }, flushTicks, flushTicks);

    // Premium refresh (sync scheduler triggers async checks).
    int premiumSeconds = Math.max(30, getConfig().getInt("battlepass.premiumRefresh.intervalSeconds", 300));
    long premiumTicks = premiumSeconds * 20L;
    this.premiumTaskId = getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
      if (runtime != null && !maintenanceMode.get()) runtime.refreshPremiumAllAsync();
    }, premiumTicks, premiumTicks);

    // Leaderboard refresh (async).
    int lbSeconds = Math.max(10, getConfig().getInt("battlepass.leaderboard.refreshSeconds", 30));
    long lbTicks = lbSeconds * 20L;
    this.leaderboardTaskId = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
      if (leaderboard != null && !maintenanceMode.get()) leaderboard.refreshNow();
    }, 20L, lbTicks).getTaskId();

    getLogger().info("OrbisBattlePass enabled.");
  }

  @Override
  public void onDisable() {
    if (schemaTaskId != -1) {
      getServer().getScheduler().cancelTask(schemaTaskId);
      schemaTaskId = -1;
    }
    if (flushTaskId != -1) {
      getServer().getScheduler().cancelTask(flushTaskId);
      flushTaskId = -1;
    }
    if (premiumTaskId != -1) {
      getServer().getScheduler().cancelTask(premiumTaskId);
      premiumTaskId = -1;
    }
    if (leaderboardTaskId != -1) {
      getServer().getScheduler().cancelTask(leaderboardTaskId);
      leaderboardTaskId = -1;
    }
    stopRuntime();
  }

  private void startRuntime() {
    this.runtime = new BattlePassRuntime(this, zakum);
    this.runtime.start();

    this.names = new NameCache();
    for (Player p : Bukkit.getOnlinePlayers()) {
      names.put(p.getUniqueId(), p.getName());
    }

    int max = Math.max(50, getConfig().getInt("battlepass.leaderboard.maxEntries", 250));
    this.leaderboard = new BattlePassLeaderboard(zakum, zakum.async(), runtime, max);
    this.leaderboard.refreshAsync();

    this.menus = new BattlePassMenus(runtime, leaderboard, names);

    this.playerListener = new BpPlayerListener(runtime, names, npcOpenCooldownMs);
    this.menuListener = new BattlePassMenuListener(runtime, menus);

    getServer().getPluginManager().registerEvents(playerListener, this);
    getServer().getPluginManager().registerEvents(menuListener, this);

    tryRegisterExpansion();
// Admin editor components (only used by staff, but must be reload-safe).
this.editorSessions = new EditorSessionManager(this, 60_000L);
BattlePassYamlStore ys = new BattlePassYamlStore(this);
this.editor = new BattlePassEditor(this, zakum, ys, adminJobRunning, editorSessions);
this.editorListener = new BattlePassEditorListener(editor);
this.editorChatListener = new BattlePassEditorChatListener(this, editor);

getServer().getPluginManager().registerEvents(editorListener, this);
getServer().getPluginManager().registerEvents(editorChatListener, this);

    subscribeNpcMenus();
  }

  private void scheduleSchemaEnsure() {
    if (schemaTaskId != -1) return;

    long everyTicks = 20L * 30; // 30s
    this.schemaTaskId = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
      if (schemaEnsured) {
        if (schemaTaskId != -1) {
          getServer().getScheduler().cancelTask(schemaTaskId);
          schemaTaskId = -1;
        }
        return;
      }

      if (zakum == null) return;
      if (zakum.database().state() != DatabaseState.ONLINE) return;

      try {
        BattlePassSchema.ensureTables(zakum.database().jdbc());
        schemaEnsured = true;
        getLogger().info("BattlePass schema ensured (DB back online).");
      } catch (Throwable t) {
        getLogger().warning("Failed ensuring BattlePass schema: " + t.getMessage());
      }
    }, 40L, everyTicks).getTaskId();
  }

  private void stopRuntime() {
    stopRuntime(true);
  }

  private void stopRuntime(boolean flush) {
    if (menuListener != null) {
      HandlerList.unregisterAll(menuListener);
      menuListener = null;
    }
    if (playerListener != null) {
      HandlerList.unregisterAll(playerListener);
      playerListener = null;
    }

if (editorListener != null) {
  HandlerList.unregisterAll(editorListener);
  editorListener = null;
}
if (editorChatListener != null) {
  HandlerList.unregisterAll(editorChatListener);
  editorChatListener = null;
}
if (editorSessions != null) {
  try { editorSessions.shutdown(); } catch (Throwable ignored) {}
  editorSessions = null;
}
editor = null;

    if (npcSub != null) {
      try { npcSub.close(); } catch (Throwable ignored) {}
      npcSub = null;
    }

    if (expansion != null) {
      try { expansion.unregister(); } catch (Throwable ignored) {}
      expansion = null;
    }

    menus = null;
    leaderboard = null;
    names = null;

    if (runtime != null) {
      runtime.stop(flush);
      runtime = null;
    }
  }

  private void subscribeNpcMenus() {
    boolean enabled = getConfig().getBoolean("battlepass.npcMenus.enabled", true);
    if (!enabled) return;

    java.util.List<String> ids = getConfig().getStringList("battlepass.npcMenus.openMainNpcIds");
    if (ids == null || ids.isEmpty()) return;

    java.util.HashSet<String> set = new java.util.HashSet<>();
    for (String s : ids) {
      if (s == null) continue;
      String v = s.trim();
      if (!v.isEmpty()) set.add(v.toUpperCase(java.util.Locale.ROOT));
    }
    if (set.isEmpty()) return;

    this.npcSub = zakum.actions().subscribe(ev -> onNpcAction(ev, set));
  }

  private void onNpcAction(ActionEvent ev, java.util.Set<String> openMainNpcIds) {
    if (ev == null) return;
    if (!"npc_interact".equalsIgnoreCase(ev.type())) return;
    if (ev.value() == null) return;

    String npcId = ev.value().trim().toUpperCase(java.util.Locale.ROOT);
    if (!openMainNpcIds.contains(npcId)) return;

    // Anti-spam: ignore repeated opens within 500ms.
    long now = System.currentTimeMillis();
    Long last = npcOpenCooldownMs.put(ev.playerId(), now);
    if (last != null && (now - last) < 500) return;

    Player p = Bukkit.getPlayer(ev.playerId());
    if (p == null) return;

    // Open main menu
    menus.openMain(p);
  }

  private void tryRegisterExpansion() {
    Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
    if (papi == null) return;

    try {
      this.expansion = new OrbisBattlePassExpansion(runtime);
      this.expansion.register();
      getLogger().info("PlaceholderAPI expansion registered: %orbisbp_*%");
    } catch (Throwable t) {
      getLogger().warning("Failed to register PlaceholderAPI expansion: " + t.getMessage());
      this.expansion = null;
    }
  }

  private boolean isAdmin(CommandSender s) {
    return s.hasPermission("orbis.battlepass.admin");
  }

  private void restartRuntimeForConfigChange(CommandSender sender, String msg) {
    // Save first so runtime reload sees the new values.
    try {
      persistConfigAtomicWithBackup();
    } catch (Exception ex) {
      getLogger().warning("Failed saving config: " + ex.getMessage());
      if (sender != null) sender.sendMessage(ChatColor.RED + "Config save failed. Check console.");
      return;
    }

    reloadConfig();
    stopRuntime();
    startRuntime();
    if (sender != null) sender.sendMessage(ChatColor.GREEN + msg);
  }


/**
 * Reloads the BattlePass runtime to apply YAML changes (quests.yml / rewards.yml).
 *
 * Editor posture: sync restart (listeners, tasks) but no config.yml persistence.
 */
public void restartRuntimeForYamlChange(CommandSender sender) {
  stopRuntime();
  startRuntime();
  if (sender != null) sender.sendMessage(ChatColor.GREEN + "BattlePass runtime reloaded.");
}

  private void applyRolloverAndRestart(CommandSender sender, int nextSeason) {
    getConfig().set("battlepass.seasons.current", nextSeason);
    getConfig().set("battlepass.weeks.current", 1);
    restartRuntimeForConfigChange(sender, "Rolled over to season " + nextSeason + " (week 1)." );
  }

  private void persistConfigAtomicWithBackup() throws Exception {
    java.io.File dir = getDataFolder();
    if (dir != null) dir.mkdirs();

    java.nio.file.Path cfg = new java.io.File(getDataFolder(), "config.yml").toPath();

    boolean backupsEnabled = getConfig().getBoolean("battlepass.admin.configBackups.enabled", true);
    int keep = Math.max(0, getConfig().getInt("battlepass.admin.configBackups.keep", 10));
    if (backupsEnabled) {
      java.nio.file.Path backupDir = getDataFolder().toPath().resolve("backups");
      FileBackups.backup(cfg, backupDir, "battlepass-config", keep);
    }

    java.nio.file.Path tmp = cfg.resolveSibling("config.yml.tmp");
    getConfig().save(tmp.toFile());
    AtomicFiles.moveReplace(tmp, cfg);
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    String n = command.getName();
    if (!n.equalsIgnoreCase("obattlepass") && !n.equalsIgnoreCase("battlepass")) return false;

    if (runtime == null) {
      sender.sendMessage(ChatColor.RED + "BattlePass is not ready.");
      return true;
    }

    if (args.length == 0) {
      if (sender instanceof Player p) {
        menus.openMain(p);
        return true;
      }
      sender.sendMessage(ChatColor.LIGHT_PURPLE + "BattlePass" + ChatColor.GRAY
        + " server=" + runtime.progressServerId()
        + " season=" + runtime.season()
        + " db=" + zakum.database().state()
      );
      return true;
    }

    String sub = args[0].toLowerCase();

    if (sub.equals("menu")) {
      if (!(sender instanceof Player p)) {
        sender.sendMessage(ChatColor.RED + "Players only.");
        return true;
      }
      menus.openMain(p);
      return true;
    }

if (sub.equals("edit") || sub.equals("editor")) {
  if (!(sender instanceof Player p)) {
    sender.sendMessage(ChatColor.RED + "Players only.");
    return true;
  }
  if (!isAdmin(sender)) {
    sender.sendMessage(ChatColor.RED + "No permission.");
    return true;
  }
  if (editor == null) {
    sender.sendMessage(ChatColor.RED + "Editor not ready.");
    return true;
  }
  editor.openHome(p);
  return true;
}

    if (sub.equals("rewards")) {
      if (!(sender instanceof Player p)) { sender.sendMessage(ChatColor.RED + "Players only."); return true; }
      menus.openRewards(p, 1);
      return true;
    }

    if (sub.equals("quests")) {
      if (!(sender instanceof Player p)) { sender.sendMessage(ChatColor.RED + "Players only."); return true; }
      menus.openQuests(p, 1);
      return true;
    }

    if (sub.equals("top")) {
      if (!(sender instanceof Player p)) { sender.sendMessage(ChatColor.RED + "Players only."); return true; }
      int page = 1;
      if (args.length >= 2) {
        try { page = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {}
      }
      menus.sendLeaderboard(p, page);
      return true;
    }

    if (sub.equals("claimall")) {
      if (!(sender instanceof Player p)) {
        sender.sendMessage(ChatColor.RED + "Players only.");
        return true;
      }
      int c = runtime.claimAll(p);
      sender.sendMessage(ChatColor.GREEN + "Claimed tiers: " + c);
      return true;
    }

    if (sub.equals("claim")) {
      if (!(sender instanceof Player p)) {
        sender.sendMessage(ChatColor.RED + "Players only.");
        return true;
      }
      if (args.length < 2) {
        sender.sendMessage(ChatColor.RED + "Usage: /battlepass claim <tier> [free|premium|both]");
        return true;
      }
      int tier;
      try { tier = Integer.parseInt(args[1]); }
      catch (NumberFormatException ex) {
        sender.sendMessage(ChatColor.RED + "Invalid tier.");
        return true;
      }

      RewardTrack track = RewardTrack.BOTH;
      if (args.length >= 3) {
        String t = args[2].toLowerCase();
        if (t.startsWith("f")) track = RewardTrack.FREE;
        else if (t.startsWith("p")) track = RewardTrack.PREMIUM;
        else track = RewardTrack.BOTH;
      }

      var res = runtime.claim(p, tier, track);
      sender.sendMessage(res.ok() ? (ChatColor.GREEN + res.message()) : (ChatColor.RED + res.message()));
      return true;
    }

    if (sub.equals("status")) {
      sender.sendMessage(ChatColor.LIGHT_PURPLE + "BattlePass" + ChatColor.GRAY
        + " server=" + runtime.progressServerId()
        + " season=" + runtime.season()
        + " week=" + runtime.week()
        + " tz=" + runtime.timezone()
        + " db=" + zakum.database().state()
        + " tiers=" + runtime.rewards().maxTier()
      );
      return true;
    }

    if (sub.equals("season")) {
      if (!isAdmin(sender)) {
        sender.sendMessage(ChatColor.RED + "No permission.");
        return true;
      }

      // /battlepass season
      if (args.length == 1) {
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "BattlePass" + ChatColor.GRAY + " season=" + runtime.season());
        sender.sendMessage(ChatColor.GRAY + "Usage: /battlepass season set <number> | /battlepass season next");
        return true;
      }

      String op = args[1].toLowerCase();
      if (op.equals("next")) {
        int next = Math.max(1, runtime.season() + 1);
        getConfig().set("battlepass.seasons.current", next);
        restartRuntimeForConfigChange(sender, "Season set to " + next + ".");
        return true;
      }
      if (op.equals("set")) {
        if (args.length < 3) {
          sender.sendMessage(ChatColor.RED + "Usage: /battlepass season set <number>");
          return true;
        }
        int v;
        try { v = Integer.parseInt(args[2]); }
        catch (NumberFormatException ex) { sender.sendMessage(ChatColor.RED + "Invalid season."); return true; }
        v = Math.max(1, v);

        getConfig().set("battlepass.seasons.current", v);
        restartRuntimeForConfigChange(sender, "Season set to " + v + ".");
        return true;
      }

      sender.sendMessage(ChatColor.RED + "Usage: /battlepass season set <number> | /battlepass season next");
      return true;
    }

    if (sub.equals("week")) {
      if (!isAdmin(sender)) {
        sender.sendMessage(ChatColor.RED + "No permission.");
        return true;
      }

      // /battlepass week
      if (args.length == 1) {
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "BattlePass" + ChatColor.GRAY + " week=" + runtime.week());
        sender.sendMessage(ChatColor.GRAY + "Usage: /battlepass week set <number> | /battlepass week next");
        return true;
      }

      String op = args[1].toLowerCase();
      if (op.equals("next")) {
        int next = Math.max(1, runtime.week() + 1);
        getConfig().set("battlepass.weeks.current", next);
        restartRuntimeForConfigChange(sender, "Week set to " + next + ".");
        return true;
      }
      if (op.equals("set")) {
        if (args.length < 3) {
          sender.sendMessage(ChatColor.RED + "Usage: /battlepass week set <number>");
          return true;
        }
        int v;
        try { v = Integer.parseInt(args[2]); }
        catch (NumberFormatException ex) { sender.sendMessage(ChatColor.RED + "Invalid week."); return true; }
        v = Math.max(1, v);

        getConfig().set("battlepass.weeks.current", v);
        restartRuntimeForConfigChange(sender, "Week set to " + v + ".");
        return true;
      }

      sender.sendMessage(ChatColor.RED + "Usage: /battlepass week set <number> | /battlepass week next");
      return true;
    }

    if (sub.equals("rollover")) {
      if (!isAdmin(sender)) {
        sender.sendMessage(ChatColor.RED + "No permission.");
        return true;
      }

      boolean backupsEnabled = getConfig().getBoolean("battlepass.backups.enabled", true);
      boolean backupBeforeChange = getConfig().getBoolean("battlepass.rollover.backupBeforeChange", true);
      boolean purgeOldSeason = getConfig().getBoolean("battlepass.rollover.purgeOldSeason", false);
      int chunk = Math.max(50, getConfig().getInt("battlepass.backups.chunkSize", 200));
      int deleteLimit = Math.max(100, getConfig().getInt("battlepass.rollover.purgeDeleteLimit", 5000));

      if (!adminJobRunning.compareAndSet(false, true)) {
        sender.sendMessage(ChatColor.RED + "Another BattlePass admin job is running.");
        return true;
      }

      BattlePassRuntime rt = runtime;
      String sid = rt.progressServerId();
      int oldSeason = rt.season();
      int nextSeason = Math.max(1, oldSeason + 1);

      // Maintenance: pause actions, flush deltas, then stop without flush to prevent stray writes during backup/purge.
      maintenanceMode.set(true);
      sender.sendMessage(ChatColor.YELLOW + "Maintenance" + ChatColor.GRAY + ": pausing actions and flushing deltas...");

      rt.pauseActions();
      rt.flushAllAndWaitAsync().whenComplete((v, flushErr) -> {
        if (!isEnabled()) { adminJobRunning.set(false); maintenanceMode.set(false); return; }

        if (flushErr != null) {
          Bukkit.getScheduler().runTask(this, () -> {
            adminJobRunning.set(false);
            maintenanceMode.set(false);
            sender.sendMessage(ChatColor.RED + "Failed flushing deltas; rollover aborted.");
            restartRuntimeForConfigChange(sender, "recovery");
          });
          return;
        }

        Bukkit.getScheduler().runTask(this, () -> {
          stopRuntime(false); // already flushed
          sender.sendMessage(ChatColor.GRAY + "Flush OK. Running rollover ops...");
        });

        java.util.concurrent.CompletableFuture<BattlePassBackupService.BackupResult> backupFuture;
        if (backupsEnabled && backupBeforeChange) {
          String createdBy = (sender instanceof Player p) ? p.getName() : "CONSOLE";
          backupFuture = BattlePassBackupService.backupSeasonAsync(zakum, sid, oldSeason, createdBy, "rollover", chunk);
        } else {
          backupFuture = java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        backupFuture.whenComplete((backup, backupErr) -> {
          if (!isEnabled()) { adminJobRunning.set(false); maintenanceMode.set(false); return; }

          if (backupsEnabled && backupBeforeChange) {
            if (backupErr != null || backup == null || !backup.ok()) {
              Bukkit.getScheduler().runTask(this, () -> {
                adminJobRunning.set(false);
                maintenanceMode.set(false);
                String why = (backupErr != null) ? (backupErr.getClass().getSimpleName() + ": " + backupErr.getMessage()) : (backup == null ? "unknown" : backup.message());
                sender.sendMessage(ChatColor.RED + "Backup failed. Rollover aborted. Cause: " + why);
                startRuntime();
              });
              return;
            }

            // Best-effort: also snapshot YAML/config into backups/batch-<id>/.
            try {
              boolean fEnabled = getConfig().getBoolean("battlepass.backups.files.enabled", true);
              int keep = Math.max(0, getConfig().getInt("battlepass.backups.files.keep", 20));
              if (fEnabled) BattlePassBatchFiles.backupBatchFiles(this, backup.batchId(), keep);
            } catch (Throwable t) {
              getLogger().warning("Batch file backup skipped: " + t.getMessage());
            }
          }

          java.util.concurrent.CompletableFuture<BattlePassBackupService.PurgeResult> purgeFuture;
          if (purgeOldSeason) {
            purgeFuture = BattlePassBackupService.purgeSeasonAsync(zakum, sid, oldSeason, deleteLimit);
          } else {
            purgeFuture = java.util.concurrent.CompletableFuture.completedFuture(null);
          }

          purgeFuture.whenComplete((purge, purgeErr) -> {
            if (!isEnabled()) { adminJobRunning.set(false); maintenanceMode.set(false); return; }

            Bukkit.getScheduler().runTask(this, () -> {
              try {
                if (purgeOldSeason && (purgeErr != null || purge == null || !purge.ok())) {
                  String why = (purgeErr != null) ? (purgeErr.getClass().getSimpleName() + ": " + purgeErr.getMessage()) : (purge == null ? "unknown" : purge.message());
                  sender.sendMessage(ChatColor.RED + "Purge failed. Rollover aborted" + ChatColor.GRAY + " (backup may still exist). Cause: " + why);
                  startRuntime();
                  return;
                }

                if (backup != null && backupsEnabled && backupBeforeChange) {
                  sender.sendMessage(ChatColor.GREEN + "Backup OK (batch " + backup.batchId() + ")" + ChatColor.GRAY +
                    " [progress=" + backup.progressRows() + ", steps=" + backup.stepRows() + ", claims=" + backup.claimRows() + ", periods=" + backup.periodRows() + "]");
                }
                if (purge != null && purgeOldSeason) {
                  sender.sendMessage(ChatColor.GREEN + "Purge OK" + ChatColor.GRAY +
                    " [progress=" + purge.progressDeleted() + ", steps=" + purge.stepDeleted() + ", claims=" + purge.claimDeleted() + ", periods=" + purge.periodDeleted() + "]");
                }

                applyRolloverAndRestart(sender, nextSeason);

              } finally {
                adminJobRunning.set(false);
                maintenanceMode.set(false);
              }
            });
          });
        });
      });

      return true;
    }

    if (sub.equals("backup")) {
      if (!isAdmin(sender)) {
        sender.sendMessage(ChatColor.RED + "No permission.");
        return true;
      }
      if (!getConfig().getBoolean("battlepass.backups.enabled", true)) {
        sender.sendMessage(ChatColor.RED + "Backups are disabled in config.");
        return true;
      }

      if (!adminJobRunning.compareAndSet(false, true)) {
        sender.sendMessage(ChatColor.RED + "Another BattlePass admin job is running.");
        return true;
      }

      int season = runtime.season();
      String note = null;
      int noteStart = 2;
      if (args.length >= 2) {
        try { season = Math.max(1, Integer.parseInt(args[1])); }
        catch (NumberFormatException ignored) {
          // No season provided; treat remaining args as note.
          season = runtime.season();
          noteStart = 1;
        }
      } else {
        noteStart = Integer.MAX_VALUE;
      }
      if (args.length > noteStart) {
        note = String.join(" ", java.util.Arrays.copyOfRange(args, noteStart, args.length));
      }

      int chunk = Math.max(50, getConfig().getInt("battlepass.backups.chunkSize", 200));
      String createdBy = (sender instanceof Player p) ? p.getName() : "CONSOLE";

      sender.sendMessage(ChatColor.GRAY + "Starting DB backup for season " + season + "...");

      BattlePassBackupService.backupSeasonAsync(zakum, runtime.progressServerId(), season, createdBy, note, chunk)
        .whenComplete((res, err) -> {
          if (!isEnabled()) { adminJobRunning.set(false); return; }

          // Best-effort: also snapshot YAML/config into backups/batch-<id>/.
          if (err == null && res != null && res.ok()) {
            try {
              boolean fEnabled = getConfig().getBoolean("battlepass.backups.files.enabled", true);
              int keep = Math.max(0, getConfig().getInt("battlepass.backups.files.keep", 20));
              if (fEnabled) BattlePassBatchFiles.backupBatchFiles(this, res.batchId(), keep);
            } catch (Throwable t) {
              getLogger().warning("Batch file backup skipped: " + t.getMessage());
            }
          }

          Bukkit.getScheduler().runTask(this, () -> {
            adminJobRunning.set(false);
            if (err != null) {
              sender.sendMessage(ChatColor.RED + "Backup failed: " + err.getClass().getSimpleName());
              return;
            }
            if (res == null || !res.ok()) {
              sender.sendMessage(ChatColor.RED + "Backup failed: " + (res == null ? "unknown" : res.message()));
              return;
            }
            sender.sendMessage(ChatColor.GREEN + "Backup OK (batch " + res.batchId() + ")" + ChatColor.GRAY +
              " [progress=" + res.progressRows() + ", steps=" + res.stepRows() + ", claims=" + res.claimRows() + ", periods=" + res.periodRows() + "]");
          });
        });
      return true;
    }

    if (sub.equals("backups")) {
      if (!isAdmin(sender)) {
        sender.sendMessage(ChatColor.RED + "No permission.");
        return true;
      }
      sender.sendMessage(ChatColor.GRAY + "Fetching recent backups...");
      BattlePassBackupService.listBatchesAsync(zakum, runtime.progressServerId(), 10)
        .whenComplete((batches, err) -> {
          if (!isEnabled()) return;
          Bukkit.getScheduler().runTask(this, () -> {
            if (err != null) {
              sender.sendMessage(ChatColor.RED + "Failed listing backups.");
              return;
            }
            if (batches == null || batches.isEmpty()) {
              sender.sendMessage(ChatColor.GRAY + "No backups found.");
              return;
            }
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "BattlePass Backups" + ChatColor.GRAY + " (" + batches.size() + ")");
            for (var b : batches) {
              boolean hasFiles = BattlePassBatchFiles.hasBatchFiles(this, b.batchId());
              sender.sendMessage(ChatColor.GRAY + "- batch=" + b.batchId() + " season=" + b.season() + " status=" + b.status() +
                (hasFiles ? " files=yes" : "") +
                (b.createdBy() != null ? " by=" + b.createdBy() : "") + (b.note() != null ? " note=" + b.note() : ""));
            }
          });
        });
      return true;
    }

    if (sub.equals("purge")) {
      if (!isAdmin(sender)) {
        sender.sendMessage(ChatColor.RED + "No permission.");
        return true;
      }
      if (args.length < 3) {
        sender.sendMessage(ChatColor.RED + "Usage: /battlepass purge <season> CONFIRM");
        return true;
      }
      int season;
      try { season = Math.max(1, Integer.parseInt(args[1])); }
      catch (NumberFormatException ex) { sender.sendMessage(ChatColor.RED + "Invalid season."); return true; }
      if (!args[2].equalsIgnoreCase("CONFIRM")) {
        sender.sendMessage(ChatColor.RED + "Refusing to delete without CONFIRM.");
        return true;
      }
      if (!adminJobRunning.compareAndSet(false, true)) {
        sender.sendMessage(ChatColor.RED + "Another BattlePass admin job is running.");
        return true;
      }

      int deleteLimit = Math.max(100, getConfig().getInt("battlepass.rollover.purgeDeleteLimit", 5000));
      String sid = runtime.progressServerId();

      // If purging the currently active season, enter maintenance to prevent in-memory flushes from re-creating rows.
      if (season == runtime.season()) {
        maintenanceMode.set(true);
        sender.sendMessage(ChatColor.YELLOW + "Maintenance" + ChatColor.GRAY + ": pausing actions and flushing deltas...");

        BattlePassRuntime rt = runtime;
        rt.pauseActions();
        rt.flushAllAndWaitAsync().whenComplete((v, flushErr) -> {
          if (!isEnabled()) { adminJobRunning.set(false); maintenanceMode.set(false); return; }

          if (flushErr != null) {
            Bukkit.getScheduler().runTask(this, () -> {
              adminJobRunning.set(false);
              maintenanceMode.set(false);
              sender.sendMessage(ChatColor.RED + "Failed flushing deltas; purge aborted.");
              restartRuntimeForConfigChange(sender, "recovery");
            });
            return;
          }

          Bukkit.getScheduler().runTask(this, () -> {
            stopRuntime(false); // already flushed; avoid re-writing while rows are being deleted
            sender.sendMessage(ChatColor.GRAY + "Purging season " + season + " (deleteLimit=" + deleteLimit + ")...");
          });

          BattlePassBackupService.purgeSeasonAsync(zakum, sid, season, deleteLimit)
            .whenComplete((res, err) -> {
              if (!isEnabled()) { adminJobRunning.set(false); maintenanceMode.set(false); return; }
              Bukkit.getScheduler().runTask(this, () -> {
                adminJobRunning.set(false);
                maintenanceMode.set(false);
                if (err != null) {
                  sender.sendMessage(ChatColor.RED + "Purge failed: " + err.getClass().getSimpleName());
                  startRuntime();
                  return;
                }
                if (res == null || !res.ok()) {
                  sender.sendMessage(ChatColor.RED + "Purge failed: " + (res == null ? "unknown" : res.message()));
                  startRuntime();
                  return;
                }
                sender.sendMessage(ChatColor.GREEN + "Purge OK" + ChatColor.GRAY +
                  " [progress=" + res.progressDeleted() + ", steps=" + res.stepDeleted() + ", claims=" + res.claimDeleted() + ", periods=" + res.periodDeleted() + "]");
                startRuntime();
              });
            });
        });
        return true;
      }

      // Non-active seasons can be purged without downtime.
      sender.sendMessage(ChatColor.GRAY + "Purging season " + season + " (deleteLimit=" + deleteLimit + ")...");
      BattlePassBackupService.purgeSeasonAsync(zakum, sid, season, deleteLimit)
        .whenComplete((res, err) -> {
          if (!isEnabled()) { adminJobRunning.set(false); return; }
          Bukkit.getScheduler().runTask(this, () -> {
            adminJobRunning.set(false);
            if (err != null) {
              sender.sendMessage(ChatColor.RED + "Purge failed: " + err.getClass().getSimpleName());
              return;
            }
            if (res == null || !res.ok()) {
              sender.sendMessage(ChatColor.RED + "Purge failed: " + (res == null ? "unknown" : res.message()));
              return;
            }
            sender.sendMessage(ChatColor.GREEN + "Purge OK" + ChatColor.GRAY +
              " [progress=" + res.progressDeleted() + ", steps=" + res.stepDeleted() + ", claims=" + res.claimDeleted() + ", periods=" + res.periodDeleted() + "]");
          });
        });
      return true;
    }

    if (sub.equals("restore")) {
      if (!isAdmin(sender)) {
        sender.sendMessage(ChatColor.RED + "No permission.");
        return true;
      }
      if (args.length < 4) {
        sender.sendMessage(ChatColor.RED + "Usage: /battlepass restore <db|files|all> <batchId> [OVERWRITE] CONFIRM");
        return true;
      }
      String mode = args[1].toLowerCase(java.util.Locale.ROOT);
      long batchId;
      try { batchId = Long.parseLong(args[2]); }
      catch (NumberFormatException ex) { sender.sendMessage(ChatColor.RED + "Invalid batch id."); return true; }

      boolean overwrite = false;
      String last = args[args.length - 1];
      if (!last.equalsIgnoreCase("CONFIRM")) {
        sender.sendMessage(ChatColor.RED + "Refusing to restore without CONFIRM.");
        return true;
      }
      for (int i = 3; i < args.length - 1; i++) {
        if (args[i].equalsIgnoreCase("OVERWRITE")) overwrite = true;
      }

      if (!adminJobRunning.compareAndSet(false, true)) {
        sender.sendMessage(ChatColor.RED + "Another BattlePass admin job is running.");
        return true;
      }

      int chunk = Math.max(50, getConfig().getInt("battlepass.backups.chunkSize", 200));
      int deleteLimit = Math.max(100, getConfig().getInt("battlepass.rollover.purgeDeleteLimit", 5000));

      maintenanceMode.set(true);
      sender.sendMessage(ChatColor.YELLOW + "Maintenance" + ChatColor.GRAY + ": stopping BattlePass (no-flush) for restore...");
      stopRuntime(false);

      java.util.concurrent.CompletableFuture<Boolean> filesFuture;
      if (mode.equals("files") || mode.equals("all")) {
        filesFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> BattlePassBatchFiles.restoreBatchFiles(this, batchId), zakum.async());
      } else {
        filesFuture = java.util.concurrent.CompletableFuture.completedFuture(true);
      }

      java.util.concurrent.CompletableFuture<BattlePassBackupService.RestoreResult> dbFuture;
      if (mode.equals("db") || mode.equals("all")) {
        dbFuture = BattlePassBackupService.restoreBatchAsync(zakum, batchId, overwrite, chunk, deleteLimit);
      } else {
        dbFuture = java.util.concurrent.CompletableFuture.completedFuture(new BattlePassBackupService.RestoreResult(true, batchId, "SKIP", 0, 0, 0, 0));
      }

      filesFuture.thenCombine(dbFuture, (filesOk, dbRes) -> new Object[]{filesOk, dbRes})
        .whenComplete((pair, err) -> {
          if (!isEnabled()) { adminJobRunning.set(false); maintenanceMode.set(false); return; }
          Bukkit.getScheduler().runTask(this, () -> {
            try {
              boolean filesOk = pair != null && (Boolean) pair[0];
              BattlePassBackupService.RestoreResult dbRes = pair == null ? null : (BattlePassBackupService.RestoreResult) pair[1];

              if (err != null) {
                sender.sendMessage(ChatColor.RED + "Restore failed: " + err.getClass().getSimpleName());
                startRuntime();
                return;
              }
              if ((mode.equals("files") || mode.equals("all")) && !filesOk) {
                sender.sendMessage(ChatColor.RED + "Restore failed: batch files not found (backups/batch-" + batchId + ")");
                startRuntime();
                return;
              }
              if ((mode.equals("db") || mode.equals("all")) && (dbRes == null || !dbRes.ok())) {
                sender.sendMessage(ChatColor.RED + "Restore failed: " + (dbRes == null ? "unknown" : dbRes.message()));
                startRuntime();
                return;
              }

              if (mode.equals("files") || mode.equals("all")) {
                reloadConfig();
              }

              startRuntime();

              sender.sendMessage(ChatColor.GREEN + "Restore OK" + ChatColor.GRAY + " (mode=" + mode + ", batch=" + batchId + ")");
              if (dbRes != null && (mode.equals("db") || mode.equals("all"))) {
                sender.sendMessage(ChatColor.GRAY + "DB rows restored: progress=" + dbRes.progressRestored() + ", steps=" + dbRes.stepRestored() + ", claims=" + dbRes.claimRestored() + ", periods=" + dbRes.periodRestored());
              }
            } finally {
              adminJobRunning.set(false);
              maintenanceMode.set(false);
            }
          });
        });
      return true;
    }

    if (sub.equals("yaml")) {
      if (!isAdmin(sender)) {
        sender.sendMessage(ChatColor.RED + "No permission.");
        return true;
      }

      BattlePassYamlStore ys = new BattlePassYamlStore(this);

      // /battlepass yaml
      if (args.length == 1 || args[1].equalsIgnoreCase("status")) {
        var q = ys.validateQuests();
        var r = ys.validateRewards();
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "BattlePass YAML" + ChatColor.GRAY + " (safety=" + getConfig().getBoolean("battlepass.yamlSafety.enabled", true) + ")");
        sender.sendMessage(ChatColor.GRAY + "- quests.yml: " + (q.ok() ? ChatColor.GREEN + "OK" : ChatColor.RED + "BAD") + ChatColor.GRAY + " (" + q.message() + ")");
        sender.sendMessage(ChatColor.GRAY + "- rewards.yml: " + (r.ok() ? ChatColor.GREEN + "OK" : ChatColor.RED + "BAD") + ChatColor.GRAY + " (" + r.message() + ")");
        sender.sendMessage(ChatColor.GRAY + "Commands: /battlepass yaml validate | backup [quests|rewards|all] | backups [quests|rewards] [limit] | restore <quests|rewards> <backup|latest> CONFIRM");
        return true;
      }

      String op = args[1].toLowerCase();

      if (op.equals("validate")) {
        var q = ys.validateQuests();
        var r = ys.validateRewards();
        sender.sendMessage(q.ok() ? (ChatColor.GREEN + q.message()) : (ChatColor.RED + q.message()));
        sender.sendMessage(r.ok() ? (ChatColor.GREEN + r.message()) : (ChatColor.RED + r.message()));
        return true;
      }

      if (op.equals("backup")) {
        if (!adminJobRunning.compareAndSet(false, true)) {
          sender.sendMessage(ChatColor.RED + "Another BattlePass admin job is running.");
          return true;
        }
        try {
          String kind = (args.length >= 3) ? args[2] : "all";
          if (kind.equalsIgnoreCase("all")) {
            String qb = ys.backupNow("quests");
            String rb = ys.backupNow("rewards");
            sender.sendMessage(ChatColor.GREEN + "Backed up quests.yml -> " + ChatColor.GRAY + qb);
            sender.sendMessage(ChatColor.GREEN + "Backed up rewards.yml -> " + ChatColor.GRAY + rb);
          } else if (kind.toLowerCase().startsWith("q")) {
            String qb = ys.backupNow("quests");
            sender.sendMessage(ChatColor.GREEN + "Backed up quests.yml -> " + ChatColor.GRAY + qb);
          } else if (kind.toLowerCase().startsWith("r")) {
            String rb = ys.backupNow("rewards");
            sender.sendMessage(ChatColor.GREEN + "Backed up rewards.yml -> " + ChatColor.GRAY + rb);
          } else {
            sender.sendMessage(ChatColor.RED + "Usage: /battlepass yaml backup [quests|rewards|all]");
          }
        } catch (Exception ex) {
          sender.sendMessage(ChatColor.RED + "Backup failed: " + ex.getMessage());
        } finally {
          adminJobRunning.set(false);
        }
        return true;
      }

      if (op.equals("backups")) {
        String kind = (args.length >= 3) ? args[2] : "all";
        int limit = 10;
        if (args.length >= 4) {
          try { limit = Integer.parseInt(args[3]); } catch (NumberFormatException ignored) {}
        }

        if (kind.equalsIgnoreCase("all")) {
          var q = ys.listBackups("quests", limit);
          var r = ys.listBackups("rewards", limit);
          sender.sendMessage(ChatColor.LIGHT_PURPLE + "Quests backups" + ChatColor.GRAY + " (" + q.size() + ")");
          for (String s : q) sender.sendMessage(ChatColor.GRAY + "- " + s);
          sender.sendMessage(ChatColor.LIGHT_PURPLE + "Rewards backups" + ChatColor.GRAY + " (" + r.size() + ")");
          for (String s : r) sender.sendMessage(ChatColor.GRAY + "- " + s);
          return true;
        }

        if (kind.toLowerCase().startsWith("q")) {
          var q = ys.listBackups("quests", limit);
          sender.sendMessage(ChatColor.LIGHT_PURPLE + "Quests backups" + ChatColor.GRAY + " (" + q.size() + ")");
          for (String s : q) sender.sendMessage(ChatColor.GRAY + "- " + s);
          return true;
        }

        if (kind.toLowerCase().startsWith("r")) {
          var r = ys.listBackups("rewards", limit);
          sender.sendMessage(ChatColor.LIGHT_PURPLE + "Rewards backups" + ChatColor.GRAY + " (" + r.size() + ")");
          for (String s : r) sender.sendMessage(ChatColor.GRAY + "- " + s);
          return true;
        }

        sender.sendMessage(ChatColor.RED + "Usage: /battlepass yaml backups [quests|rewards|all] [limit]");
        return true;
      }

      if (op.equals("restore")) {
        if (args.length < 5) {
          sender.sendMessage(ChatColor.RED + "Usage: /battlepass yaml restore <quests|rewards> <backup|latest> CONFIRM");
          return true;
        }
        String kind = args[2];
        String backup = args[3];
        if (!args[4].equalsIgnoreCase("CONFIRM")) {
          sender.sendMessage(ChatColor.RED + "Refusing to overwrite without CONFIRM.");
          return true;
        }

        if (!adminJobRunning.compareAndSet(false, true)) {
          sender.sendMessage(ChatColor.RED + "Another BattlePass admin job is running.");
          return true;
        }

        try {
          boolean ok = ys.restore(kind, backup);
          if (!ok) {
            sender.sendMessage(ChatColor.RED + "Restore failed: backup not found.");
            return true;
          }

          // Reload runtime to apply quests/rewards changes immediately.
          stopRuntime();
          startRuntime();
          sender.sendMessage(ChatColor.GREEN + "Restored " + kind + " from backup and reloaded BattlePass runtime.");
        } catch (Exception ex) {
          sender.sendMessage(ChatColor.RED + "Restore failed: " + ex.getMessage());
        } finally {
          adminJobRunning.set(false);
        }
        return true;
      }

      sender.sendMessage(ChatColor.RED + "Usage: /battlepass yaml validate | backup [quests|rewards|all] | backups [quests|rewards|all] [limit] | restore <quests|rewards> <backup|latest> CONFIRM");
      return true;
    }

    if (sub.equals("reload")) {
      if (!isAdmin(sender)) {
        sender.sendMessage(ChatColor.RED + "No permission.");
        return true;
      }
      reloadConfig();
      stopRuntime();
      startRuntime();
      sender.sendMessage(ChatColor.GREEN + "BattlePass reloaded.");
      return true;
    }

    if (sub.equals("givepoints")) {
      if (!isAdmin(sender)) {
        sender.sendMessage(ChatColor.RED + "No permission.");
        return true;
      }
      if (args.length < 3) {
        sender.sendMessage(ChatColor.RED + "Usage: /battlepass givepoints <player> <amount>");
        return true;
      }
      Player target = Bukkit.getPlayerExact(args[1]);
      if (target == null) {
        sender.sendMessage(ChatColor.RED + "Player not online.");
        return true;
      }
      long amt;
      try { amt = Long.parseLong(args[2]); }
      catch (NumberFormatException ex) { sender.sendMessage(ChatColor.RED + "Invalid amount."); return true; }
      if (amt == 0) return true;

      var st = runtime.state(target.getUniqueId());
      if (st == null) {
        sender.sendMessage(ChatColor.RED + "State not loaded.");
        return true;
      }
      st.addPoints(amt);
      int newTier = runtime.rewards().tierForPoints(st.points());
      if (newTier > st.tier()) st.setTier(newTier);

      sender.sendMessage(ChatColor.GREEN + "Added points.");
      target.sendMessage(ChatColor.LIGHT_PURPLE + "BattlePass" + ChatColor.GRAY + " points=" + st.points() + " tier=" + st.tier());
      return true;
    }

    sender.sendMessage(ChatColor.RED + "Unknown subcommand.");
    sender.sendMessage(ChatColor.GRAY + "Try: /battlepass menu | rewards | quests | top | claim | claimall | status | reload | givepoints | season | week | rollover | yaml");
    return true;
  }
}
