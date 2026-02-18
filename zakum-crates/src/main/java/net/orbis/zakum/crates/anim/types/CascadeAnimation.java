package net.orbis.zakum.crates.anim.types;

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
 * Cascade animation with falling particle waterfall effect.
 * 
 * Visual: Particles cascade down from above the crate like a waterfall,
 * building intensity until the reward is revealed at the bottom.
 * 
 * Phases:
 * - Phase 1: Light drizzle (0-15 ticks)
 * - Phase 2: Moderate flow (15-30 ticks)
 * - Phase 3: Heavy cascade (30-45 ticks)
 * - Phase 4: Reward pool formation (45-60 ticks)
 * 
 * Duration: 60 ticks (~3 seconds)
 */
public class CascadeAnimation implements CrateAnimation {
    
    private static final int TOTAL_DURATION = 60; // 3 seconds
    private static final int PHASE_1_END = 15;
    private static final int PHASE_2_END = 30;
    private static final int PHASE_3_END = 45;
    
    private static final double CASCADE_HEIGHT = 3.0;
    private static final double CASCADE_RADIUS = 0.5;
    
    private Player player;
    private CrateDef crate;
    private RewardDef finalReward;
    private Location crateLocation;
    private final Random random = new Random();
    
    private int currentTick = 0;
    private boolean complete = false;
    private final List<FallingParticle> particles = new ArrayList<>();
    
    @Override
    public void initialize(Player player, CrateDef crate, RewardDef reward) {
        this.player = Objects.requireNonNull(player, "player");
        this.crate = Objects.requireNonNull(crate, "crate");
        this.finalReward = Objects.requireNonNull(reward, "finalReward");
        this.crateLocation = player.getLocation().clone();
        
        this.currentTick = 0;
        this.complete = false;
        this.particles.clear();
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
        
        // Spawn new particles based on phase
        int spawnCount = getSpawnCount();
        for (int i = 0; i < spawnCount; i++) {
            spawnNewParticle();
        }
        
        // Update and render existing particles
        updateParticles(world);
        
        // Play sound effects
        if (currentTick % 10 == 0) {
            player.playSound(crateLocation, Sound.BLOCK_WATER_AMBIENT, 0.6f, 1.5f);
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
        
        // Animated cascade effect in GUI
        double progress = (double) currentTick / TOTAL_DURATION;
        
        // Top row: Source of cascade
        if (currentTick % 4 < 2) {
            inventory.setItem(4, new ItemStack(Material.WATER_BUCKET));
        }
        
        // Middle row: Falling particles
        if (progress > 0.25) {
            int column = (currentTick / 3) % 9;
            inventory.setItem(9 + column, new ItemStack(Material.LIGHT_BLUE_DYE));
        }
        
        // Bottom row: Pool formation
        if (progress > 0.5) {
            for (int i = 18; i < 27; i++) {
                if ((i + currentTick / 2) % 3 == 0) {
                    inventory.setItem(i, new ItemStack(Material.CYAN_DYE));
                }
            }
        }
        
        // Final reward reveal
        if (progress > 0.75) {
            ItemStack rewardIcon = getRewardIcon(finalReward);
            inventory.setItem(13, rewardIcon);
        }
    }
    
    @Override
    public void cleanup() {
        particles.clear();
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
     * Get number of particles to spawn this tick based on phase.
     */
    private int getSpawnCount() {
        if (currentTick < PHASE_1_END) {
            return 2; // Light drizzle
        } else if (currentTick < PHASE_2_END) {
            return 4; // Moderate flow
        } else if (currentTick < PHASE_3_END) {
            return 6; // Heavy cascade
        } else {
            return 3; // Tapering off
        }
    }
    
    /**
     * Spawn a new falling particle at the top.
     */
    private void spawnNewParticle() {
        double offsetX = (random.nextDouble() - 0.5) * CASCADE_RADIUS;
        double offsetZ = (random.nextDouble() - 0.5) * CASCADE_RADIUS;
        
        Location startLocation = crateLocation.clone()
            .add(offsetX, CASCADE_HEIGHT, offsetZ);
        
        particles.add(new FallingParticle(startLocation, 0.1 + random.nextDouble() * 0.05));
    }
    
    /**
     * Update and render all active particles.
     */
    private void updateParticles(World world) {
        particles.removeIf(particle -> {
            // Update position
            particle.location.add(0, -particle.fallSpeed, 0);
            
            // Check if reached ground
            if (particle.location.getY() <= crateLocation.getY()) {
                spawnSplashEffect(world, particle.location);
                return true; // Remove particle
            }
            
            // Render particle
            world.spawnParticle(
                Particle.WATER_DROP,
                particle.location,
                1,
                0, 0, 0,
                0
            );
            
            world.spawnParticle(
                Particle.GLOW,
                particle.location,
                1,
                0.05, 0.05, 0.05,
                0
            );
            
            return false; // Keep particle
        });
    }
    
    /**
     * Spawn splash effect when particle hits ground.
     */
    private void spawnSplashEffect(World world, Location location) {
        world.spawnParticle(
            Particle.WATER_SPLASH,
            location,
            3,
            0.2, 0.1, 0.2,
            0.05
        );
    }
    
    /**
     * Spawn completion effect when cascade finishes.
     */
    private void spawnCompletionEffect(World world) {
        Location center = crateLocation.clone().add(0, 0.5, 0);
        
        // Pool of particles at ground level
        for (int i = 0; i < 30; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = random.nextDouble() * 1.5;
            double x = center.getX() + Math.cos(angle) * radius;
            double z = center.getZ() + Math.sin(angle) * radius;
            
            Location particleLocation = new Location(world, x, center.getY(), z);
            
            world.spawnParticle(
                Particle.GLOW,
                particleLocation,
                3,
                0.1, 0.1, 0.1,
                0.02
            );
        }
        
        // Central splash
        world.spawnParticle(
            Particle.WATER_SPLASH,
            center,
            20,
            0.5, 0.3, 0.5,
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
    
    /**
     * Internal class representing a falling particle.
     */
    private static class FallingParticle {
        Location location;
        double fallSpeed;
        
        FallingParticle(Location location, double fallSpeed) {
            this.location = location;
            this.fallSpeed = fallSpeed;
        }
    }
}
