package net.orbis.zakum.core.actions.emitters;

import net.orbis.zakum.api.actions.ActionBus;
import net.orbis.zakum.api.actions.ActionEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

public final class FishCatchEmitter implements Listener {

  private final ActionBus bus;

  public FishCatchEmitter(ActionBus bus) {
    this.bus = bus;
  }

  @EventHandler(ignoreCancelled = true)
  public void onFish(PlayerFishEvent e) {
    if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH && e.getState() != PlayerFishEvent.State.CAUGHT_ENTITY) return;

    Entity caught = e.getCaught();
    String value = "";

    if (caught instanceof Item it) {
      ItemStack stack = it.getItemStack();
      value = stack.getType().name();
    } else if (caught != null) {
      value = caught.getType().name();
    }

    bus.publish(new ActionEvent(
      "fish_catch",
      e.getPlayer().getUniqueId(),
      1,
      "caught",
      value
    ));
  }
}
