package net.orbis.zakum.crates;

import net.orbis.zakum.api.vault.EconomyService;
import net.orbis.zakum.crates.model.RewardDef;
import net.orbis.zakum.crates.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

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

    for (String cmd : reward.commands()) {
      String c = cmd
        .replace("{player}", p.getName())
        .replace("{amount}", String.valueOf(reward.economyAmount()));
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c);
    }

    giveItems(p, reward.items());
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
