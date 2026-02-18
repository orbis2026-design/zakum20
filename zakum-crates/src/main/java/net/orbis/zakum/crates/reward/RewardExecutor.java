package net.orbis.zakum.crates.reward;

import net.orbis.zakum.crates.model.RewardDef;
import org.bukkit.entity.Player;

/**
 * Base interface for reward execution.
 * 
 * Executors handle granting rewards to players when they open crates.
 */
public interface RewardExecutor {
    
    /**
     * Execute the reward for a player.
     * 
     * @param player Player receiving the reward
     * @param reward Reward definition
     * @return true if successfully executed, false otherwise
     */
    boolean execute(Player player, RewardDef reward);
    
    /**
     * Get the type identifier for this executor.
     * 
     * @return Type name (e.g., "command", "item", "money")
     */
    String getType();
    
    /**
     * Check if this executor can handle the given reward.
     * 
     * @param reward Reward to check
     * @return true if this executor can handle it
     */
    boolean canHandle(RewardDef reward);
}
