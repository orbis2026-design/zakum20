package net.orbis.zakum.core.actions.emitters;

import net.orbis.zakum.api.actions.ActionBus;
import net.orbis.zakum.api.actions.ActionEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLevelChangeEvent;

public final class LevelChangeEmitter implements Listener {

  private final ActionBus bus;

  public LevelChangeEmitter(ActionBus bus) {
    this.bus = bus;
  }

  @EventHandler(ignoreCancelled = true)
  public void onLevel(PlayerLevelChangeEvent e) {
    int delta = e.getNewLevel() - e.getOldLevel();
    if (delta == 0) return;

    bus.publish(new ActionEvent(
      "level_change",
      e.getPlayer().getUniqueId(),
      Math.abs((long) delta),
      "direction",
      delta > 0 ? "UP" : "DOWN"
    ));
  }
}
