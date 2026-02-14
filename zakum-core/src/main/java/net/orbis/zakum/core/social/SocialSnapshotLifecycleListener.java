package net.orbis.zakum.core.social;

import net.orbis.zakum.api.social.SocialService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class SocialSnapshotLifecycleListener implements Listener {

  private final SocialService socialService;

  public SocialSnapshotLifecycleListener(SocialService socialService) {
    this.socialService = socialService;
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    socialService.refreshAsync(event.getPlayer().getUniqueId());
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    socialService.invalidate(event.getPlayer().getUniqueId());
  }
}
