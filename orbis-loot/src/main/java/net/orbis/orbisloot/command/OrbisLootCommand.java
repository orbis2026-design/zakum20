package net.orbis.orbisloot.command;

import net.orbis.orbisloot.OrbisLootPlugin;
import net.orbis.orbisloot.config.LootConfig;
import net.orbis.orbisloot.service.LootService;
import net.orbis.orbisloot.service.LootStatus;
import net.orbis.zakum.api.util.BrandingText;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class OrbisLootCommand implements CommandExecutor, TabCompleter {

  private static final DecimalFormat PERCENT = new DecimalFormat("0.00");

  private final OrbisLootPlugin plugin;
  private final LootService service;

  public OrbisLootCommand(OrbisLootPlugin plugin, LootService service) {
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
      if (!has(sender, "orbisloot.admin")) return noPermission(sender);
      LootStatus status = service.snapshot();
      send(sender, "&8[<gradient:#38bdf8:#60a5fa>Orbis</gradient>&8] &bLoot Status");
      send(sender, "&7running=&f" + status.running());
      send(sender, "&7enabled=&f" + status.enabled());
      send(sender, "&7cleanupIntervalTicks=&f" + status.cleanupIntervalTicks());
      send(sender, "&7openCooldownSeconds=&f" + status.openCooldownSeconds());
      send(sender, "&7maxRewardsPerOpen=&f" + status.maxRewardsPerOpen());
      send(sender, "&7crates=&f" + status.crates());
      send(sender, "&7activeCooldownEntries=&f" + status.activeCooldownEntries());
      send(sender, "&7taskId=&f" + status.taskId());
      return true;
    }

    if (sub.equals("reload")) {
      if (!has(sender, "orbisloot.reload")) return noPermission(sender);
      plugin.reloadConfig();
      LootConfig loaded = LootConfig.load(plugin.getConfig(), plugin.getLogger());
      service.reload(loaded);
      send(sender, "&aOrbis Loot reloaded. &7crates=&f" + loaded.crates().size());
      return true;
    }

    if (sub.equals("simulate")) {
      if (!has(sender, "orbisloot.simulate")) return noPermission(sender);
      if (args.length < 3) {
        send(sender, "&cUsage: /" + label + " simulate <crate> <rolls>");
        return true;
      }

      int rolls;
      try {
        rolls = Integer.parseInt(args[2]);
      } catch (NumberFormatException ignored) {
        send(sender, "&cInvalid rolls value: " + args[2]);
        return true;
      }

      if (rolls < 1 || rolls > 1_000_000) {
        send(sender, "&cRolls must be between 1 and 1,000,000.");
        return true;
      }

      Map<String, Integer> counts = service.simulate(args[1], rolls);
      if (counts.isEmpty()) {
        send(sender, "&cUnknown crate or no rewards: " + args[1]);
        return true;
      }

      send(sender, "&8[<gradient:#38bdf8:#60a5fa>Orbis</gradient>&8] &bSimulation &7(" + args[1] + ", rolls=" + rolls + ")");
      counts.entrySet().stream()
        .sorted(Comparator.comparingInt((Map.Entry<String, Integer> e) -> e.getValue()).reversed())
        .forEach(entry -> {
          double pct = (double) entry.getValue() * 100.0D / (double) rolls;
          send(
            sender,
            "&7" + entry.getKey() + "=&f" + entry.getValue() + "&8 (" + PERCENT.format(pct) + "%)"
          );
        });
      return true;
    }

    sendHelp(sender);
    return true;
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
    if (args.length == 1) {
      List<String> values = new ArrayList<>();
      if (has(sender, "orbisloot.admin")) values.add("status");
      if (has(sender, "orbisloot.reload")) values.add("reload");
      if (has(sender, "orbisloot.simulate")) values.add("simulate");
      return prefix(values, args[0]);
    }
    if (args.length == 2 && args[0].equalsIgnoreCase("simulate") && has(sender, "orbisloot.simulate")) {
      return prefix(new ArrayList<>(service.crateIds()), args[1]);
    }
    return List.of();
  }

  private static List<String> prefix(List<String> source, String token) {
    String needle = token == null ? "" : token.toLowerCase(Locale.ROOT);
    return source.stream()
      .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(needle))
      .collect(Collectors.toList());
  }

  private boolean has(CommandSender sender, String permission) {
    return sender.hasPermission("orbisloot.admin") || sender.hasPermission(permission);
  }

  private static boolean noPermission(CommandSender sender) {
    send(sender, "&cNo permission.");
    return true;
  }

  private void sendHelp(CommandSender sender) {
    send(sender, "&8[<gradient:#38bdf8:#60a5fa>Orbis</gradient>&8] &bLoot Commands");
    if (has(sender, "orbisloot.admin")) send(sender, "&7/orbisloot status");
    if (has(sender, "orbisloot.reload")) send(sender, "&7/orbisloot reload");
    if (has(sender, "orbisloot.simulate")) send(sender, "&7/orbisloot simulate <crate> <rolls>");
  }

  private static void send(CommandSender sender, String message) {
    sender.sendMessage(BrandingText.render(message));
  }
}
