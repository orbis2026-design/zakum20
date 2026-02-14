package net.orbis.zakum.core.actions;

import net.orbis.zakum.api.ZakumApi;

import net.orbis.zakum.api.ServerIdentity;
import net.orbis.zakum.api.actions.ActionBus;
import net.orbis.zakum.api.actions.DeferredActionService;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

public final class DeferredActionReplayListener implements Listener {

  private final Plugin plugin;
  private final ServerIdentity server;
  private final DeferredActionService deferred;
  private final ActionBus bus;
  private final int limit;

  public DeferredActionReplayListener(Plugin plugin, ServerIdentity server, DeferredActionService deferred, ActionBus bus, int limit) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.server = Objects.requireNonNull(server, "server");
    this.deferred = Objects.requireNonNull(deferred, "deferred");
    this.bus = Objects.requireNonNull(bus, "bus");
    this.limit = Math.max(1, Math.min(500, limit));
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent e) {
    var p = e.getPlayer();
    if (p == null) return;

    // Async DB claim -> sync publish
    deferred.claim(server.serverId(), p.getName(), p.getUniqueId(), limit).whenComplete((events, err) -> {
      if (err != null || events == null || events.isEmpty()) return;

      ZakumApi.get().getScheduler().runTask(plugin, () -> {
        for (var ev : events) bus.publish(ev);
      });
    });
  }
}

