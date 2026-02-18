package net.orbis.zakum.crates.reward;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.orbis.zakum.api.vault.EconomyService;
import net.orbis.zakum.crates.model.RewardDef;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Objects;

/**
 * Executes money rewards through Vault economy.
 * 
 * Requires Vault and an economy plugin to be installed.
 * Falls back gracefully if Vault is not available.
 */
public class MoneyRewardExecutor implements RewardExecutor {
    
    private Economy economy;
    private EconomyService economyService;
    private boolean vaultAvailable = false;
    
    public MoneyRewardExecutor(EconomyService economyService) {
        this.economyService = economyService;
        this.vaultAvailable = economyService != null;
        if (!vaultAvailable) {
            setupEconomy();
        }
    }
    
    public MoneyRewardExecutor() {
        this(null);
    }
    
    @Override
    public boolean execute(Player player, RewardDef reward) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(reward, "reward");
        
        if (!vaultAvailable && economy == null) {
            player.sendMessage("§cEconomy system not available!");
            return false;
        }
        
        // Check if reward has money specified in commands or messages
        // Look for economy amount in various formats
        double amount = parseMoneyAmount(reward);
        
        if (amount <= 0) {
            return false; // No money reward
        }
        
        // Use EconomyService if available, otherwise use Vault directly
        if (economyService != null) {
            economyService.deposit(player.getUniqueId(), amount);
            player.sendMessage("§a+$" + String.format("%.2f", amount));
            return true;
        } else if (economy != null) {
            // Deposit money via Vault
            EconomyResponse response = economy.depositPlayer(player, amount);
            
            if (response.transactionSuccess()) {
                player.sendMessage("§a+$" + String.format("%.2f", amount));
                return true;
            } else {
                player.sendMessage("§cFailed to give money: " + response.errorMessage);
                return false;
            }
        }
        
        return false;
    }
    
    @Override
    public String getType() {
        return "money";
    }
    
    @Override
    public boolean canHandle(RewardDef reward) {
        return (economyService != null || vaultAvailable) && parseMoneyAmount(reward) > 0;
    }
    
    /**
     * Setup Vault economy integration.
     */
    private void setupEconomy() {
        if (Bukkit.getServer().getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        
        RegisteredServiceProvider<Economy> rsp = 
            Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
        
        if (rsp == null) {
            return;
        }
        
        economy = rsp.getProvider();
        vaultAvailable = economy != null;
    }
    
    /**
     * Parse money amount from reward definition.
     * Looks in commands for patterns like "eco give %player% <amount>"
     */
    private double parseMoneyAmount(RewardDef reward) {
        if (reward == null || reward.commands() == null) {
            return 0.0;
        }
        
        for (String command : reward.commands()) {
            if (command == null) continue;
            
            String lower = command.toLowerCase().trim();
            
            // Pattern: "eco give %player% <amount>"
            if (lower.startsWith("eco give") || lower.startsWith("[console]eco give")) {
                String[] parts = command.split("\\s+");
                if (parts.length >= 4) {
                    try {
                        return Double.parseDouble(parts[parts.length - 1]);
                    } catch (NumberFormatException e) {
                        // Continue checking other commands
                    }
                }
            }
            
            // Pattern: "money give %player% <amount>"
            if (lower.startsWith("money give") || lower.contains("give") && lower.contains("money")) {
                String[] parts = command.split("\\s+");
                for (String part : parts) {
                    try {
                        double value = Double.parseDouble(part);
                        if (value > 0) return value;
                    } catch (NumberFormatException e) {
                        // Continue
                    }
                }
            }
        }
        
        return 0.0;
    }
    
    /**
     * Check if Vault economy is available.
     */
    public boolean isVaultAvailable() {
        return vaultAvailable;
    }
}
