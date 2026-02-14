package net.orbis.zakum.battlepass.rewards;

import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.api.action.AceEngine;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

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
    String uuid = player.getUniqueId().toString().toLowerCase(Locale.ROOT);

    List<String> script = cmds.stream()
      .filter(raw -> raw != null && !raw.isBlank())
      .map(raw -> raw
        .replace("%player%", name)
        .replace("%uuid%", uuid))
      .map(cmd -> cmd.startsWith("[") ? cmd : "[COMMAND] " + cmd)
      .collect(Collectors.toList());

    AceEngine.ActionContext ctx = AceEngine.ActionContext.of(player);
    ZakumApi.get().getAceEngine().executeScript(script, ctx);
  }
}
