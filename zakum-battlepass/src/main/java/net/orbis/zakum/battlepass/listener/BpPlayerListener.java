package net.orbis.zakum.battlepass.listener;

import net.orbis.zakum.battlepass.BattlePassRuntime;
import net.orbis.zakum.battlepass.ui.NameCache;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;

public final class BpPlayerListener implements Listener {

  private final BattlePassRuntime runtime;
  private final NameCache names;
  private final Map<UUID, Long> npcOpenCooldownMs;

  public BpPlayerListener(BattlePassRuntime runtime, NameCache names, Map<UUID, Long> npcOpenCooldownMs) {
    this.runtime = runtime;
    this.names = names;
    this.npcOpenCooldownMs = npcOpenCooldownMs;
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent e) {
    names.put(e.getPlayer().getUniqueId(), e.getPlayer().getName());
    runtime.onJoin(e.getPlayer().getUniqueId());
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent e) {
    // Prevent an unbounded map from growing forever on long-uptime servers.
    if (npcOpenCooldownMs != null) npcOpenCooldownMs.remove(e.getPlayer().getUniqueId());
    runtime.onQuit(e.getPlayer().getUniqueId());
  }
}
