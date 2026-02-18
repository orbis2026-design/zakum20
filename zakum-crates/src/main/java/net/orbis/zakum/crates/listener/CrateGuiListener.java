package net.orbis.zakum.crates.listener;

import net.orbis.zakum.crates.anim.CrateAnimatorV2;
import net.orbis.zakum.crates.gui.CrateGuiHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles GUI interactions for crate opening animations.
 * 
 * Prevents item manipulation and tracks inventory closure.
 */
public final class CrateGuiListener implements Listener {

  private final CrateAnimatorV2 animator;

  public CrateGuiListener(CrateAnimatorV2 animator) {
    this.animator = animator;
  }

  /**
   * Prevent clicking in crate GUIs.
   * All crate GUIs are view-only during animation.
   */
  @EventHandler(ignoreCancelled = true)
  public void onClick(InventoryClickEvent e) {
    if (!(e.getInventory().getHolder() instanceof CrateGuiHolder)) return;
    
    // Cancel all clicks in crate GUIs
    e.setCancelled(true);
    
    // Optionally show item info on click
    if (e.getCurrentItem() != null && !e.getCurrentItem().getType().isAir()) {
      Player player = (Player) e.getWhoClicked();
      ItemStack clicked = e.getCurrentItem();
      
      // Show item name as action bar message
      if (clicked.hasItemMeta() && clicked.getItemMeta().hasDisplayName()) {
        player.sendActionBar(clicked.getItemMeta().displayName());
      }
    }
  }
  
  /**
   * Prevent dragging items in crate GUIs.
   */
  @EventHandler(ignoreCancelled = true)
  public void onDrag(InventoryDragEvent e) {
    if (!(e.getInventory().getHolder() instanceof CrateGuiHolder)) return;
    
    // Cancel all drags in crate GUIs
    e.setCancelled(true);
  }

  /**
   * Mark session as closed when player closes the inventory.
   * Animation will complete in background if closed early.
   */
  @EventHandler
  public void onClose(InventoryCloseEvent e) {
    if (!(e.getInventory().getHolder() instanceof CrateGuiHolder h)) return;
    
    animator.markClosed(h.owner());
    
    // Notify player that animation is completing
    if (e.getPlayer() instanceof Player player) {
      player.sendMessage("§7Animation completing in background...");
    }
  }
}
