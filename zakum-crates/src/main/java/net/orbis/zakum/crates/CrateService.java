package net.orbis.zakum.crates;

import net.orbis.zakum.api.item.ZakumItem;
import net.orbis.zakum.crates.anim.CrateAnimatorV2;
import net.orbis.zakum.crates.model.CrateDef;
import net.orbis.zakum.crates.util.ItemBuilder;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class CrateService {

  private final CrateAnimatorV2 animator;

  public CrateService(CrateAnimatorV2 animator) {
    this.animator = animator;
  }

  public void open(Player opener, CrateDef crate) {
    if (animator.isOpening(opener.getUniqueId())) {
      opener.sendMessage(ItemBuilder.color("&cYou're already opening a crate."));
      return;
    }

    ItemStack consumed = consumeKey(opener, crate);
    if (consumed == null) {
      opener.sendMessage(ItemBuilder.color("&cYou need a key to open this crate."));
      return;
    }

    broadcastOpen(opener, crate);

    boolean ok = animator.begin(opener, crate, crate.animationType());
    if (!ok) {
      opener.getInventory().addItem(consumed);
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
      p.showTitle(net.kyori.adventure.title.Title.title(
        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
          .deserialize(ItemBuilder.color("&bOpening...")),
        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
          .deserialize(ItemBuilder.color(crate.name())),
        net.kyori.adventure.title.Title.Times.times(
          java.time.Duration.ZERO,
          java.time.Duration.ofSeconds(1),
          java.time.Duration.ofMillis(500)
        )
      ));
    }
  }

  private ItemStack consumeKey(Player p, CrateDef crate) {
    ItemStack key = crate.keyItem();
    if (key == null || key.getType().isAir()) return null;

    ItemStack k = key.clone();
    k.setAmount(1);
    String crateId = crate.id();

    var inv = p.getInventory();
    for (int i = 0; i < inv.getSize(); i++) {
      ItemStack slot = inv.getItem(i);
      if (slot == null) continue;
      if (!matchesKey(slot, k, crateId)) continue;

      int amt = slot.getAmount();
      ItemStack consumed = slot.clone();
      consumed.setAmount(1);
      if (amt <= 1) inv.setItem(i, null);
      else slot.setAmount(amt - 1);
      return consumed;
    }
    return null;
  }

  private boolean matchesKey(ItemStack candidate, ItemStack template, String crateId) {
    // Canonical path: key tagged with zakum:id.
    if (ZakumItem.hasId(candidate, crateId)) return true;
    // Backward compatibility: untagged legacy template checks.
    return candidate.isSimilar(template);
  }
}
