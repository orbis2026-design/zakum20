package net.orbis.zakum.crates.anim.types;

import net.orbis.zakum.api.util.WeightedTable;
import net.orbis.zakum.crates.model.CrateDef;
import net.orbis.zakum.crates.model.RewardDef;
import net.orbis.zakum.crates.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.Random;

/**
 * Roulette-style animation with spinning belt and deceleration.
 * 
 * Visual: A horizontal belt of items scrolls past with a pointer at center.
 * The belt starts fast and gradually slows down until stopping at the reward.
 * 
 * Physics:
 * - Initial velocity: Fast scroll (1 item per 2 ticks)
 * - Deceleration: Gradually slows down
 * - Final stop: Lands on the final reward
 * 
 * Timing:
 * - Total duration: ~5 seconds (100 ticks)
 * - Fast phase: 40 ticks
 * - Slow phase: 40 ticks
 * - Stop phase: 20 ticks
 */
public class RouletteAnimation implements CrateAnimation {
    
    private static final int TOTAL_DURATION = 100; // 5 seconds
    private static final int BELT_SIZE = 9;
    private static final int CENTER_SLOT = 4; // Middle of 9-slot belt
    
    private Player player;
    private CrateDef crate;
    private RewardDef finalReward;
    private final Random random = new Random();
    
    // Animation state
    private final ItemStack[] belt = new ItemStack[BELT_SIZE];
    private int currentTick = 0;
    private int nextShiftTick = 0;
    private boolean complete = false;
    
    // Physics simulation
    private double position = 0.0; // Current position in belt (can be fractional)
    private double velocity = 0.5; // Items per tick (starts fast)
    private final double initialVelocity = 0.5;
    private final double deceleration = 0.006; // Slow down rate
    
    @Override
    public void initialize(Player player, CrateDef crate, RewardDef reward) {
        this.player = Objects.requireNonNull(player, "player");
        this.crate = Objects.requireNonNull(crate, "crate");
        this.finalReward = Objects.requireNonNull(reward, "finalReward");
        
        // Initialize belt with random rewards
        WeightedTable<RewardDef> rewards = crate.rewards();
        for (int i = 0; i < BELT_SIZE; i++) {
            belt[i] = getRewardIcon(rewards.pick(random));
        }
        
        // Ensure final reward is at the right position to stop at center
        // Calculate where to place it based on deceleration physics
        int stoppingIndex = calculateStoppingIndex();
        belt[stoppingIndex % BELT_SIZE] = getRewardIcon(finalReward);
        
        this.velocity = initialVelocity;
        this.position = 0.0;
        this.currentTick = 0;
        this.nextShiftTick = 0;
        this.complete = false;
    }
    
    @Override
    public boolean tick(int tickNumber) {
        if (complete) return false;
        
        currentTick = tickNumber;
        
        // Update physics
        if (velocity > 0.001) {
            position += velocity;
            velocity = Math.max(0, velocity - deceleration);
            
            // Check if we need to shift the belt
            if (position >= 1.0) {
                shiftBelt();
                position -= 1.0;
                
                // Play scroll sound
                if (player != null && player.isOnline()) {
                    float pitch = (float)(1.0 + (1.0 - velocity / initialVelocity));
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, pitch);
                }
            }
        } else {
            // Animation complete - stopped moving
            if (!complete) {
                complete = true;
                if (player != null && player.isOnline()) {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.1f);
                }
            }
            return false;
        }
        
        // Continue if not yet at end of duration
        return currentTick < TOTAL_DURATION && !complete;
    }
    
    @Override
    public void updateGui(Inventory inventory, int tickNumber) {
        if (inventory == null) return;
        
        // Render the belt in slots 9-17 (middle row)
        for (int i = 0; i < BELT_SIZE; i++) {
            inventory.setItem(9 + i, belt[i]);
        }
        
        // Keep the pointer at slot 4 (center top)
        inventory.setItem(4, ItemBuilder.pointer());
        
        // Fill rest with panes
        ItemStack pane = ItemBuilder.pane();
        for (int i = 0; i < 9; i++) {
            if (i != 4) inventory.setItem(i, pane);
        }
        for (int i = 18; i < 27; i++) {
            inventory.setItem(i, pane);
        }
    }
    
    @Override
    public void cleanup() {
        // No resources to clean up
        complete = true;
    }
    
    @Override
    public int getDurationTicks() {
        return TOTAL_DURATION;
    }
    
    @Override
    public boolean isComplete() {
        return complete;
    }
    
    /**
     * Shift the belt one position to the left (scrolling effect).
     */
    private void shiftBelt() {
        // Shift all items left
        System.arraycopy(belt, 1, belt, 0, BELT_SIZE - 1);
        
        // Add new random item at the end
        WeightedTable<RewardDef> rewards = crate.rewards();
        belt[BELT_SIZE - 1] = getRewardIcon(rewards.pick(random));
    }
    
    /**
     * Calculate which belt index should contain the final reward to stop at center.
     */
    private int calculateStoppingIndex() {
        // Simulate the physics to find where we'll stop
        double simVelocity = initialVelocity;
        double simPosition = 0.0;
        int shifts = 0;
        
        while (simVelocity > 0.001) {
            simPosition += simVelocity;
            simVelocity = Math.max(0, simVelocity - deceleration);
            
            if (simPosition >= 1.0) {
                shifts++;
                simPosition -= 1.0;
            }
        }
        
        // We want the final reward to be at center (slot 4) when we stop
        // So place it at (CENTER_SLOT + shifts) initially
        return CENTER_SLOT + shifts;
    }
    
    /**
     * Get display icon for a reward.
     */
    private ItemStack getRewardIcon(RewardDef reward) {
        if (reward == null) {
            return new ItemStack(Material.CHEST);
        }
        
        if (reward.items() != null && !reward.items().isEmpty()) {
            ItemStack item = reward.items().get(0);
            if (item != null && !item.getType().isAir()) {
                return item.clone();
            }
        }
        
        // Fallback to chest
        return new ItemStack(Material.CHEST);
    }
}
