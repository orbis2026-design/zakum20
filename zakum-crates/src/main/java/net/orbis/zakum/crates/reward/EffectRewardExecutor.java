package net.orbis.zakum.crates.reward;

import net.orbis.zakum.crates.model.RewardDef;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Objects;

/**
 * Executes potion effect rewards.
 * 
 * Applies potion effects to the player with configurable duration and amplifier.
 */
public class EffectRewardExecutor implements RewardExecutor {
    
    @Override
    public boolean execute(Player player, RewardDef reward) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(reward, "reward");
        
        List<String> effects = reward.effects();
        if (effects == null || effects.isEmpty()) {
            return false;
        }
        
        boolean allApplied = true;
        
        for (String effectString : effects) {
            if (effectString == null || effectString.isBlank()) {
                continue;
            }
            
            // Parse effect string: "EFFECT_TYPE:duration:amplifier"
            // Example: "SPEED:600:1" = Speed II for 30 seconds (600 ticks)
            String[] parts = effectString.split(":");
            if (parts.length < 1) {
                allApplied = false;
                continue;
            }
            
            try {
                PotionEffectType effectType = PotionEffectType.getByName(parts[0].toUpperCase());
                if (effectType == null) {
                    allApplied = false;
                    continue;
                }
                
                int duration = parts.length > 1 ? Integer.parseInt(parts[1]) : 600; // Default 30s
                int amplifier = parts.length > 2 ? Integer.parseInt(parts[2]) : 0; // Default level 1
                
                // Validate values
                duration = Math.max(1, Math.min(duration, 72000)); // 1 tick to 1 hour
                amplifier = Math.max(0, Math.min(amplifier, 255)); // 0 to 255
                
                PotionEffect effect = new PotionEffect(
                    effectType,
                    duration,
                    amplifier,
                    false, // ambient
                    true,  // particles
                    true   // icon
                );
                
                player.addPotionEffect(effect);
                
            } catch (NumberFormatException e) {
                allApplied = false;
            }
        }
        
        return allApplied;
    }
    
    @Override
    public String getType() {
        return "effect";
    }
    
    @Override
    public boolean canHandle(RewardDef reward) {
        return reward != null && reward.effects() != null && !reward.effects().isEmpty();
    }
}
