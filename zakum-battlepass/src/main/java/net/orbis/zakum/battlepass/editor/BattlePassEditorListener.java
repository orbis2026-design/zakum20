package net.orbis.zakum.battlepass.editor;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

public final class BattlePassEditorListener implements Listener {

  private final BattlePassEditor editor;

  public BattlePassEditorListener(BattlePassEditor editor) {
    this.editor = editor;
  }

  @EventHandler(ignoreCancelled = true)
  public void onClick(InventoryClickEvent e) {
    if (!(e.getWhoClicked() instanceof Player p)) return;
    if (!(e.getInventory().getHolder() instanceof BpEditorHolder h)) return;
    if (!p.getUniqueId().equals(h.viewer())) return;

    e.setCancelled(true);

    int slot = e.getRawSlot();
    if (slot < 0) return;

    ClickType ct = e.getClick();

    switch (h.type()) {
      case ADMIN_HOME -> {
        if (slot == 11) editor.openQuestList(p, 1);
        else if (slot == 15) editor.openTierList(p, 1);
        else if (slot == 13) {
          // Validate + backup (non-blocking, command surface exists too)
          p.performCommand("battlepass yaml validate");
          p.performCommand("battlepass yaml backup all");
          editor.openHome(p);
        }
        else if (slot == 22) p.closeInventory();
        else if (slot == 26) {
          p.closeInventory();
          p.performCommand("battlepass menu");
        }
      }

      case QUEST_LIST -> {
        if (slot == 49) editor.openHome(p);
        else if (slot == 45) editor.openQuestList(p, Math.max(1, h.page() - 1));
        else if (slot == 53) editor.openQuestList(p, h.page() + 1);
        else if (slot >= 0 && slot <= 44) {
          // map slot -> quest index by page
          int idx = (h.page() - 1) * 45 + slot;
          // questId is stored in display name, strip color
          if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null) return;
          String questId = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
          if (questId == null || questId.isBlank()) return;

          if (ct.isRightClick()) {
            editor.toggleQuestEnabled(p, questId, () -> editor.openQuestList(p, h.page()));
          } else {
            editor.openQuestDetail(p, questId);
          }
        }
      }

      case QUEST_DETAIL -> {
        String questId = h.a();
        if (questId == null) { editor.openQuestList(p, 1); return; }

        if (slot == 26) editor.openQuestList(p, 1);
        else if (slot == 10) editor.toggleQuestEnabled(p, questId, () -> editor.openQuestDetail(p, questId));
        else if (slot == 11) editor.prompt(p, EditKind.QUEST_NAME, questId, null, -1, "Enter new quest name");
        else if (slot == 12) editor.prompt(p, EditKind.QUEST_POINTS, questId, null, -1, "Enter points (number)");
        else if (slot == 13) editor.toggleQuestBool(p, questId, "premiumOnly", () -> editor.openQuestDetail(p, questId));
        else if (slot == 14) editor.prompt(p, EditKind.QUEST_PREMIUM_BONUS, questId, null, -1, "Enter premium bonus points (number)");
        else if (slot == 15) editor.cycleQuestCadence(p, questId, () -> editor.openQuestDetail(p, questId));
        else if (slot == 16) editor.prompt(p, EditKind.QUEST_WEEKS, questId, null, -1, "Enter weeks list (e.g. 1,2,3) or 'none'");
        else if (slot == 22) editor.openQuestSteps(p, questId, 1);
      }

      case QUEST_STEPS -> {
        String questId = h.a();
        if (questId == null) { editor.openQuestList(p, 1); return; }

        if (slot == 49) editor.openQuestDetail(p, questId);
        else if (slot == 45) editor.openQuestSteps(p, questId, Math.max(1, h.page() - 1));
        else if (slot == 53) {
          editor.prompt(p, EditKind.STEP_ADD, questId, null, -1, "Enter: TYPE key value required (or use | as separators)");
        }
        else if (slot >= 0 && slot <= 44) {
          int stepIndex = (h.page() - 1) * 45 + slot;
          if (ct == ClickType.SHIFT_RIGHT) {
            editor.deleteStep(p, questId, stepIndex, () -> editor.openQuestSteps(p, questId, h.page()));
            return;
          }
          if (ct == ClickType.SHIFT_LEFT) {
            editor.prompt(p, EditKind.STEP_VALUE, questId, null, stepIndex, "Enter new step value");
            return;
          }
          editor.prompt(p, EditKind.STEP_REQUIRED, questId, null, stepIndex, "Enter new required count (number)");
        }
      }

      case REWARD_TIER_LIST -> {
        if (slot == 49) editor.openHome(p);
        else if (slot == 45) editor.openTierList(p, Math.max(1, h.page() - 1));
        else if (slot == 53) editor.prompt(p, EditKind.TIER_ADD, null, null, -1, "Enter: <tier> <pointsRequired>");
        else if (slot >= 0 && slot <= 44) {
          if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null) return;
          String dn = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
          if (dn == null) return;
          // "Tier N"
          String[] parts = dn.split("\\s+");
          if (parts.length < 2) return;
          int tier;
          try { tier = Integer.parseInt(parts[1]); } catch (NumberFormatException ex) { return; }
          editor.openTierDetail(p, tier);
        }
      }

