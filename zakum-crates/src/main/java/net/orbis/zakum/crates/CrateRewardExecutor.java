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

    executeAceCommands(p, reward.commands(), reward.economyAmount(), true);
    executeAceCommands(p, reward.script(), reward.economyAmount(), false);

    giveItems(p, reward.items());
  }

  private void executeAceCommands(Player player, List<String> lines, double amount, boolean commandMode) {
    if (lines == null || lines.isEmpty()) return;
    List<String> script = lines.stream()
      .filter(cmd -> cmd != null && !cmd.isBlank())
      .map(cmd -> cmd
        .replace("{player}", player.getName())
        .replace("{uuid}", player.getUniqueId().toString())
        .replace("{amount}", String.valueOf(amount)))
      .map(cmd -> commandMode && !cmd.startsWith("[") ? "[COMMAND] " + cmd : cmd)
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
