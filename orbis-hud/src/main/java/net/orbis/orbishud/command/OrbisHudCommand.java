package net.orbis.orbishud.command;

import net.orbis.orbishud.OrbisHudPlugin;
import net.orbis.orbishud.config.HudConfig;
import net.orbis.orbishud.service.HudStatus;
import net.orbis.orbishud.service.HudService;
import net.orbis.zakum.api.util.BrandingText;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class OrbisHudCommand implements CommandExecutor, TabCompleter {

  private final OrbisHudPlugin plugin;
  private final HudService service;

  public OrbisHudCommand(OrbisHudPlugin plugin, HudService service) {
    this.plugin = plugin;
    this.service = service;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (args.length == 0) {
      sendHelp(sender);
      return true;
    }

    String sub = args[0].toLowerCase(Locale.ROOT);
    if (sub.equals("status")) {
      if (!has(sender, "orbishud.admin")) return noPermission(sender);
      HudStatus status = service.snapshot();
      send(sender, "&8[<gradient:#38bdf8:#60a5fa>Orbis</gradient>&8] &bHUD Status");
      send(sender, "&7running=&f" + status.running());
      send(sender, "&7enabled=&f" + status.enabled());
      send(sender, "&7updateIntervalTicks=&f" + status.updateIntervalTicks());
      send(sender, "&7profiles=&f" + status.profiles());
      send(sender, "&7defaultProfile=&f" + status.defaultProfile());
      send(sender, "&7trackedPlayers=&f" + status.trackedPlayers());
      send(sender, "&7onlinePlayers=&f" + status.onlinePlayers());
      send(sender, "&7taskId=&f" + status.taskId());
      send(sender, "&7availableProfiles=&f" + String.join(", ", service.availableProfiles()));
      return true;
    }

    if (sub.equals("reload")) {
      if (!has(sender, "orbishud.reload")) return noPermission(sender);
      plugin.reloadConfig();
      HudConfig loaded = HudConfig.load(plugin.getConfig(), plugin.getLogger());
      service.reload(loaded);
      send(sender, "&aOrbis HUD reloaded. &7profiles=&f" + loaded.profileIds().size());
      plugin.getLogger().info("OrbisHud reload requested by " + actor(sender) + ".");
      return true;
    }

    if (sub.equals("profile")) {
      if (!has(sender, "orbishud.profile.set")) return noPermission(sender);
      if (args.length < 3) {
        send(sender, "&cUsage: /" + label + " profile <player> <profile|default>");
        return true;
      }

      Player target = Bukkit.getPlayerExact(args[1]);
      if (target == null) {
        send(sender, "&cPlayer not online: " + args[1]);
        return true;
      }

      String requested = args[2].toLowerCase(Locale.ROOT);
      if (requested.equals("default")) {
        service.clearProfile(target.getUniqueId());
        send(sender, "&aCleared forced profile for &f" + target.getName() + "&a.");
        plugin.getLogger().info("OrbisHud profile cleared for " + target.getName() + " by " + actor(sender) + ".");
        return true;
      }

      boolean ok = service.setProfile(target.getUniqueId(), requested);
      if (!ok) {
        send(sender, "&cUnknown profile: " + requested);
        send(sender, "&7Available: &f" + String.join(", ", service.availableProfiles()));
        return true;
      }

      send(sender, "&aSet HUD profile for &f" + target.getName() + " &ato &f" + requested + "&a.");
      plugin.getLogger().info(
        "OrbisHud profile set for " + target.getName() + " -> " + requested + " by " + actor(sender) + "."
      );
      return true;
    }

    sendHelp(sender);
    return true;
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
    if (args.length == 1) {
      List<String> subs = new ArrayList<>();
      if (has(sender, "orbishud.admin")) subs.add("status");
      if (has(sender, "orbishud.reload")) subs.add("reload");
      if (has(sender, "orbishud.profile.set")) subs.add("profile");
      return prefix(subs, args[0]);
    }
    if (args.length == 2 && args[0].equalsIgnoreCase("profile") && has(sender, "orbishud.profile.set")) {
      List<String> players = new ArrayList<>();
      for (Player p : Bukkit.getOnlinePlayers()) {
        players.add(p.getName());
      }
      return prefix(players, args[1]);
    }
    if (args.length == 3 && args[0].equalsIgnoreCase("profile") && has(sender, "orbishud.profile.set")) {
      List<String> profiles = service.availableProfiles().stream().sorted().collect(Collectors.toCollection(ArrayList::new));
      profiles.add("default");
      return prefix(profiles, args[2]);
    }
    return List.of();
  }

  private static List<String> prefix(List<String> source, String token) {
    String needle = token == null ? "" : token.toLowerCase(Locale.ROOT);
    List<String> out = new ArrayList<>();
    for (String value : source) {
      if (value.toLowerCase(Locale.ROOT).startsWith(needle)) out.add(value);
    }
    return out;
  }

  private boolean has(CommandSender sender, String perm) {
    return sender.hasPermission("orbishud.admin") || sender.hasPermission(perm);
  }

  private boolean noPermission(CommandSender sender) {
    send(sender, "&cNo permission.");
    return true;
  }

  private void sendHelp(CommandSender sender) {
    send(sender, "&8[<gradient:#38bdf8:#60a5fa>Orbis</gradient>&8] &bHUD Commands");
    if (has(sender, "orbishud.admin")) {
      send(sender, "&7/orbishud status");
    }
    if (has(sender, "orbishud.reload")) {
      send(sender, "&7/orbishud reload");
    }
    if (has(sender, "orbishud.profile.set")) {
      send(sender, "&7/orbishud profile <player> <profile|default>");
    }
    send(sender, "&8Profiles: &f" + String.join(", ", service.availableProfiles()));
  }

  private static String actor(CommandSender sender) {
    return sender instanceof Player p ? p.getName() : sender.getName();
  }

  private static void send(CommandSender sender, String message) {
    sender.sendMessage(BrandingText.render(message));
  }
}
