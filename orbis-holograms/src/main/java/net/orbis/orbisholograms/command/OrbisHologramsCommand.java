package net.orbis.orbisholograms.command;

import net.orbis.orbisholograms.OrbisHologramsPlugin;
import net.orbis.orbisholograms.config.HologramsConfig;
import net.orbis.orbisholograms.service.HologramsService;
import net.orbis.orbisholograms.service.HologramsStatus;
import net.orbis.zakum.api.util.BrandingText;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class OrbisHologramsCommand implements CommandExecutor, TabCompleter {

  private final OrbisHologramsPlugin plugin;
  private final HologramsService service;

  public OrbisHologramsCommand(OrbisHologramsPlugin plugin, HologramsService service) {
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
      if (!has(sender, "orbisholograms.admin")) return noPermission(sender);
      HologramsStatus status = service.snapshot();
      send(sender, "&8[<gradient:#38bdf8:#60a5fa>Orbis</gradient>&8] &bHolograms Status");
      send(sender, "&7running=&f" + status.running());
      send(sender, "&7enabled=&f" + status.enabled());
      send(sender, "&7renderTickInterval=&f" + status.renderTickInterval());
      send(sender, "&7viewDistance=&f" + status.viewDistance());
      send(sender, "&7maxVisiblePerPlayer=&f" + status.maxVisiblePerPlayer());
      send(sender, "&7hideThroughWalls=&f" + status.hideThroughWalls());
      send(sender, "&7definitions=&f" + status.definitions());
      send(sender, "&7visibleAssignments=&f" + status.visibleAssignments());
      send(sender, "&7taskId=&f" + status.taskId());
      return true;
    }

    if (sub.equals("reload")) {
      if (!has(sender, "orbisholograms.reload")) return noPermission(sender);
      plugin.reloadConfig();
      HologramsConfig loaded = HologramsConfig.load(plugin.getConfig(), plugin.getLogger());
      service.reload(loaded);
      send(sender, "&aOrbis Holograms reloaded. &7definitions=&f" + loaded.definitions().size());
      return true;
    }

    sendHelp(sender);
    return true;
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
    if (args.length == 1) {
      List<String> values = new ArrayList<>();
      if (has(sender, "orbisholograms.admin")) values.add("status");
      if (has(sender, "orbisholograms.reload")) values.add("reload");
      return prefix(values, args[0]);
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

  private boolean has(CommandSender sender, String permission) {
    return sender.hasPermission("orbisholograms.admin") || sender.hasPermission(permission);
  }

  private static boolean noPermission(CommandSender sender) {
    send(sender, "&cNo permission.");
    return true;
  }

  private void sendHelp(CommandSender sender) {
    send(sender, "&8[<gradient:#38bdf8:#60a5fa>Orbis</gradient>&8] &bHolograms Commands");
    if (has(sender, "orbisholograms.admin")) send(sender, "&7/orbishologram status");
    if (has(sender, "orbisholograms.reload")) send(sender, "&7/orbishologram reload");
  }

  private static void send(CommandSender sender, String message) {
    sender.sendMessage(BrandingText.render(message));
  }
}
