package net.orbis.zakum.crates.reward;

import net.orbis.zakum.crates.model.CrateDef;
import net.orbis.zakum.crates.model.RewardDef;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Complete reward system manager.
 * 
 * Coordinates reward execution, history tracking, notifications, and probability.
 */
public class RewardSystemManager {
    
    private final CompositeRewardExecutor executor;
    private final RewardProbabilityEngine probabilityEngine;
    private final RewardHistoryTracker historyTracker;
    private final RewardNotifier notifier;
    
    public RewardSystemManager() {
        this.executor = new CompositeRewardExecutor();
        this.probabilityEngine = new RewardProbabilityEngine();
        this.historyTracker = new RewardHistoryTracker();
        this.notifier = new RewardNotifier();
        
        // Register all executor types
        registerDefaultExecutors();
    }
    
    /**
     * Register default reward executors.
     */
    private void registerDefaultExecutors() {
        executor.register(new ItemRewardExecutor());
        executor.register(new CommandRewardExecutor());
        executor.register(new EffectRewardExecutor());
        executor.register(new MoneyRewardExecutor());
        executor.register(new PermissionRewardExecutor());
    }
    
    /**
     * Select and grant a reward to a player.
     * 
     * @param player Player to receive reward
     * @param crate Crate being opened
     * @return The reward that was granted
     */
    public RewardDef selectAndGrantReward(Player player, CrateDef crate) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(crate, "crate");
        
        // Select reward based on probability
        List<RewardDef> rewards = new ArrayList<>();
        crate.rewards().forEach(rewards::add);
        
        RewardDef selectedReward = probabilityEngine.selectReward(rewards);
        
        if (selectedReward == null) {
            player.sendMessage("§cNo reward available!");
            return null;
        }
        
        // Grant the reward
        grantReward(player, crate, selectedReward);
        
        return selectedReward;
    }
    
    /**
     * Grant a specific reward to a player.
     */
    public void grantReward(Player player, CrateDef crate, RewardDef reward) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(crate, "crate");
        Objects.requireNonNull(reward, "reward");
        
        // Execute reward
        boolean success = executor.execute(player, reward);
        
        // Record history
        RewardHistory history = success 
            ? RewardHistory.success(player.getUniqueId(), player.getName(), 
                                   crate.id(), crate.name(), reward)
            : RewardHistory.failure(player.getUniqueId(), player.getName(), 
                                   crate.id(), crate.name(), reward);
        
        historyTracker.record(history);
        
        // Notify player
        if (success) {
            notifier.notifyReward(player, reward, RewardNotifier.NotificationStyle.FULL);
            
            // Broadcast if rare (low probability)
            double probability = probabilityEngine.calculateProbability(
                reward, 
                getAllRewards(crate)
            );
            
            if (probability < 5.0) { // Less than 5% chance
                notifier.broadcastRareReward(player, reward);
            }
        }
    }
    
    /**
     * Get all rewards from a crate.
     */
    private List<RewardDef> getAllRewards(CrateDef crate) {
        List<RewardDef> rewards = new ArrayList<>();
        crate.rewards().forEach(rewards::add);
        return rewards;
    }
    
    /**
     * Calculate probability for a reward.
     */
    public double getRewardProbability(RewardDef reward, CrateDef crate) {
        return probabilityEngine.calculateProbability(reward, getAllRewards(crate));
    }
    
    /**
     * Get reward history for a player.
     */
    public List<RewardHistory> getPlayerHistory(Player player) {
        return historyTracker.getPlayerHistory(player.getUniqueId());
    }
    
    /**
     * Get player statistics.
     */
    public RewardHistoryTracker.PlayerStats getPlayerStats(Player player) {
        return historyTracker.getPlayerStats(player.getUniqueId());
    }
    
    /**
     * Register a custom reward executor.
     */
    public void registerExecutor(RewardExecutor customExecutor) {
        executor.register(customExecutor);
    }
    
    /**
     * Get the probability engine.
     */
    public RewardProbabilityEngine getProbabilityEngine() {
        return probabilityEngine;
    }
    
    /**
     * Get the history tracker.
     */
    public RewardHistoryTracker getHistoryTracker() {
        return historyTracker;
    }
    
    /**
     * Get the notifier.
     */
    public RewardNotifier getNotifier() {
        return notifier;
    }
    
    /**
     * Get the executor.
     */
    public CompositeRewardExecutor getExecutor() {
        return executor;
    }
}
