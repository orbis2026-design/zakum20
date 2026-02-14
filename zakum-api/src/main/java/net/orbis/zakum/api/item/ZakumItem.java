package net.orbis.zakum.api.item;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public record ZakumItem(ItemStack stack) {

  public static final NamespacedKey KEY = new NamespacedKey("zakum", "id");

  public String getId() {
    if (stack == null || !stack.hasItemMeta()) return null;
    var meta = stack.getItemMeta();
    if (meta == null) return null;
    return meta.getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
  }
}
