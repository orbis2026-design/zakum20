package net.orbis.zakum.crates.reward;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.orbis.zakum.crates.model.RewardDef;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Executes permission rewards through LuckPerms.
 * 
 * Grants permissions to players, with optional expiration.
 * Falls back gracefully if LuckPerms is not available.
 */
public class PermissionRewardExecutor implements RewardExecutor {
    
    private LuckPerms luckPerms;
    private boolean luckPermsAvailable = false;
    
    public PermissionRewardExecutor() {
        setupLuckPerms();
    }
    
    @Override
    public boolean execute(Player player, RewardDef reward) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(reward, "reward");
        
        if (!luckPermsAvailable || luckPerms == null) {
            player.sendMessage("§cPermission system not available!");
            return false;
        }
        
        // Parse permission grants from commands
        boolean anyGranted = false;
        
        if (reward.commands() != null) {
            for (String command : reward.commands()) {
                if (command == null) continue;
                
                String lower = command.toLowerCase().trim();
                
                // Pattern: "lp user %player% permission set <permission>"
                // Pattern: "lp user %player% permission set <permission> <duration>"
                if (lower.startsWith("lp user") && lower.contains("permission set")) {
                    String[] parts = command.split("\\s+");
                    if (parts.length >= 5) {
                        String permission = parts[4];
                        long durationSeconds = 0;
                        
                        // Check for duration
                        if (parts.length >= 6) {
                            try {
                                durationSeconds = parseDuration(parts[5]);
                            } catch (Exception e) {
                                // No duration specified
                            }
                        }
                        
                        if (grantPermission(player, permission, durationSeconds)) {
                            anyGranted = true;
                        }
                    }
                }
                
                // Pattern: "permission add %player% <permission>"
                if (lower.startsWith("permission add") || lower.startsWith("perm add")) {
                    String[] parts = command.split("\\s+");
                    if (parts.length >= 3) {
                        String permission = parts[2];
                        if (grantPermission(player, permission, 0)) {
                            anyGranted = true;
                        }
                    }
                }
            }
        }
        
        return anyGranted;
    }
    
    @Override
    public String getType() {
        return "permission";
    }
    
    @Override
    public boolean canHandle(RewardDef reward) {
        if (!luckPermsAvailable || reward == null || reward.commands() == null) {
            return false;
        }
        
        // Check if any commands are permission-related
        for (String command : reward.commands()) {
            if (command == null) continue;
            String lower = command.toLowerCase();
            if (lower.contains("permission") || lower.contains("lp user")) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Grant a permission to a player.
     */
    private boolean grantPermission(Player player, String permission, long durationSeconds) {
        try {
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user == null) {
                return false;
            }
            
            Node.Builder nodeBuilder = Node.builder(permission);
            
            // Add expiration if specified
            if (durationSeconds > 0) {
                nodeBuilder.expiry(durationSeconds, TimeUnit.SECONDS);
            }
            
            Node node = nodeBuilder.build();
            user.data().add(node);
            
            // Save changes
            luckPerms.getUserManager().saveUser(user);
            
            // Notify player
            String message = "§aGranted permission: §e" + permission;
            if (durationSeconds > 0) {
                message += " §7(expires in " + formatDuration(durationSeconds) + ")";
            }
            player.sendMessage(message);
            
            return true;
        } catch (Exception e) {
            player.sendMessage("§cFailed to grant permission: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Setup LuckPerms integration.
     */
    private void setupLuckPerms() {
        RegisteredServiceProvider<LuckPerms> provider = 
            Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        
        if (provider != null) {
            luckPerms = provider.getProvider();
            luckPermsAvailable = luckPerms != null;
        }
    }
    
    /**
     * Parse duration string to seconds.
     * Supports: 30s, 5m, 2h, 1d, 1w
     */
    private long parseDuration(String duration) {
        if (duration == null || duration.isEmpty()) {
            return 0;
        }
        
        duration = duration.toLowerCase().trim();
        
        try {
            char unit = duration.charAt(duration.length() - 1);
            long value = Long.parseLong(duration.substring(0, duration.length() - 1));
            
            return switch (unit) {
                case 's' -> value; // seconds
                case 'm' -> value * 60; // minutes
                case 'h' -> value * 3600; // hours
                case 'd' -> value * 86400; // days
                case 'w' -> value * 604800; // weeks
                default -> Long.parseLong(duration); // assume seconds
            };
        } catch (Exception e) {
            return 0;
        }
    }
    
    /**
     * Format duration in human-readable form.
     */
    private String formatDuration(long seconds) {
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m";
        if (seconds < 86400) return (seconds / 3600) + "h";
        if (seconds < 604800) return (seconds / 86400) + "d";
        return (seconds / 604800) + "w";
    }
    
    /**
     * Check if LuckPerms is available.
     */
    public boolean isLuckPermsAvailable() {
        return luckPermsAvailable;
    }
}
