package net.orbis.zakum.core.actions.emitters;

import net.orbis.zakum.api.actions.ActionBus;
import net.orbis.zakum.api.actions.ActionEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerLifecycleEmitter implements Listener {

  private final ActionBus bus;

  public PlayerLifecycleEmitter(ActionBus bus) {
    this.bus = bus;
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent e) {
    bus.publish(new ActionEvent(
      "player_join",
      e.getPlayer().getUniqueId(),
      1,
      "",
      ""
    ));
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent e) {
    bus.publish(new ActionEvent(
      "player_quit",
      e.getPlayer().getUniqueId(),
      1,
      "",
      ""
    ));
  }
}
