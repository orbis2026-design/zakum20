package net.orbis.zakum.core.perf;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;

public final class PlayerVisualModeListener implements Listener {

  private final PlayerVisualModeService visualModeService;

  public PlayerVisualModeListener(PlayerVisualModeService visualModeService) {
    this.visualModeService = Objects.requireNonNull(visualModeService, "visualModeService");
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    visualModeService.load(event.getPlayer());
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    visualModeService.clear(event.getPlayer().getUniqueId());
  }
}

