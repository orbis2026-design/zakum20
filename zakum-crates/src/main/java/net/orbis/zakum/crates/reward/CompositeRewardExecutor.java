package net.orbis.zakum.crates.reward;

import net.orbis.zakum.crates.model.RewardDef;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Composite reward executor that delegates to appropriate sub-executors.
 * 
 * Handles all reward types and executes them in proper order.
 */
public class CompositeRewardExecutor implements RewardExecutor {
    
    private final List<RewardExecutor> executors = new ArrayList<>();
    
    public CompositeRewardExecutor() {
        // Register all executor types
        register(new ItemRewardExecutor());
        register(new CommandRewardExecutor());
        register(new EffectRewardExecutor());
    }
    
    /**
     * Register a custom executor.
     */
    public void register(RewardExecutor executor) {
        Objects.requireNonNull(executor, "executor");
        executors.add(executor);
    }
    
    @Override
    public boolean execute(Player player, RewardDef reward) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(reward, "reward");
        
        boolean anyExecuted = false;
        
        // Execute all applicable executors
        for (RewardExecutor executor : executors) {
            if (executor.canHandle(reward)) {
                try {
                    boolean success = executor.execute(player, reward);
                    anyExecuted = anyExecuted || success;
                } catch (Exception e) {
                    player.sendMessage("§cError executing reward: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        
        // Send messages if any
        if (reward.messages() != null) {
            for (String message : reward.messages()) {
                if (message != null && !message.isBlank()) {
                    player.sendMessage(message
                        .replace("%player%", player.getName())
                        .replace('&', '§'));
                }
            }
        }
        
        return anyExecuted;
    }
    
    @Override
    public String getType() {
        return "composite";
    }
    
    @Override
    public boolean canHandle(RewardDef reward) {
        return true; // Can handle any reward
    }
}
