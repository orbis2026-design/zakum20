package net.orbis.zakum.api.item;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public record ZakumItem(ItemStack stack) {

  public static final NamespacedKey KEY = new NamespacedKey("zakum", "id");

  public String getId() {
    return idOf(stack);
  }

  public static String idOf(ItemStack stack) {
    if (stack == null || !stack.hasItemMeta()) return null;
    var meta = stack.getItemMeta();
    if (meta == null) return null;
    return meta.getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
  }

  public static boolean hasId(ItemStack stack, String expectedId) {
    if (expectedId == null || expectedId.isBlank()) return false;
    String found = idOf(stack);
    return found != null && found.equalsIgnoreCase(expectedId);
  }

  public static void setId(ItemStack stack, String id) {
    if (stack == null || id == null || id.isBlank()) return;
    var meta = stack.getItemMeta();
    if (meta == null) return;
    meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, id);
    stack.setItemMeta(meta);
  }
}
