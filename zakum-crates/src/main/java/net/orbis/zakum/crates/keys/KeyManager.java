package net.orbis.zakum.crates.keys;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Manages both physical (inventory) and virtual (database) crate keys.
 * Thread-safe with async database operations.
 */
public class KeyManager {
    
    private final VirtualKeyStore virtualStore;
    private final PhysicalKeyFactory physicalFactory;
    
    public KeyManager(VirtualKeyStore virtualStore, PhysicalKeyFactory physicalFactory) {
        this.virtualStore = virtualStore;
        this.physicalFactory = physicalFactory;
    }
    
    /**
     * Get virtual key count for player (async).
     */
    public CompletableFuture<Integer> getVirtualKeys(UUID playerId, String crateId) {
        return virtualStore.getKeyCount(playerId, crateId);
    }
    
    /**
     * Add virtual keys to player (async).
     */
    public CompletableFuture<Void> giveVirtualKeys(UUID playerId, String crateId, int amount) {
        return virtualStore.addKeys(playerId, crateId, amount);
    }
    
    /**
     * Remove virtual keys from player (async). Returns actual amount removed.
     */
    public CompletableFuture<Integer> takeVirtualKeys(UUID playerId, String crateId, int amount) {
        return virtualStore.removeKeys(playerId, crateId, amount);
    }
    
    /**
     * Create physical key ItemStack for a crate.
     */
    public ItemStack createPhysicalKey(String crateId) {
        return physicalFactory.createKey(crateId);
    }
    
    /**
     * Check if ItemStack is a physical key for specific crate.
     */
    public boolean isPhysicalKey(ItemStack item, String crateId) {
        return physicalFactory.isKey(item, crateId);
    }
    
    /**
     * Count physical keys in player inventory.
     */
    public int countPhysicalKeys(Player player, String crateId) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isPhysicalKey(item, crateId)) {
                count += item.getAmount();
            }
        }
        return count;
    }
    
    /**
     * Remove physical keys from player inventory.
     */
    public boolean takePhysicalKeys(Player player, String crateId, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && isPhysicalKey(item, crateId)) {
                int stackAmount = item.getAmount();
                if (stackAmount <= remaining) {
                    remaining -= stackAmount;
                    contents[i] = null;
                } else {
                    item.setAmount(stackAmount - remaining);
                    remaining = 0;
                }
                if (remaining == 0) break;
            }
        }
        
        player.getInventory().setContents(contents);
        return remaining == 0;
    }
}
