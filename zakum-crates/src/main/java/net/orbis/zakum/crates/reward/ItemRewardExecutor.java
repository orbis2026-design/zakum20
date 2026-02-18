package net.orbis.zakum.crates.reward;

import net.orbis.zakum.crates.model.RewardDef;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/**
 * Executes item rewards.
 * 
 * Gives items to player's inventory, handling overflow to ground.
 */
public class ItemRewardExecutor implements RewardExecutor {
    
    @Override
    public boolean execute(Player player, RewardDef reward) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(reward, "reward");
        
        List<ItemStack> items = reward.items();
        if (items == null || items.isEmpty()) {
            return false;
        }
        
        boolean allGiven = true;
        
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            
            // Try to add to inventory
            HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item.clone());
            
            // Drop overflow items at player's location
            if (!overflow.isEmpty()) {
                for (ItemStack overflowItem : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), overflowItem);
                }
                
                // Notify player about overflow
                player.sendMessage("§eSome items were dropped on the ground!");
                allGiven = false;
            }
        }
        
        return allGiven;
    }
    
    @Override
    public String getType() {
        return "item";
    }
    
    @Override
    public boolean canHandle(RewardDef reward) {
        return reward != null && reward.items() != null && !reward.items().isEmpty();
    }
}
