package net.orbis.zakum.crates;

import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.api.action.AceEngine;
import net.orbis.zakum.api.vault.EconomyService;
import net.orbis.zakum.crates.model.RewardDef;
import net.orbis.zakum.crates.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Legacy reward executor - replaced by RewardSystemManager.
 * Kept for backward compatibility but not actively used.
 * 
 * @deprecated Use {@link net.orbis.zakum.crates.reward.RewardSystemManager} instead
 */
@Deprecated
public final class CrateRewardExecutor {

  private final EconomyService economy;

  public CrateRewardExecutor(EconomyService economy) {
    this.economy = economy;
  }

  public void execute(Player p, RewardDef reward) {
    // Messages
    for (String msg : reward.messages()) {
      if (!msg.isBlank()) {
        p.sendMessage(ItemBuilder.color(msg));
      }
    }

    // Commands
    executeCommands(p, reward.commands());

    // Items
    giveItems(p, reward.items());
  }

  private void executeCommands(Player player, List<String> commands) {
    if (commands == null || commands.isEmpty()) return;
    
    List<String> script = commands.stream()
      .filter(cmd -> cmd != null && !cmd.isBlank())
      .map(cmd -> cmd
        .replace("{player}", player.getName())
        .replace("{uuid}", player.getUniqueId().toString()))
      .map(cmd -> cmd.startsWith("[") ? cmd : "[COMMAND] " + cmd)
      .collect(Collectors.toList());

    if (script.isEmpty()) return;
    ZakumApi.get().getAceEngine().executeScript(script, AceEngine.ActionContext.of(player));
  }

  private void giveItems(Player p, List<ItemStack> items) {
    if (items == null) return;
    for (ItemStack it : items) {
      if (it == null || it.getType().isAir()) continue;
      var leftover = p.getInventory().addItem(it);
      if (!leftover.isEmpty()) {
        for (var e : leftover.values()) {
          p.getWorld().dropItemNaturally(p.getLocation(), e);
        }
      }
    }
  }
}
