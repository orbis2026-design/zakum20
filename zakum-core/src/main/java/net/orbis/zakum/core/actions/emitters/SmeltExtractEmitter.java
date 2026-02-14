package net.orbis.zakum.core.actions.emitters;

import net.orbis.zakum.api.actions.ActionBus;
import net.orbis.zakum.api.actions.ActionEvent;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceExtractEvent;

public final class SmeltExtractEmitter implements Listener {

  private final ActionBus bus;

  public SmeltExtractEmitter(ActionBus bus) {
    this.bus = bus;
  }

  @EventHandler(ignoreCancelled = true)
  public void onExtract(FurnaceExtractEvent e) {
    Material m = e.getItemType();
    if (m.isAir()) return;

    int amt = e.getItemAmount();
    if (amt <= 0) return;

    bus.publish(new ActionEvent(
      "smelt_extract",
      e.getPlayer().getUniqueId(),
      amt,
      "material",
      m.name()
    ));
  }
}