      case REWARD_TIER_DETAIL -> {
        int tier;
        try { tier = Integer.parseInt(h.a()); } catch (Exception ex) { editor.openTierList(p, 1); return; }

        if (slot == 26) editor.openTierList(p, 1);
        else if (slot == 11) editor.prompt(p, EditKind.TIER_POINTS_REQUIRED, String.valueOf(tier), null, -1, "Enter pointsRequired (number)");
        else if (slot == 13) editor.openRewardList(p, tier, "free", 1);
        else if (slot == 15) editor.openRewardList(p, tier, "premium", 1);
      }

      case REWARD_LIST -> {
        int tier;
        try { tier = Integer.parseInt(h.a()); } catch (Exception ex) { editor.openTierList(p, 1); return; }
        String track = h.b();

        if (slot == 49) editor.openTierDetail(p, tier);
        else if (slot == 45) editor.openRewardList(p, tier, track, Math.max(1, h.page() - 1));
        else if (slot == 53) editor.prompt(p, EditKind.REWARD_ADD, String.valueOf(tier), track, -1, "Enter console command for new reward");
        else if (slot >= 0 && slot <= 44) {
          if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null) return;
          String rewardId = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
          if (rewardId == null) return;
          // displayName is "id (TYPE)" -> grab first token
          String rid = rewardId.split("\\s+")[0];

          if (ct == ClickType.SHIFT_RIGHT) {
            editor.deleteReward(p, tier, track, rid, () -> editor.openRewardList(p, tier, track, h.page()));
            return;
          }

          editor.openRewardCommands(p, tier, track, rid, 1);
        }
      }

      case REWARD_COMMANDS -> {
        int tier;
        try { tier = Integer.parseInt(h.a()); } catch (Exception ex) { editor.openTierList(p, 1); return; }
        String trackReward = h.b();
        String[] parts = trackReward == null ? new String[]{"free","r1"} : trackReward.split(":", 2);
        String track = parts.length > 0 ? parts[0] : "free";
        String rewardId = parts.length > 1 ? parts[1] : "r1";

        if (slot == 49) editor.openRewardList(p, tier, track, 1);
        else if (slot == 45) editor.openRewardCommands(p, tier, track, rewardId, Math.max(1, h.page() - 1));
        else if (slot == 53) editor.prompt(p, EditKind.REWARD_ADD_COMMAND, String.valueOf(tier), track + ":" + rewardId, -1, "Enter console command to append");
        else if (slot >= 0 && slot <= 44) {
          if (ct != ClickType.SHIFT_RIGHT) return;
          int cmdIndex = (h.page() - 1) * 45 + slot;
          editor.deleteCommand(p, tier, track, rewardId, cmdIndex, () -> editor.openRewardCommands(p, tier, track, rewardId, h.page()));
        }
      }
    }
  }
}
