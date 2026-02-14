package net.orbis.zakum.core.actions.emitters;

import net.orbis.zakum.api.actions.ActionBus;
import net.orbis.zakum.api.actions.ActionEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public final class PlayerDeathEmitter implements Listener {

  private final ActionBus bus;

  public PlayerDeathEmitter(ActionBus bus) {
    this.bus = bus;
  }

  @EventHandler
  public void onDeath(PlayerDeathEvent e) {
    Player victim = e.getEntity();
    Player killer = victim.getKiller();

    bus.publish(new ActionEvent(
      "player_death",
      victim.getUniqueId(),
      1,
      "killer",
      killer == null ? "" : killer.getUniqueId().toString()
    ));

    if (killer != null) {
      bus.publish(new ActionEvent(
        "player_kill",
        killer.getUniqueId(),
        1,
        "victim",
        victim.getUniqueId().toString()
      ));
    }
  }
}
