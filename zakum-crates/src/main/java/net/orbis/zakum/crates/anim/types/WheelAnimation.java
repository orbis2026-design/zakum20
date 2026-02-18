package net.orbis.zakum.crates.anim.types;

import net.orbis.zakum.api.util.WeightedTable;
import net.orbis.zakum.crates.model.CrateDef;
import net.orbis.zakum.crates.model.RewardDef;
import net.orbis.zakum.crates.util.ItemBuilder;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Wheel animation with spinning segments that slow to a stop.
 * 
 * Visual: A circular wheel divided into segments, each containing a reward.
 * The wheel spins fast initially, then slows down until landing on the final reward.
 * 
 * GUI Layout:
 * - 9 segments arranged in a circle pattern in the inventory
 * - Pointer indicates current selection
 * - Smooth rotation with deceleration physics
 * 
 * Duration: 80 ticks (~4 seconds)
 */
public class WheelAnimation implements CrateAnimation {
    
    private static final int TOTAL_DURATION = 80; // 4 seconds
    private static final int SEGMENT_COUNT = 8;
    
    // Circular layout positions in 27-slot inventory
    private static final int[] WHEEL_SLOTS = {1, 2, 5, 14, 23, 22, 19, 10};
    private static final int POINTER_SLOT = 13; // Center
    
    private Player player;
    private CrateDef crate;
    private RewardDef finalReward;
    private final Random random = new Random();
    
    private int currentTick = 0;
    private boolean complete = false;
    
    // Wheel state
    private final ItemStack[] segments = new ItemStack[SEGMENT_COUNT];
    private int currentSegment = 0;
    private int ticksSinceLastRotation = 0;
    private int rotationDelay = 2; // Ticks between rotations (increases for deceleration)
    
    @Override
    public void initialize(Player player, CrateDef crate, RewardDef reward) {
        this.player = Objects.requireNonNull(player, "player");
        this.crate = Objects.requireNonNull(crate, "crate");
        this.finalReward = Objects.requireNonNull(reward, "finalReward");
        
        // Initialize segments with random rewards
        WeightedTable<RewardDef> rewards = crate.rewards();
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            segments[i] = getRewardIcon(rewards.pick(random));
        }
        
        // Place final reward at calculated stopping position
        int stoppingSegment = calculateStoppingSegment();
        segments[stoppingSegment] = getRewardIcon(finalReward);
        
        this.currentTick = 0;
        this.complete = false;
        this.currentSegment = 0;
        this.ticksSinceLastRotation = 0;
        this.rotationDelay = 2;
    }
    
    @Override
    public boolean tick(int tickNumber) {
        if (complete) return false;
        
        currentTick = tickNumber;
        
        if (player == null || !player.isOnline()) {
            complete = true;
            return false;
        }
        
        ticksSinceLastRotation++;
        
        // Rotate wheel when delay is met
        if (ticksSinceLastRotation >= rotationDelay) {
            ticksSinceLastRotation = 0;
            currentSegment = (currentSegment + 1) % SEGMENT_COUNT;
            
            // Play click sound
            float pitch = 1.0f + (0.5f * ((float) currentTick / TOTAL_DURATION));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, pitch);
            
            // Increase delay for deceleration
            double progress = (double) currentTick / TOTAL_DURATION;
            if (progress > 0.3) {
                rotationDelay = Math.min(20, (int) (2 + progress * 15));
            }
        }
        
        // Completion
        if (currentTick >= TOTAL_DURATION) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.1f);
            spawnCompletionEffect();
            complete = true;
            return false;
        }
        
        return true;
    }
    
    @Override
    public void updateGui(Inventory inventory, int tickNumber) {
        if (inventory == null) return;
        
        // Clear inventory
        inventory.clear();
        
        // Fill with background panes
        ItemStack pane = ItemBuilder.pane();
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, pane);
        }
        
        // Place wheel segments in circular pattern
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            int slotIndex = WHEEL_SLOTS[i];
            ItemStack segment = segments[i];
            
            // Highlight current segment
            if (i == currentSegment) {
                // Add glow effect to current segment
                inventory.setItem(slotIndex, addGlow(segment));
            } else {
                inventory.setItem(slotIndex, segment);
            }
        }
        
        // Place pointer at center
        inventory.setItem(POINTER_SLOT, ItemBuilder.pointer());
        
        // Add corner decorations
        ItemStack decoration = new ItemStack(Material.GOLD_BLOCK);
        inventory.setItem(0, decoration);
        inventory.setItem(8, decoration);
        inventory.setItem(18, decoration);
        inventory.setItem(26, decoration);
    }
    
    @Override
    public void cleanup() {
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
     * Calculate which segment should contain the final reward.
     */
    private int calculateStoppingSegment() {
        // Simulate rotation to find stopping position
        int simSegment = 0;
        int simTick = 0;
        int simTicksSinceRotation = 0;
        int simDelay = 2;
        
        while (simTick < TOTAL_DURATION) {
            simTicksSinceRotation++;
            
            if (simTicksSinceRotation >= simDelay) {
                simTicksSinceRotation = 0;
                simSegment = (simSegment + 1) % SEGMENT_COUNT;
                
                double progress = (double) simTick / TOTAL_DURATION;
                if (progress > 0.3) {
                    simDelay = Math.min(20, (int) (2 + progress * 15));
                }
            }
            
            simTick++;
        }
        
        return simSegment;
    }
    
    /**
     * Add glow effect to item (visual highlight).
     */
    private ItemStack addGlow(ItemStack item) {
        if (item == null) return null;
        
        // Clone and add enchantment glow
        ItemStack glowItem = item.clone();
        glowItem.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.LUCK, 1);
        
        return glowItem;
    }
    
    /**
     * Spawn completion particle effect.
     */
    private void spawnCompletionEffect() {
        Location location = player.getLocation();
        World world = location.getWorld();
        if (world == null) return;
        
        Location center = location.clone().add(0, 1.5, 0);
        
        // Circular burst
        for (int i = 0; i < 16; i++) {
            double angle = (i * Math.PI * 2) / 16;
            double x = center.getX() + Math.cos(angle) * 0.8;
            double z = center.getZ() + Math.sin(angle) * 0.8;
            
            Location particleLocation = new Location(world, x, center.getY(), z);
            world.spawnParticle(Particle.GLOW, particleLocation, 3, 0.1, 0.1, 0.1, 0.05);
        }
        
        // Center flash
        world.spawnParticle(Particle.FLASH, center, 3, 0.3, 0.3, 0.3, 0);
    }
    
    /**
     * Get display icon for reward.
     */
    private ItemStack getRewardIcon(RewardDef reward) {
        if (reward != null && reward.items() != null && !reward.items().isEmpty()) {
            ItemStack item = reward.items().get(0);
            if (item != null && !item.getType().isAir()) {
                return item.clone();
            }
        }
        return new ItemStack(Material.CHEST);
    }
}
