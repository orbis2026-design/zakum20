package net.orbis.zakum.bridge.commandapi;

import dev.jorel.commandapi.CommandAPIBukkit;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.DoubleArgument;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.arguments.LongArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import dev.jorel.commandapi.executors.CommandExecutor;
import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.api.boosters.BoosterKind;
import net.orbis.zakum.api.db.DatabaseState;
import net.orbis.zakum.api.entitlements.EntitlementScope;
import net.orbis.zakum.api.packets.PacketService;
import net.orbis.zakum.core.boosters.SqlBoosterService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
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

  @Override
  public void onEnable() {
    this.api = Bukkit.getServicesManager().load(ZakumApi.class);
    if (api == null) {
      getLogger().severe("ZakumApi not found. Disabling ZakumBridgeCommandAPI.");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }

    // Remove the fallback /zakum command (registered by zakum-core plugin.yml) so we can own the node cleanly.
    try {
      CommandAPIBukkit.unregister("zakum", true, true);
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

    root.register();
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

