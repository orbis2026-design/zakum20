package net.orbis.zakum.core.actions.emitters;

import net.orbis.zakum.api.actions.ActionBus;
import net.orbis.zakum.api.actions.ActionEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerExpChangeEvent;

public final class XpGainEmitter implements Listener {

  private final ActionBus bus;

  public XpGainEmitter(ActionBus bus) {
    this.bus = bus;
  }

  @EventHandler(ignoreCancelled = true)
  public void onXp(PlayerExpChangeEvent e) {
    int amt = e.getAmount();
    if (amt <= 0) return;

    bus.publish(new ActionEvent(
      "xp_gain",
      e.getPlayer().getUniqueId(),
      amt,
      "",
      ""
    ));
  }
}
