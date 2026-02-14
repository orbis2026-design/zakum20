package net.orbis.zakum.core.actions.emitters;

import net.orbis.zakum.api.actions.ActionBus;
import net.orbis.zakum.api.actions.ActionEvent;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;

public final class CraftEmitter implements Listener {

  private final ActionBus bus;

  public CraftEmitter(ActionBus bus) {
    this.bus = bus;
  }

  @EventHandler(ignoreCancelled = true)
  public void onCraft(CraftItemEvent e) {
    if (!(e.getWhoClicked() instanceof org.bukkit.entity.Player p)) return;

    ItemStack current = e.getCurrentItem();
    ItemStack out = current != null ? current : (e.getRecipe() == null ? null : e.getRecipe().getResult());
    if (out == null) return;

    Material m = out.getType();
    if (m.isAir()) return;

    long amt = Math.max(1, out.getAmount());

    bus.publish(new ActionEvent(
      "item_craft",
      p.getUniqueId(),
      amt,
      "material",
      m.name()
    ));
  }
}
