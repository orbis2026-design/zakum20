package net.orbis.zakum.crates.anim.types;

import net.orbis.zakum.crates.model.CrateDef;
import net.orbis.zakum.crates.model.RewardDef;
import net.orbis.zakum.crates.util.ItemBuilder;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Objects;

/**
 * Spiral animation with particles rotating around the crate.
 * 
 * Visual: Particles spiral upward around the crate location,
 * creating a helix pattern that speeds up before revealing the reward.
 * 
 * Physics:
 * - Spiral radius: 1.5 blocks
 * - Vertical speed: 0.1 blocks/tick
 * - Rotation speed: Increases over time (10° → 30° per tick)
 * - Duration: 60 ticks (~3 seconds)
 */
public class SpiralAnimation implements CrateAnimation {
    
    private static final int TOTAL_DURATION = 60; // 3 seconds
    private static final double SPIRAL_RADIUS = 1.5;
    private static final double VERTICAL_SPEED = 0.1;
    private static final int PARTICLES_PER_TICK = 3;
    
    private Player player;
    private CrateDef crate;
    private RewardDef finalReward;
    private Location crateLocation;
    
    private int currentTick = 0;
    private boolean complete = false;
    private double currentAngle = 0.0;
    private double currentHeight = 0.0;
    
    @Override
    public void initialize(Player player, CrateDef crate, RewardDef reward) {
        this.player = Objects.requireNonNull(player, "player");
        this.crate = Objects.requireNonNull(crate, "crate");
        this.finalReward = Objects.requireNonNull(reward, "finalReward");
        this.crateLocation = player.getLocation().clone();
        
        this.currentTick = 0;
        this.complete = false;
        this.currentAngle = 0.0;
        this.currentHeight = 0.0;
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
        
        // Calculate rotation speed (accelerates over time)
        double progress = (double) currentTick / TOTAL_DURATION;
        double rotationSpeed = Math.toRadians(10 + progress * 20); // 10° to 30° per tick
        
        // Spawn spiral particles
        for (int i = 0; i < PARTICLES_PER_TICK; i++) {
            double angle = currentAngle + (i * Math.PI * 2 / PARTICLES_PER_TICK);
            spawnSpiralParticle(world, angle, currentHeight);
        }
        
        // Update position
        currentAngle += rotationSpeed;
        currentHeight += VERTICAL_SPEED;
        
        // Reset height if too high
        if (currentHeight > 3.0) {
            currentHeight = 0.0;
        }
        
        // Play sound effects
        if (currentTick % 5 == 0) {
            float pitch = (float)(1.0 + progress);
            player.playSound(crateLocation, Sound.BLOCK_NOTE_BLOCK_HARP, 0.5f, pitch);
        }
        
        // Completion
        if (currentTick >= TOTAL_DURATION) {
            spawnCompletionEffect(world);
            player.playSound(crateLocation, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.1f);
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
        
        // Fill with panes
        ItemStack pane = ItemBuilder.pane();
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, pane);
        }
        
        // Show animated spiral pattern in GUI
        double progress = (double) currentTick / TOTAL_DURATION;
        
        if (progress < 0.33) {
            // Phase 1: Corner glow
            inventory.setItem(0, new ItemStack(Material.GLOW_LICHEN));
            inventory.setItem(8, new ItemStack(Material.GLOW_LICHEN));
            inventory.setItem(18, new ItemStack(Material.GLOW_LICHEN));
            inventory.setItem(26, new ItemStack(Material.GLOW_LICHEN));
        } else if (progress < 0.66) {
            // Phase 2: Edge glow
            inventory.setItem(1, new ItemStack(Material.GLOW_LICHEN));
            inventory.setItem(7, new ItemStack(Material.GLOW_LICHEN));
            inventory.setItem(19, new ItemStack(Material.GLOW_LICHEN));
            inventory.setItem(25, new ItemStack(Material.GLOW_LICHEN));
        } else {
            // Phase 3: Reveal reward
            ItemStack rewardIcon = getRewardIcon(finalReward);
            inventory.setItem(13, rewardIcon);
            
            // Glow around reward
            ItemStack glow = new ItemStack(Material.GLOW_BERRIES);
            inventory.setItem(12, glow);
            inventory.setItem(14, glow);
            inventory.setItem(4, glow);
            inventory.setItem(22, glow);
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
     * Spawn a single particle in the spiral pattern.
     */
    private void spawnSpiralParticle(World world, double angle, double height) {
        // Calculate position on spiral
        double x = crateLocation.getX() + Math.cos(angle) * SPIRAL_RADIUS;
        double y = crateLocation.getY() + height;
        double z = crateLocation.getZ() + Math.sin(angle) * SPIRAL_RADIUS;
        
        Location particleLocation = new Location(world, x, y, z);
        
        // Spawn particles
        world.spawnParticle(
            Particle.GLOW,
            particleLocation,
            1,
            0.05, 0.05, 0.05,
            0
        );
        
        world.spawnParticle(
            Particle.END_ROD,
            particleLocation,
            1,
            0.02, 0.02, 0.02,
            0.01
        );
    }
    
    /**
     * Spawn completion effect when spiral finishes.
     */
    private void spawnCompletionEffect(World world) {
        Location center = crateLocation.clone().add(0, 1.5, 0);
        
        // Ring of particles
        for (int i = 0; i < 16; i++) {
            double angle = (i * Math.PI * 2) / 16;
            double x = center.getX() + Math.cos(angle) * 1.0;
            double z = center.getZ() + Math.sin(angle) * 1.0;
            
            Location particleLocation = new Location(world, x, center.getY(), z);
            
            world.spawnParticle(
                Particle.GLOW,
                particleLocation,
                5,
                0.1, 0.1, 0.1,
                0.05
            );
        }
        
        // Central burst
        world.spawnParticle(
            Particle.FLASH,
            center,
            5,
            0.5, 0.5, 0.5,
            0
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
