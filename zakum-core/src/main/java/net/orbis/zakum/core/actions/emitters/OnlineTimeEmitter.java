package net.orbis.zakum.core.actions.emitters;

import net.orbis.zakum.api.actions.ActionBus;
import net.orbis.zakum.api.actions.ActionEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Emits online time deltas when a player quits.
 *
 * Action:
 *   type = online_time
 *   amount = seconds
 */
public final class OnlineTimeEmitter implements Listener {

  private final ActionBus bus;
  private final Clock clock;
  private final Map<UUID, Long> joinEpochSeconds = new HashMap<>();

  public OnlineTimeEmitter(ActionBus bus, Clock clock) {
    this.bus = bus;
    this.clock = clock;

    // Seed for /reload or plugin late enable.
    for (Player p : Bukkit.getOnlinePlayers()) {
      joinEpochSeconds.put(p.getUniqueId(), clock.instant().getEpochSecond());
    }
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent e) {
    joinEpochSeconds.put(e.getPlayer().getUniqueId(), clock.instant().getEpochSecond());
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent e) {
    UUID uuid = e.getPlayer().getUniqueId();
    Long start = joinEpochSeconds.remove(uuid);
    if (start == null) return;

    long now = clock.instant().getEpochSecond();
    long seconds = Math.max(1, now - start);

    bus.publish(new ActionEvent(
      "online_time",
      uuid,
      seconds,
      "unit",
      "seconds"
    ));
  }
}
