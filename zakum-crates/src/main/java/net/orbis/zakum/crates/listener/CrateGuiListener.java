package net.orbis.zakum.crates.listener;

import net.orbis.zakum.crates.anim.CrateAnimator;
import net.orbis.zakum.crates.gui.CrateGuiHolder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

public final class CrateGuiListener implements Listener {

  private final CrateAnimator animator;

  public CrateGuiListener(CrateAnimator animator) {
    this.animator = animator;
  }

  @EventHandler(ignoreCancelled = true)
  public void onClick(InventoryClickEvent e) {
    if (!(e.getInventory().getHolder() instanceof CrateGuiHolder)) return;
    e.setCancelled(true);
  }

  @EventHandler
  public void onClose(InventoryCloseEvent e) {
    if (!(e.getInventory().getHolder() instanceof CrateGuiHolder h)) return;
    animator.markClosed(h.owner());
  }
}
