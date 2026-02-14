package net.orbis.zakum.crates;

import net.orbis.zakum.crates.anim.CrateAnimator;
import net.orbis.zakum.crates.model.CrateDef;
import net.orbis.zakum.crates.util.ItemBuilder;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class CrateService {

  private final CrateAnimator animator;

  public CrateService(CrateAnimator animator) {
    this.animator = animator;
  }

  public void open(Player opener, CrateDef crate) {
    if (animator.isOpening(opener.getUniqueId())) {
      opener.sendMessage(ItemBuilder.color("&cYou're already opening a crate."));
      return;
    }

    if (!consumeKey(opener, crate.keyItem())) {
      opener.sendMessage(ItemBuilder.color("&cYou need a key to open this crate."));
      return;
    }

    broadcastOpen(opener, crate);

    boolean ok = animator.begin(opener, crate);
    if (!ok) {
      opener.getInventory().addItem(crate.keyItem().clone());
      opener.sendMessage(ItemBuilder.color("&cCould not start crate animation."));
    }
  }

  private void broadcastOpen(Player opener, CrateDef crate) {
    opener.playSound(opener.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);

    if (!crate.publicOpen()) return;

    int r = crate.publicRadius();
    double r2 = r * r;

    for (Player p : opener.getWorld().getPlayers()) {
      if (p.getLocation().distanceSquared(opener.getLocation()) > r2) continue;
      p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.15f);
      p.sendTitle(ItemBuilder.color("&bOpening..."), ItemBuilder.color(crate.name()), 0, 20, 10);
    }
  }

  private boolean consumeKey(Player p, ItemStack key) {
    ItemStack k = key.clone();
    k.setAmount(1);

    var inv = p.getInventory();
    for (int i = 0; i < inv.getSize(); i++) {
      ItemStack slot = inv.getItem(i);
      if (slot == null) continue;
      if (!slot.isSimilar(k)) continue;

      int amt = slot.getAmount();
      if (amt <= 1) inv.setItem(i, null);
      else slot.setAmount(amt - 1);
      return true;
    }
    return false;
  }
}
