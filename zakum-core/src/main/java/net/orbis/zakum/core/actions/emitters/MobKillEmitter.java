package net.orbis.zakum.core.actions.emitters;

import net.orbis.zakum.api.actions.ActionBus;
import net.orbis.zakum.api.actions.ActionEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public final class MobKillEmitter implements Listener {

  private final ActionBus bus;

  public MobKillEmitter(ActionBus bus) {
    this.bus = bus;
  }

  @EventHandler(ignoreCancelled = true)
  public void onEntityDeath(EntityDeathEvent e) {
    Player killer = e.getEntity().getKiller();
    if (killer == null) return;

    bus.publish(new ActionEvent(
      "mob_kill",
      killer.getUniqueId(),
      1,
      "entity",
      e.getEntityType().name()
    ));
  }
}
