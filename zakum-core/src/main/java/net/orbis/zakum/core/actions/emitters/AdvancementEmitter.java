package net.orbis.zakum.core.actions.emitters;

import net.orbis.zakum.api.actions.ActionBus;
import net.orbis.zakum.api.actions.ActionEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

public final class AdvancementEmitter implements Listener {

  private final ActionBus bus;

  public AdvancementEmitter(ActionBus bus) {
    this.bus = bus;
  }

  @EventHandler
  public void onAdv(PlayerAdvancementDoneEvent e) {
    NamespacedKey key = e.getAdvancement().getKey();

    bus.publish(new ActionEvent(
      "advancement",
      e.getPlayer().getUniqueId(),
      1,
      "key",
      key.toString()
    ));
  }
}
