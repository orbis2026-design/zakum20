package net.orbis.zakum.crates.anim.types;

import net.orbis.zakum.crates.model.CrateDef;
import net.orbis.zakum.crates.model.RewardDef;
import net.orbis.zakum.crates.util.ItemBuilder;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 * Explosion-style animation with firework particle bursts.
 * 
 * Visual: Multiple firework explosions at the crate location,
 * building up to a final dramatic reveal of the reward.
 * 
 * Phases:
 * - Phase 1: Small bursts (0-20 ticks)
 * - Phase 2: Medium bursts (20-40 ticks)
 * - Phase 3: Large explosion (40-60 ticks)
 * - Phase 4: Reward reveal (60-80 ticks)
 * 
 * Timing: ~4 seconds (80 ticks)
 */
public class ExplosionAnimation implements CrateAnimation {
    
    private static final int TOTAL_DURATION = 80; // 4 seconds
    private static final int PHASE_1_END = 20;
    private static final int PHASE_2_END = 40;
    private static final int PHASE_3_END = 60;
    
    private Player player;
    private CrateDef crate;
    private RewardDef finalReward;
    private Location crateLocation;
    
    private int currentTick = 0;
    private boolean complete = false;
    private boolean rewardRevealed = false;
    
    @Override
    public void initialize(Player player, CrateDef crate, RewardDef reward) {
        this.player = Objects.requireNonNull(player, "player");
        this.crate = Objects.requireNonNull(crate, "crate");
        this.finalReward = Objects.requireNonNull(reward, "finalReward");
        this.crateLocation = player.getLocation().clone();
        
        this.currentTick = 0;
        this.complete = false;
        this.rewardRevealed = false;
    }
    
    @Override
    public boolean tick(int tickNumber) {
        if (complete) return false;
        
        currentTick = tickNumber;
        
        if (player == null || !player.isOnline()) {
            complete = true;
            return false;
        }
        
        World world = crateLocation.getWorld();
        if (world == null) {
            complete = true;
            return false;
        }
        
        // Phase-based particle effects
        if (currentTick < PHASE_1_END) {
            // Small bursts every 5 ticks
            if (currentTick % 5 == 0) {
                spawnSmallBurst(world);
                player.playSound(crateLocation, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.5f, 1.5f);
            }
        } else if (currentTick < PHASE_2_END) {
            // Medium bursts every 4 ticks
            if (currentTick % 4 == 0) {
                spawnMediumBurst(world);
                player.playSound(crateLocation, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.7f, 1.2f);
            }
        } else if (currentTick < PHASE_3_END) {
            // Large bursts every 3 ticks
            if (currentTick % 3 == 0) {
                spawnLargeBurst(world);
                player.playSound(crateLocation, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 0.9f, 1.0f);
            }
        } else if (currentTick == PHASE_3_END) {
            // Final explosion
            spawnFinalExplosion(world);
            player.playSound(crateLocation, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);
            player.playSound(crateLocation, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            rewardRevealed = true;
        }
        
        // Complete after duration
        if (currentTick >= TOTAL_DURATION) {
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
        
        // Fill with panes initially
        ItemStack pane = ItemBuilder.pane();
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, pane);
        }
        
        // Show reward after phase 3
        if (rewardRevealed) {
            ItemStack rewardIcon = getRewardIcon(finalReward);
            inventory.setItem(13, rewardIcon); // Center slot
            
            // Add sparkle effect around reward
            ItemStack sparkle = new ItemStack(Material.FIREWORK_STAR);
            inventory.setItem(12, sparkle);
            inventory.setItem(14, sparkle);
            inventory.setItem(4, sparkle);
            inventory.setItem(22, sparkle);
        } else {
            // Show mystery item before reveal
            ItemStack mystery = new ItemStack(Material.CHEST);
            inventory.setItem(13, mystery);
        }
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
     * Spawn small particle burst (Phase 1).
     */
    private void spawnSmallBurst(World world) {
        world.spawnParticle(
            Particle.FIREWORK,
            crateLocation.clone().add(0, 1, 0),
            10, // count
            0.3, 0.3, 0.3, // spread
            0.05 // speed
        );
    }
    
    /**
     * Spawn medium particle burst (Phase 2).
     */
    private void spawnMediumBurst(World world) {
        world.spawnParticle(
            Particle.FIREWORK,
            crateLocation.clone().add(0, 1.5, 0),
            20, // count
            0.5, 0.5, 0.5, // spread
            0.1 // speed
        );
        
        // Add some color
        world.spawnParticle(
            Particle.GLOW,
            crateLocation.clone().add(0, 1.5, 0),
            15,
            0.5, 0.5, 0.5,
            0.05
        );
    }
    
    /**
     * Spawn large particle burst (Phase 3).
     */
    private void spawnLargeBurst(World world) {
        world.spawnParticle(
            Particle.FIREWORK,
            crateLocation.clone().add(0, 2, 0),
            30, // count
            0.8, 0.8, 0.8, // spread
            0.15 // speed
        );
        
        world.spawnParticle(
            Particle.GLOW,
            crateLocation.clone().add(0, 2, 0),
            25,
            0.8, 0.8, 0.8,
            0.1
        );
        
        world.spawnParticle(
            Particle.FLASH,
            crateLocation.clone().add(0, 2, 0),
            5,
            0.5, 0.5, 0.5,
            0
        );
    }
    
    /**
     * Spawn final dramatic explosion.
     */
    private void spawnFinalExplosion(World world) {
        // Multiple particle types for dramatic effect
        world.spawnParticle(
            Particle.EXPLOSION,
            crateLocation.clone().add(0, 1.5, 0),
            10,
            1.0, 1.0, 1.0,
            0
        );
        
        world.spawnParticle(
            Particle.FIREWORK,
            crateLocation.clone().add(0, 1.5, 0),
            50,
            1.2, 1.2, 1.2,
            0.2
        );
        
        world.spawnParticle(
            Particle.GLOW,
            crateLocation.clone().add(0, 1.5, 0),
            40,
            1.0, 1.0, 1.0,
            0.15
        );
        
        world.spawnParticle(
            Particle.FLASH,
            crateLocation.clone().add(0, 1.5, 0),
            10,
            0.8, 0.8, 0.8,
            0
        );
        
        world.spawnParticle(
            Particle.END_ROD,
            crateLocation.clone().add(0, 1.5, 0),
            30,
            1.0, 1.0, 1.0,
            0.1
        );
    }
    
    /**
     * Get display icon for reward.
     */
    private ItemStack getRewardIcon(RewardDef reward) {
        if (reward.items() != null && !reward.items().isEmpty()) {
            ItemStack item = reward.items().get(0);
            if (item != null && !item.getType().isAir()) {
                return item.clone();
            }
        }
        return new ItemStack(Material.CHEST);
    }
}
