package net.orbis.zakum.crates.anim;

import net.orbis.zakum.api.util.WeightedTable;
import net.orbis.zakum.crates.gui.CrateGuiHolder;
import net.orbis.zakum.crates.model.CrateDef;
import net.orbis.zakum.crates.model.RewardDef;
import net.orbis.zakum.crates.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Single-ticker crate animation runner.
 *
 * - one repeating task total
 * - session map keyed by opener UUID
 * - GUI updates are bounded + lightweight
 */
public final class CrateAnimator {

  private final Plugin plugin;
  private final Random random = new Random();

  private final ConcurrentHashMap<UUID, CrateSession> sessions = new ConcurrentHashMap<>();

  private final int steps;
  private final int ticksPerStep;

  private int taskId = -1;

  private final BiConsumer<Player, RewardDef> rewardExecutor;

  public CrateAnimator(Plugin plugin, int steps, int ticksPerStep, BiConsumer<Player, RewardDef> rewardExecutor) {
    this.plugin = plugin;
    this.steps = Math.max(10, steps);
    this.ticksPerStep = Math.max(1, ticksPerStep);
    this.rewardExecutor = rewardExecutor;
  }

  public void start() {
    if (taskId != -1) return;
    taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, 1L, 1L);
  }

  public void shutdown() {
    if (taskId != -1) {
      Bukkit.getScheduler().cancelTask(taskId);
      taskId = -1;
    }

    // End sessions safely.
    for (CrateSession s : sessions.values()) {
      endSession(s, false);
    }
    sessions.clear();
  }

  public boolean isOpening(UUID opener) {
    return sessions.containsKey(opener);
  }

  public boolean begin(Player opener, CrateDef crate) {
    UUID id = opener.getUniqueId();
    if (sessions.containsKey(id)) return false;

    RewardDef finalReward = crate.rewards().pick(random);

    CrateGuiHolder holder = new CrateGuiHolder(id);
    Inventory inv = Bukkit.createInventory(holder, 27, ItemBuilder.color("&bCrate: &f" + crate.name()));
    holder.bind(inv);

    buildShell(inv);

    var s = new CrateSession(id, crate, opener.getLocation().clone(), finalReward, holder, inv, steps, ticksPerStep);

    // seed belt with random reward icons
    for (int i = 0; i < 9; i++) {
      s.belt[i] = icon(crate.rewards());
    }
    renderBelt(inv, s.belt);

    sessions.put(id, s);

    opener.openInventory(inv);
    opener.playSound(opener.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);
    opener.sendTitle(ItemBuilder.color("&bOpening..."), ItemBuilder.color(crate.name()), 0, 20, 10);

    return true;
  }

  public void markClosed(UUID opener) {
    CrateSession s = sessions.get(opener);
    if (s != null) s.inventoryClosed = true;
  }

  private void tick() {
    if (sessions.isEmpty()) return;

    for (CrateSession s : sessions.values()) {
      if (--s.tickCountdown > 0) continue;
      s.tickCountdown = s.ticksPerStep;

      s.stepIdx++;
      shiftBelt(s);
      if (!s.inventoryClosed) {
        renderBelt(s.inv, s.belt);
      }

      Player p = Bukkit.getPlayer(s.opener);
      if (p != null) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
      }

      if (s.stepIdx >= s.steps) {
        endSession(s, true);
        sessions.remove(s.opener);
      }
    }
  }

  private void endSession(CrateSession s, boolean grant) {
    Player p = Bukkit.getPlayer(s.opener);
    if (p != null) {
      if (!s.inventoryClosed) p.closeInventory();
      p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.1f);
    }

    if (grant && p != null) {
      rewardExecutor.accept(p, s.finalReward);
    }
  }

  private void shiftBelt(CrateSession s) {
    System.arraycopy(s.belt, 1, s.belt, 0, s.belt.length - 1);
    s.belt[s.belt.length - 1] = icon(s.crate.rewards());
  }

  private ItemStack icon(WeightedTable<RewardDef> rewards) {
    RewardDef r = rewards.pick(random);
    if (r.items() != null && !r.items().isEmpty()) {
      ItemStack it = r.items().get(0);
      if (it != null && !it.getType().isAir()) return it.clone();
    }

    ItemStack chest = new ItemStack(Material.CHEST);
    return chest;
  }

  private static void buildShell(Inventory inv) {
    ItemStack pane = ItemBuilder.pane();
    for (int i = 0; i < inv.getSize(); i++) {
      inv.setItem(i, pane);
    }
    // clear the belt row
    for (int i = 9; i <= 17; i++) inv.setItem(i, null);

    // pointer
    inv.setItem(4, ItemBuilder.pointer());
  }

  private static void renderBelt(Inventory inv, ItemStack[] belt) {
    for (int i = 0; i < 9; i++) {
      inv.setItem(9 + i, belt[i]);
    }
  }
}
