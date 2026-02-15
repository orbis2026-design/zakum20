package net.orbis.zakum.battlepass.rewards;

import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.api.action.AceEngine;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Executes rewards (sync world interaction).
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
      switch (r.type()) {
        case COMMAND -> execCommands(player, r.commands());
        case MESSAGE -> sendMessages(player, r.messages());
        case ACE_SCRIPT -> execAceScript(player, r.aceScript());
      }
    }
  }

  private void execCommands(Player player, List<String> cmds) {
    executeAceScript(player, cmds, true);
  }

  private void execAceScript(Player player, List<String> scriptLines) {
    executeAceScript(player, scriptLines, false);
  }

  private void executeAceScript(Player player, List<String> lines, boolean commandMode) {
    if (lines == null || lines.isEmpty()) return;

    List<String> script = new ArrayList<>(lines.size());
    for (String raw : lines) {
      if (raw == null || raw.isBlank()) continue;
      String prepared = applyPlaceholders(raw, player);
      if (commandMode && !prepared.startsWith("[")) {
        prepared = "[COMMAND] " + prepared;
      }
      script.add(prepared);
    }
    if (script.isEmpty()) return;

    AceEngine.ActionContext ctx = AceEngine.ActionContext.of(player);
    ZakumApi.get().getAceEngine().executeScript(script, ctx);
  }

  private void sendMessages(Player player, List<String> messages) {
    if (messages == null || messages.isEmpty()) return;
    for (String raw : messages) {
      if (raw == null || raw.isBlank()) continue;
      player.sendRichMessage(applyPlaceholders(raw, player));
    }
  }

  private String applyPlaceholders(String raw, Player player) {
    if (raw == null || raw.isBlank()) return "";
    String name = player.getName();
    String uuid = player.getUniqueId().toString().toLowerCase(Locale.ROOT);
    return raw
      .replace("%player%", name)
      .replace("%uuid%", uuid);
  }
}
