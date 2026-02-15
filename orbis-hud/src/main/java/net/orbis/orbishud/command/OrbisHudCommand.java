package net.orbis.orbishud.command;

import net.orbis.orbishud.OrbisHudPlugin;
import net.orbis.orbishud.config.HudConfig;
import net.orbis.orbishud.service.HudStatus;
import net.orbis.orbishud.service.HudService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
      sender.sendMessage(ChatColor.AQUA + "OrbisHud Status");
      sender.sendMessage(ChatColor.GRAY + "running=" + ChatColor.WHITE + status.running());
      sender.sendMessage(ChatColor.GRAY + "enabled=" + ChatColor.WHITE + status.enabled());
      sender.sendMessage(ChatColor.GRAY + "updateIntervalTicks=" + ChatColor.WHITE + status.updateIntervalTicks());
      sender.sendMessage(ChatColor.GRAY + "profiles=" + ChatColor.WHITE + status.profiles());
      sender.sendMessage(ChatColor.GRAY + "defaultProfile=" + ChatColor.WHITE + status.defaultProfile());
      sender.sendMessage(ChatColor.GRAY + "trackedPlayers=" + ChatColor.WHITE + status.trackedPlayers());
      sender.sendMessage(ChatColor.GRAY + "onlinePlayers=" + ChatColor.WHITE + status.onlinePlayers());
      sender.sendMessage(ChatColor.GRAY + "taskId=" + ChatColor.WHITE + status.taskId());
      sender.sendMessage(ChatColor.GRAY + "availableProfiles=" + ChatColor.WHITE + String.join(", ", service.availableProfiles()));
      return true;
    }

    if (sub.equals("reload")) {
      if (!has(sender, "orbishud.reload")) return noPermission(sender);
      plugin.reloadConfig();
      HudConfig loaded = HudConfig.load(plugin.getConfig(), plugin.getLogger());
      service.reload(loaded);
      sender.sendMessage(ChatColor.GREEN + "OrbisHud reloaded. profiles=" + loaded.profileIds().size());
      plugin.getLogger().info("OrbisHud reload requested by " + actor(sender) + ".");
      return true;
    }

    if (sub.equals("profile")) {
      if (!has(sender, "orbishud.profile.set")) return noPermission(sender);
      if (args.length < 3) {
        sender.sendMessage(ChatColor.RED + "Usage: /" + label + " profile <player> <profile|default>");
        return true;
      }

      Player target = Bukkit.getPlayerExact(args[1]);
      if (target == null) {
        sender.sendMessage(ChatColor.RED + "Player not online: " + args[1]);
        return true;
      }

      String requested = args[2].toLowerCase(Locale.ROOT);
      if (requested.equals("default")) {
        service.clearProfile(target.getUniqueId());
        sender.sendMessage(ChatColor.GREEN + "Cleared forced profile for " + target.getName() + ".");
        plugin.getLogger().info("OrbisHud profile cleared for " + target.getName() + " by " + actor(sender) + ".");
        return true;
      }

      boolean ok = service.setProfile(target.getUniqueId(), requested);
      if (!ok) {
        sender.sendMessage(ChatColor.RED + "Unknown profile: " + requested);
        sender.sendMessage(ChatColor.GRAY + "Available: " + String.join(", ", service.availableProfiles()));
        return true;
      }

      sender.sendMessage(ChatColor.GREEN + "Set HUD profile for " + target.getName() + " to " + requested + ".");
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
    sender.sendMessage(ChatColor.RED + "No permission.");
    return true;
  }

  private void sendHelp(CommandSender sender) {
    sender.sendMessage(ChatColor.AQUA + "OrbisHud Commands");
    if (has(sender, "orbishud.admin")) {
      sender.sendMessage(ChatColor.GRAY + "/orbishud status");
    }
    if (has(sender, "orbishud.reload")) {
      sender.sendMessage(ChatColor.GRAY + "/orbishud reload");
    }
    if (has(sender, "orbishud.profile.set")) {
      sender.sendMessage(ChatColor.GRAY + "/orbishud profile <player> <profile|default>");
    }
    sender.sendMessage(ChatColor.DARK_GRAY + "Profiles: " + String.join(", ", service.availableProfiles()));
  }

  private static String actor(CommandSender sender) {
    return sender instanceof Player p ? p.getName() : sender.getName();
  }
}
