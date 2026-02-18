package net.orbis.zakum.crates.reward;

import net.orbis.zakum.crates.model.RewardDef;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;

/**
 * Executes command rewards.
 * 
 * Commands can be run as console or as the player.
 * Supports placeholder substitution for %player%, %uuid%, etc.
 */
public class CommandRewardExecutor implements RewardExecutor {
    
    @Override
    public boolean execute(Player player, RewardDef reward) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(reward, "reward");
        
        List<String> commands = reward.commands();
        if (commands == null || commands.isEmpty()) {
            return false;
        }
        
        boolean allSucceeded = true;
        
        for (String command : commands) {
            if (command == null || command.isBlank()) {
                continue;
            }
            
            // Substitute placeholders
            String processedCommand = substitutePlaceholders(command, player);
            
            // Check if should run as player or console
            if (processedCommand.startsWith("[player]")) {
                // Run as player
                String playerCommand = processedCommand.substring("[player]".length()).trim();
                boolean success = player.performCommand(playerCommand);
                if (!success) {
                    allSucceeded = false;
                }
            } else {
                // Run as console (default)
                if (processedCommand.startsWith("[console]")) {
                    processedCommand = processedCommand.substring("[console]".length()).trim();
                }
                
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand);
                } catch (Exception e) {
                    allSucceeded = false;
                    Bukkit.getLogger().warning("Failed to execute command: " + processedCommand);
                    e.printStackTrace();
                }
            }
        }
        
        return allSucceeded;
    }
    
    @Override
    public String getType() {
        return "command";
    }
    
    @Override
    public boolean canHandle(RewardDef reward) {
        return reward != null && reward.commands() != null && !reward.commands().isEmpty();
    }
    
    /**
     * Substitute common placeholders in commands.
     */
    private String substitutePlaceholders(String command, Player player) {
        return command
            .replace("%player%", player.getName())
            .replace("%uuid%", player.getUniqueId().toString())
            .replace("%world%", player.getWorld().getName())
            .replace("%x%", String.valueOf(player.getLocation().getBlockX()))
            .replace("%y%", String.valueOf(player.getLocation().getBlockY()))
            .replace("%z%", String.valueOf(player.getLocation().getBlockZ()));
    }
}
