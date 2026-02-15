package net.orbis.orbishud.listener;

import net.orbis.orbishud.service.HudService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class HudPlayerListener implements Listener {

  private final HudService service;

  public HudPlayerListener(HudService service) {
    this.service = service;
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onJoin(PlayerJoinEvent event) {
    service.onPlayerJoin(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onQuit(PlayerQuitEvent event) {
    service.onPlayerQuit(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onWorldChange(PlayerChangedWorldEvent event) {
    Player player = event.getPlayer();
    service.refreshPlayer(player);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onGameMode(PlayerGameModeChangeEvent event) {
    service.refreshPlayer(event.getPlayer());
  }
}
