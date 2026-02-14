package net.orbis.zakum.crates.keys;

import net.orbis.zakum.crates.model.CrateDef;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Factory for creating and validating physical key items with NBT data.
 */
public class PhysicalKeyFactory {
    
    private final Plugin plugin;
    private final NamespacedKey crateIdKey;
    
    public PhysicalKeyFactory(Plugin plugin) {
        this.plugin = plugin;
        this.crateIdKey = new NamespacedKey(plugin, "crate_id");
    }
    
    public ItemStack createKey(String crateId, CrateDef crate) {
        // Get material from crate config
        Material material = Material.valueOf(crate.key().material());
        ItemStack item = new ItemStack(material, 1);
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        
        // Set display name
        meta.setDisplayName(crate.key().name());
        
        // Set lore
        if (crate.key().lore() != null && !crate.key().lore().isEmpty()) {
            meta.setLore(crate.key().lore());
        }
        
        // Set NBT data to identify this as a crate key
        meta.getPersistentDataContainer().set(crateIdKey, PersistentDataType.STRING, crateId);
        
        // Make it glow if configured
        if (crate.key().glow()) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        }
        
        // Hide all attributes
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
        
        item.setItemMeta(meta);
        return item;
    }
    
    public ItemStack createKey(String crateId) {
        // Fallback if crate def not available
        ItemStack item = new ItemStack(Material.TRIPWIRE_HOOK, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(crateIdKey, PersistentDataType.STRING, crateId);
            item.setItemMeta(meta);
        }
        return item;
    }
    
    public boolean isKey(ItemStack item, String crateId) {
        if (item == null || !item.hasItemMeta()) return false;
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        
        String storedCrateId = meta.getPersistentDataContainer().get(crateIdKey, PersistentDataType.STRING);
        return crateId.equals(storedCrateId);
    }
    
    public String getCrateId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        
        return meta.getPersistentDataContainer().get(crateIdKey, PersistentDataType.STRING);
    }
}
