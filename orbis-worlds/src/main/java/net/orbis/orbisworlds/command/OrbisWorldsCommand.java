package net.orbis.orbisworlds.command;

import net.orbis.orbisworlds.OrbisWorldsPlugin;
import net.orbis.orbisworlds.config.WorldsConfig;
import net.orbis.orbisworlds.service.WorldsService;
import net.orbis.orbisworlds.service.WorldsStatus;
import net.orbis.zakum.api.util.BrandingText;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class OrbisWorldsCommand implements CommandExecutor, TabCompleter {

  private final OrbisWorldsPlugin plugin;
  private final WorldsService service;

  public OrbisWorldsCommand(OrbisWorldsPlugin plugin, WorldsService service) {
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
      if (!has(sender, "orbisworlds.admin")) return noPermission(sender);
      WorldsStatus status = service.snapshot();
      send(sender, "&8[<gradient:#38bdf8:#60a5fa>Orbis</gradient>&8] &bWorlds Status");
      send(sender, "&7running=&f" + status.running());
      send(sender, "&7enabled=&f" + status.enabled());
      send(sender, "&7updateIntervalTicks=&f" + status.updateIntervalTicks());
      send(sender, "&7managedWorlds=&f" + status.managedWorlds());
      send(sender, "&7loadedManagedWorlds=&f" + status.loadedManagedWorlds());
      send(sender, "&7autoLoad=&f" + status.autoLoad());
      send(sender, "&7safeTeleport=&f" + status.safeTeleport());
      send(sender, "&7maxParallelWorldLoads=&f" + status.maxParallelWorldLoads());
      send(sender, "&7taskId=&f" + status.taskId());
      return true;
    }

    if (sub.equals("reload")) {
      if (!has(sender, "orbisworlds.reload")) return noPermission(sender);
      plugin.reloadConfig();
      WorldsConfig loaded = WorldsConfig.load(plugin.getConfig(), plugin.getLogger());
      service.reload(loaded);
      send(sender, "&aOrbis Worlds reloaded. &7managedWorlds=&f" + loaded.managedWorlds().size());
      return true;
    }

    sendHelp(sender);
    return true;
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
    if (args.length == 1) {
      List<String> values = new ArrayList<>();
      if (has(sender, "orbisworlds.admin")) values.add("status");
      if (has(sender, "orbisworlds.reload")) values.add("reload");
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
    return sender.hasPermission("orbisworlds.admin") || sender.hasPermission(permission);
  }

  private static boolean noPermission(CommandSender sender) {
    send(sender, "&cNo permission.");
    return true;
  }

  private void sendHelp(CommandSender sender) {
    send(sender, "&8[<gradient:#38bdf8:#60a5fa>Orbis</gradient>&8] &bWorlds Commands");
    if (has(sender, "orbisworlds.admin")) send(sender, "&7/orbisworld status");
    if (has(sender, "orbisworlds.reload")) send(sender, "&7/orbisworld reload");
  }

  private static void send(CommandSender sender, String message) {
    sender.sendMessage(BrandingText.render(message));
  }
}
