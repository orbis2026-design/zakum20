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

public final class CrateRewardExecutor {

  private final EconomyService economy;

  public CrateRewardExecutor(EconomyService economy) {
    this.economy = economy;
  }

  public void execute(Player p, RewardDef reward) {
    if (reward.economyAmount() > 0.0 && economy != null && economy.available()) {
      economy.deposit(p.getUniqueId(), reward.economyAmount());
    }

    for (String msg : reward.messages()) {
      if (!msg.isBlank()) {
        p.sendMessage(ItemBuilder.color(msg).replace("{amount}", String.valueOf(reward.economyAmount())));
      }
    }

    executeAceCommands(p, reward);

    giveItems(p, reward.items());
  }

  private void executeAceCommands(Player player, RewardDef reward) {
    List<String> script = reward.commands().stream()
      .filter(cmd -> cmd != null && !cmd.isBlank())
      .map(cmd -> cmd
        .replace("{player}", player.getName())
        .replace("{amount}", String.valueOf(reward.economyAmount())))
      .map(cmd -> cmd.startsWith("[") ? cmd : "[COMMAND] " + cmd)
      .collect(Collectors.toList());

    if (script.isEmpty()) return;
    ZakumApi.get().getAceEngine().executeScript(script, AceEngine.ActionContext.of(player));
  }

  private void giveItems(Player p, List<ItemStack> items) {
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
