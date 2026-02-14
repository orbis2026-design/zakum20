package net.orbis.zakum.crates.keys;

import net.orbis.zakum.api.item.ZakumItem;
import net.orbis.zakum.crates.model.CrateDef;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Factory for creating and validating physical key items with NBT data.
 */
public class PhysicalKeyFactory {
    
    private final NamespacedKey legacyCrateIdKey;
    
    public PhysicalKeyFactory(Plugin plugin) {
        this.legacyCrateIdKey = new NamespacedKey(plugin, "crate_id");
    }
    
    public ItemStack createKey(String crateId, CrateDef crate) {
        if (crate == null || crate.keyItem() == null) {
            return createKey(crateId);
        }

        ItemStack item = crate.keyItem().clone();
        item.setAmount(1);

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        setIds(meta, crateId);
        item.setItemMeta(meta);
        return item;
    }
    
    public ItemStack createKey(String crateId) {
        // Fallback if crate def not available
        ItemStack item = new ItemStack(Material.TRIPWIRE_HOOK, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setIds(meta, crateId);
            item.setItemMeta(meta);
        }
        return item;
    }
    
    public boolean isKey(ItemStack item, String crateId) {
        if (item == null || !item.hasItemMeta()) return false;
        if (ZakumItem.hasId(item, crateId)) return true;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        String storedCrateId = meta.getPersistentDataContainer().get(legacyCrateIdKey, PersistentDataType.STRING);
        return crateId.equals(storedCrateId);
    }
    
    public String getCrateId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String id = ZakumItem.idOf(item);
        if (id != null && !id.isBlank()) return id;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        return meta.getPersistentDataContainer().get(legacyCrateIdKey, PersistentDataType.STRING);
    }

    private void setIds(ItemMeta meta, String crateId) {
        if (meta == null || crateId == null || crateId.isBlank()) return;
        meta.getPersistentDataContainer().set(ZakumItem.KEY, PersistentDataType.STRING, crateId);
        meta.getPersistentDataContainer().set(legacyCrateIdKey, PersistentDataType.STRING, crateId);
    }
}
