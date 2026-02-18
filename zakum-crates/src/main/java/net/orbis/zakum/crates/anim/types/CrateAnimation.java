package net.orbis.zakum.crates.anim.types;

import net.orbis.zakum.crates.model.CrateDef;
import net.orbis.zakum.crates.model.RewardDef;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Base interface for crate opening animations.
 * 
 * Each animation type controls:
 * - Timing and progression
 * - Visual effects (particles, sounds, GUI updates)
 * - Reward revelation
 */
public interface CrateAnimation {
    
    /**
     * Initialize the animation with context.
     * 
     * @param player The player opening the crate
     * @param crate The crate definition
     * @param reward The final reward to reveal
     */
    void initialize(Player player, CrateDef crate, RewardDef reward);
    
    /**
     * Tick the animation forward one step.
     * Called periodically by the animation system.
     * 
     * @param tickNumber The current tick number (0-based)
     * @return true if animation should continue, false if complete
     */
    boolean tick(int tickNumber);
    
    /**
     * Update the GUI inventory to reflect current animation state.
     * 
     * @param inventory The inventory to update
     * @param tickNumber The current tick number
     */
    void updateGui(Inventory inventory, int tickNumber);
    
    /**
     * Clean up animation resources.
     * Called when animation ends or is cancelled.
     */
    void cleanup();
    
    /**
     * Get total duration of animation in ticks.
     * 
     * @return Duration in ticks (20 ticks = 1 second)
     */
    int getDurationTicks();
    
    /**
     * Check if animation is complete.
     * 
     * @return true if animation has finished
     */
    boolean isComplete();
}
