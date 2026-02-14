package net.orbis.zakum.core.actions.emitters;

import net.orbis.zakum.api.actions.ActionBus;
import net.orbis.zakum.api.actions.ActionEvent;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.inventory.ItemStack;

public final class EnchantEmitter implements Listener {

  private final ActionBus bus;

  public EnchantEmitter(ActionBus bus) {
    this.bus = bus;
  }

  @EventHandler(ignoreCancelled = true)
  public void onEnchant(EnchantItemEvent e) {
    ItemStack item = e.getItem();
    Material m = item.getType();
    if (m.isAir()) return;

    bus.publish(new ActionEvent(
      "item_enchant",
      e.getEnchanter().getUniqueId(),
      1,
      "material",
      m.name()
    ));
  }
}
