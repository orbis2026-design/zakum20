package net.orbis.zakum.battlepass.editor;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

final class EditorItems {

  private EditorItems() {}

  static ItemStack item(Material mat, String name, List<String> lore) {
    ItemStack it = new ItemStack(mat);
    ItemMeta im = it.getItemMeta();
    if (im != null) {
      im.displayName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(name));
      if (lore != null && !lore.isEmpty()) {
        im.lore(lore.stream()
          .map(line -> net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(line))
          .toList());
      }
      im.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
      im.addItemFlags(ItemFlag.HIDE_ENCHANTS);
      it.setItemMeta(im);
    }
    return it;
  }

  static List<String> lore(String... lines) {
    List<String> out = new ArrayList<>();
    if (lines == null) return out;
    for (String s : lines) {
      if (s == null) continue;
      out.add(s);
    }
    return out;
  }

  static ItemStack navBack() {
    return item(Material.ARROW, ChatColor.YELLOW + "Back", List.of(ChatColor.DARK_GRAY + "Left click"));
  }

  static ItemStack navClose() {
    return item(Material.BARRIER, ChatColor.RED + "Close", List.of());
  }

  static ItemStack navPrev() {
    return item(Material.ARROW, ChatColor.GRAY + "Prev", List.of());
  }

  static ItemStack navNext() {
    return item(Material.ARROW, ChatColor.GRAY + "Next", List.of());
  }
}
