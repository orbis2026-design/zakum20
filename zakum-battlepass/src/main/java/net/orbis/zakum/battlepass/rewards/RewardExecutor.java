package net.orbis.zakum.battlepass.rewards;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Executes rewards (sync world interaction).
 *
 * v1: COMMAND rewards only.
 */
public final class RewardExecutor {

  private final Plugin plugin;

  public RewardExecutor(Plugin plugin) {
    this.plugin = plugin;
  }

  public void execute(Player player, List<RewardDef> rewards) {
    if (player == null || rewards == null || rewards.isEmpty()) return;

    for (RewardDef r : rewards) {
      if (r == null) continue;

      if (r.type() == RewardType.COMMAND) {
        execCommands(player, r.commands());
      }
    }
  }

  private void execCommands(Player player, List<String> cmds) {
    if (cmds == null || cmds.isEmpty()) return;

    String name = player.getName();
    UUID uuid = player.getUniqueId().toString().toLowerCase(Locale.ROOT);

    for (String raw : cmds) {
      if (raw == null) continue;
      String cmd = raw
        .replace("%player%", name)
        .replace("%uuid%", uuid);

      // must run on main thread
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }
  }
}
