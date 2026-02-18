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
 * Instant animation with immediate reward reveal.
 * 
 * Visual: Quick flash effect and immediate display of reward.
 * No delay, no spinning - perfect for players who want speed.
 * 
 * Duration: 10 ticks (0.5 seconds) - Just enough for visual feedback
 */
public class InstantAnimation implements CrateAnimation {
    
    private static final int TOTAL_DURATION = 10; // 0.5 seconds
    
    private Player player;
    private CrateDef crate;
    private RewardDef finalReward;
    private Location crateLocation;
    
    private int currentTick = 0;
    private boolean complete = false;
    private boolean effectPlayed = false;
    
    @Override
    public void initialize(Player player, CrateDef crate, RewardDef reward) {
        this.player = Objects.requireNonNull(player, "player");
        this.crate = Objects.requireNonNull(crate, "crate");
        this.finalReward = Objects.requireNonNull(reward, "finalReward");
        this.crateLocation = player.getLocation().clone();
        
        this.currentTick = 0;
        this.complete = false;
        this.effectPlayed = false;
    }
    
    @Override
    public boolean tick(int tickNumber) {
        if (complete) return false;
        
        currentTick = tickNumber;
        
        if (player == null || !player.isOnline()) {
            complete = true;
            return false;
        }
        
        // Play instant reveal effect on first tick
        if (!effectPlayed) {
            playRevealEffect();
            effectPlayed = true;
        }
        
        // Complete after short duration
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
        
        // Fill with panes
        ItemStack pane = ItemBuilder.pane();
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, pane);
        }
        
        // Immediately show reward in center
        ItemStack rewardIcon = getRewardIcon(finalReward);
        inventory.setItem(13, rewardIcon);
        
        // Add decorative border
        ItemStack glow = new ItemStack(Material.GLOWSTONE);
        inventory.setItem(12, glow);
        inventory.setItem(14, glow);
        inventory.setItem(4, glow);
        inventory.setItem(22, glow);
        
        // Corner accents
        ItemStack accent = new ItemStack(Material.GOLD_BLOCK);
        inventory.setItem(0, accent);
        inventory.setItem(8, accent);
        inventory.setItem(18, accent);
        inventory.setItem(26, accent);
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
     * Play instant reveal effect.
     */
    private void playRevealEffect() {
        World world = crateLocation.getWorld();
        if (world == null) return;
        
        Location center = crateLocation.clone().add(0, 1.5, 0);
        
        // Flash effect
        world.spawnParticle(
            Particle.FLASH,
            center,
            10,
            0.5, 0.5, 0.5,
            0
        );
        
        // Burst of particles
        world.spawnParticle(
            Particle.END_ROD,
            center,
            20,
            0.8, 0.8, 0.8,
            0.1
        );
        
        world.spawnParticle(
            Particle.GLOW,
            center,
            30,
            0.6, 0.6, 0.6,
            0.05
        );
        
        // Sound effects
        player.playSound(crateLocation, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
        player.playSound(crateLocation, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
        player.playSound(crateLocation, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 1.8f);
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
