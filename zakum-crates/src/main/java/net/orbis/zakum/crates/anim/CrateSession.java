package net.orbis.zakum.crates.anim;

import net.orbis.zakum.crates.anim.types.CrateAnimation;
import net.orbis.zakum.crates.gui.CrateGuiHolder;
import net.orbis.zakum.crates.model.CrateDef;
import net.orbis.zakum.crates.model.RewardDef;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

/**
 * Session representing an active crate opening.
 * 
 * Manages the animation lifecycle and tracks state during opening.
 */
final class CrateSession {

  final UUID opener;
  final CrateDef crate;
  final Location origin;
  final RewardDef finalReward;
  final CrateGuiHolder holder;
  final Inventory inv;
  final CrateAnimation animation;
  
  int currentTick = 0;
  boolean inventoryClosed = false;
  boolean cancelled = false;

  CrateSession(UUID opener, CrateDef crate, Location origin, RewardDef finalReward,
              CrateGuiHolder holder, Inventory inv, CrateAnimation animation) {
    this.opener = opener;
    this.crate = crate;
    this.origin = origin;
    this.finalReward = finalReward;
    this.holder = holder;
    this.inv = inv;
    this.animation = animation;
  }
  
  /**
   * Tick the animation forward.
   * 
   * @return true if should continue, false if complete
   */
  boolean tick() {
    if (cancelled || animation.isComplete()) {
      return false;
    }
    
    boolean shouldContinue = animation.tick(currentTick);
    
    if (!inventoryClosed) {
      animation.updateGui(inv, currentTick);
    }
    
    currentTick++;
    return shouldContinue;
  }
  
  /**
   * Cancel the session (player disconnected or closed inventory).
   */
  void cancel() {
    cancelled = true;
    animation.cleanup();
  }
  
  /**
   * Check if animation is complete.
   */
  boolean isComplete() {
    return animation.isComplete() || cancelled;
  }
}
