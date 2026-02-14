package net.orbis.zakum.battlepass.ui;

import net.orbis.zakum.battlepass.BattlePassRuntime;
import net.orbis.zakum.battlepass.rewards.RewardTrack;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

public final class BattlePassMenuListener implements Listener {

  private final BattlePassRuntime runtime;
  private final BattlePassMenus menus;

  public BattlePassMenuListener(BattlePassRuntime runtime, BattlePassMenus menus) {
    this.runtime = runtime;
    this.menus = menus;
  }

  @EventHandler(ignoreCancelled = true)
  public void onClick(InventoryClickEvent e) {
    if (!(e.getWhoClicked() instanceof Player p)) return;
    if (!(e.getInventory().getHolder() instanceof BpMenuHolder h)) return;
    if (!p.getUniqueId().equals(h.viewer())) return;

    e.setCancelled(true);

    int slot = e.getRawSlot();
    if (slot < 0) return;

    if (h.type() == MenuType.MAIN) {
      if (slot == 11) menus.openRewards(p, 1);
      else if (slot == 13) menus.openQuests(p, 1);
      else if (slot == 15) menus.sendLeaderboard(p, 1);
      else if (slot == 22) p.closeInventory();
      else if (slot == 26) {
        if (p.hasPermission("orbis.battlepass.admin")) {
          p.closeInventory();
          p.performCommand("battlepass edit");
        }
      }
      return;
    }

    if (h.type() == MenuType.REWARDS) {
      if (slot == 49) { menus.openMain(p); return; }
      if (slot == 45) { menus.openRewards(p, Math.max(1, h.page() - 1)); return; }
      if (slot == 53) { menus.openRewards(p, h.page() + 1); return; }

      // tier slots 0..44
      if (slot >= 0 && slot <= 44) {
        int tier = (h.page() - 1) * 45 + slot + 1;

        RewardTrack track;
        ClickType ct = e.getClick();
        if (ct.isRightClick()) track = RewardTrack.PREMIUM;
        else if (ct.isShiftClick()) track = RewardTrack.BOTH;
        else track = RewardTrack.FREE;

        var res = runtime.claim(p, tier, track);
        p.sendMessage(res.ok() ? (ChatColor.GREEN + res.message()) : (ChatColor.RED + res.message()));

        // re-open same page to reflect claim status
        menus.openRewards(p, h.page());
      }
      return;
    }

    if (h.type() == MenuType.QUESTS) {
      if (slot == 49) { menus.openMain(p); return; }
      if (slot == 45) { menus.openQuests(p, Math.max(1, h.page() - 1)); return; }
      if (slot == 53) { menus.openQuests(p, h.page() + 1); return; }
    }
  }
}
