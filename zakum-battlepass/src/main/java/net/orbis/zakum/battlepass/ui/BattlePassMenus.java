package net.orbis.zakum.battlepass.ui;

import net.orbis.zakum.battlepass.BattlePassRuntime;
import net.orbis.zakum.battlepass.leaderboard.BattlePassLeaderboard;
import net.orbis.zakum.battlepass.model.QuestDef;
import net.orbis.zakum.battlepass.model.QuestStep;
import net.orbis.zakum.battlepass.rewards.RewardTrack;
import net.orbis.zakum.battlepass.rewards.TierRewards;
import net.orbis.zakum.battlepass.state.PlayerBpState;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Event-driven menus (no refresh loop).
 */
public final class BattlePassMenus {

  private final BattlePassRuntime runtime;
  private final BattlePassLeaderboard leaderboard;
  private final NameCache names;

  public BattlePassMenus(BattlePassRuntime runtime, BattlePassLeaderboard leaderboard, NameCache names) {
    this.runtime = runtime;
    this.leaderboard = leaderboard;
    this.names = names;
  }

  public void openMain(Player p) {
    BpMenuHolder holder = new BpMenuHolder(p.getUniqueId(), MenuType.MAIN, 1);
    Inventory inv = Bukkit.createInventory(holder, 27, ChatColor.LIGHT_PURPLE + "Orbis BattlePass");
    holder.bind(inv);

    PlayerBpState st = runtime.state(p.getUniqueId());

    inv.setItem(11, item(Material.CHEST, ChatColor.AQUA + "Rewards",
      lore(
        ChatColor.GRAY + "Claim tier rewards.",
        ChatColor.DARK_GRAY + "Left click"
      )
    ));
    inv.setItem(13, item(Material.BOOK, ChatColor.YELLOW + "Quests",
      lore(ChatColor.GRAY + "View quest progress.")
    ));
    inv.setItem(15, item(Material.PAPER, ChatColor.GREEN + "Leaderboard",
      lore(ChatColor.GRAY + "Top points this season.")
    ));

    if (st != null) {
      inv.setItem(4, item(Material.NETHER_STAR, ChatColor.LIGHT_PURPLE + "Your Progress",
        lore(
          ChatColor.GRAY + "Tier: " + ChatColor.AQUA + st.tier(),
          ChatColor.GRAY + "Points: " + ChatColor.AQUA + st.points(),
          ChatColor.GRAY + "Premium: " + (st.premium ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No")
        )
      ));
    } else {
      inv.setItem(4, item(Material.NETHER_STAR, ChatColor.LIGHT_PURPLE + "Your Progress",
        lore(ChatColor.GRAY + "Loading...")
      ));
    }

    if (p.hasPermission("orbis.battlepass.admin")) {
  inv.setItem(26, item(Material.COMMAND_BLOCK, ChatColor.DARK_AQUA + "Admin Editor",
    lore(
      ChatColor.GRAY + "Edit quests.yml / rewards.yml in-game.",
      ChatColor.DARK_GRAY + "Click"
    )
  ));
}

inv.setItem(22, item(Material.BARRIER, ChatColor.RED + "Close", List.of()));
    p.openInventory(inv);
  }

  public void openRewards(Player p, int page) {
    int pg = Math.max(1, page);
    BpMenuHolder holder = new BpMenuHolder(p.getUniqueId(), MenuType.REWARDS, pg);
    Inventory inv = Bukkit.createInventory(holder, 54, ChatColor.AQUA + "BattlePass Rewards");
    holder.bind(inv);

    PlayerBpState st = runtime.state(p.getUniqueId());
    int curTier = st == null ? 0 : st.tier();
    boolean prem = st != null && st.premium;

    int perPage = 45;
    int startTier = (pg - 1) * perPage + 1;
    int endTier = Math.min(runtime.rewards().maxTier(), startTier + perPage - 1);

    int slot = 0;
    for (int tier = startTier; tier <= endTier; tier++, slot++) {
      Optional<TierRewards> tr = runtime.rewards().tier(tier);
      if (tr.isEmpty()) continue;

      boolean reached = curTier >= tier;
      boolean claimedFree = st != null && st.hasClaim(false, tier);
      boolean claimedPrem = st != null && st.hasClaim(true, tier);

      Material mat = reached ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
      String name = (reached ? ChatColor.GREEN : ChatColor.DARK_GRAY) + "Tier " + tier;

      List<String> lore = new ArrayList<>();
      lore.add(ChatColor.GRAY + "Required: " + ChatColor.AQUA + tr.get().pointsRequired() + " points");
      lore.add(ChatColor.GRAY + "Status: " + (reached ? ChatColor.GREEN + "Unlocked" : ChatColor.RED + "Locked"));

      lore.add(" ");
      lore.add(ChatColor.WHITE + "Free: " + (claimedFree ? ChatColor.GREEN + "Claimed" : (reached ? ChatColor.YELLOW + "Unclaimed" : ChatColor.DARK_GRAY + "Locked")));
      lore.add(ChatColor.WHITE + "Premium: " + (prem
        ? (claimedPrem ? ChatColor.GREEN + "Claimed" : (reached ? ChatColor.YELLOW + "Unclaimed" : ChatColor.DARK_GRAY + "Locked"))
        : ChatColor.DARK_GRAY + "No premium"));

      lore.add(" ");
      lore.add(ChatColor.DARK_GRAY + "Left: claim free");
      lore.add(ChatColor.DARK_GRAY + "Right: claim premium");
      lore.add(ChatColor.DARK_GRAY + "Shift+Left: claim both");

      inv.setItem(slot, item(mat, name, lore));
    }

    // Nav
    inv.setItem(49, item(Material.ARROW, ChatColor.YELLOW + "Back", lore(ChatColor.GRAY + "Main menu")));
    inv.setItem(45, item(Material.ARROW, ChatColor.GRAY + "Prev", List.of()));
    inv.setItem(53, item(Material.ARROW, ChatColor.GRAY + "Next", List.of()));

    p.openInventory(inv);
  }

  public void openQuests(Player p, int page) {
    int pg = Math.max(1, page);
    BpMenuHolder holder = new BpMenuHolder(p.getUniqueId(), MenuType.QUESTS, pg);
    Inventory inv = Bukkit.createInventory(holder, 54, ChatColor.YELLOW + "BattlePass Quests");
    holder.bind(inv);

    PlayerBpState st = runtime.state(p.getUniqueId());

    List<QuestDef> all = new ArrayList<>(runtime.allQuests());
    all.sort(Comparator.comparing(QuestDef::id));

    int perPage = 45;
    int from = (pg - 1) * perPage;
    int to = Math.min(all.size(), from + perPage);

    int slot = 0;
    for (int i = from; i < to; i++, slot++) {
      QuestDef q = all.get(i);

      var ss = (st == null) ? new PlayerBpState.StepStateSnap(0, 0) : st.getQuest(q.id());
      int idx = Math.max(0, ss.stepIdx());
      long prog = Math.max(0, ss.progress());

      int steps = q.steps().size();
      boolean complete = idx >= steps;

      QuestStep step = complete ? q.steps().get(steps - 1) : q.steps().get(idx);
      long req = Math.max(1, step.required());

      Material mat = complete ? Material.LIME_DYE : Material.PAPER;
      String name = (complete ? ChatColor.GREEN : ChatColor.WHITE) + q.name();

      List<String> lore = new ArrayList<>();
      lore.add(ChatColor.DARK_GRAY + q.id());
      lore.add(ChatColor.GRAY + "Cadence: " + ChatColor.AQUA + q.cadence().name());
      lore.add(ChatColor.GRAY + "Points: " + ChatColor.AQUA + q.points());
      lore.add(ChatColor.GRAY + "Premium-only: " + (q.premiumOnly() ? ChatColor.RED + "Yes" : ChatColor.GREEN + "No"));
      lore.add(" ");
      lore.add(ChatColor.GRAY + "Step: " + ChatColor.AQUA + Math.min(idx + 1, steps) + ChatColor.GRAY + "/" + ChatColor.AQUA + steps);
      lore.add(ChatColor.GRAY + "Progress: " + ChatColor.AQUA + Math.min(prog, req) + ChatColor.GRAY + "/" + ChatColor.AQUA + req);
      lore.add(ChatColor.DARK_GRAY + "Match: " + step.type() + " " + step.key() + "=" + step.value());

      inv.setItem(slot, item(mat, name, lore));
    }

    inv.setItem(49, item(Material.ARROW, ChatColor.YELLOW + "Back", lore(ChatColor.GRAY + "Main menu")));
    inv.setItem(45, item(Material.ARROW, ChatColor.GRAY + "Prev", List.of()));
    inv.setItem(53, item(Material.ARROW, ChatColor.GRAY + "Next", List.of()));

    p.openInventory(inv);
  }

  public void sendLeaderboard(Player p, int page) {
    int pg = Math.max(1, page);
    List<BattlePassLeaderboard.Entry> list = leaderboard.page(pg, 10);

    p.sendMessage(ChatColor.LIGHT_PURPLE + "BattlePass Top (page " + pg + ")");
    if (list.isEmpty()) {
      p.sendMessage(ChatColor.GRAY + "No entries yet.");
      return;
    }

    int startRank = (pg - 1) * 10 + 1;
    for (int i = 0; i < list.size(); i++) {
      var e = list.get(i);
      String name = names.get(e.uuid());
      if (name == null) name = e.uuid().toString().substring(0, 8);

      p.sendMessage(ChatColor.GRAY + "" + (startRank + i) + ". "
        + ChatColor.AQUA + name
        + ChatColor.DARK_GRAY + " | "
        + ChatColor.GRAY + "Tier " + ChatColor.AQUA + e.tier()
        + ChatColor.DARK_GRAY + " | "
        + ChatColor.GRAY + "Points " + ChatColor.AQUA + e.points()
      );
    }
  }

  private static ItemStack item(Material mat, String name, List<String> lore) {
    ItemStack it = new ItemStack(mat);
    ItemMeta meta = it.getItemMeta();
    if (meta != null) {
      meta.setDisplayName(name);
      if (lore != null) meta.setLore(lore);
      meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
      it.setItemMeta(meta);
    }
    return it;
  }

  private static List<String> lore(String... lines) {
    if (lines == null) return List.of();
    return List.of(lines);
  }
}
