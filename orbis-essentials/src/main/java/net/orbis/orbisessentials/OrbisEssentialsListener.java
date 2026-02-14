package net.orbis.orbisessentials;

import net.orbis.orbisessentials.teleport.TeleportService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;

public final class OrbisEssentialsListener implements Listener {

  private final TeleportService tp;

  public OrbisEssentialsListener(TeleportService tp) {
    this.tp = Objects.requireNonNull(tp, "tp");
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent e) {
    tp.cancel(e.getPlayer().getUniqueId());
  }

  @EventHandler
  public void onDeath(PlayerDeathEvent e) {
    tp.cancel(e.getEntity().getUniqueId());
  }
}
