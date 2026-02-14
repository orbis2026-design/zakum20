package net.orbis.zakum.core.listeners;

import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.api.action.AceEngine;
import net.orbis.zakum.api.capability.ZakumCapabilities;
import net.orbis.zakum.api.social.SocialService;
import net.orbis.zakum.core.cloud.SecureCloudClient;
import net.orbis.zakum.core.social.CloudTabRenderer;
import net.orbis.zakum.core.util.PdcKeys;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.logging.Logger;

public final class CloudIdentityListener implements Listener {

  private static final List<String> JOIN_TITLE_SCRIPT =
    List.of("[TITLE] <gradient:#44FFCC:#33AAFF>Welcome back!</gradient>");

  private final ZakumApi api;
  private final SecureCloudClient cloudClient;
  private final CloudTabRenderer tabRenderer;
  private final Logger logger;

  public CloudIdentityListener(ZakumApi api, SecureCloudClient cloudClient, CloudTabRenderer tabRenderer, Logger logger) {
    this.api = api;
    this.cloudClient = cloudClient;
    this.tabRenderer = tabRenderer;
    this.logger = logger;
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    api.getScheduler().runAsync(() -> cloudClient.fetchIdentity(player.getUniqueId())
      .thenAccept(identity -> api.getScheduler().runAtEntity(player, () -> {
        if (!player.isOnline()) return;
        player.getPersistentDataContainer().set(PdcKeys.CLOUD_RANK, PersistentDataType.STRING, identity.rank());
        byte linked = identity.discordLinked() ? (byte) 1 : (byte) 0;
        player.getPersistentDataContainer().set(PdcKeys.CLOUD_DISCORD_LINKED, PersistentDataType.BYTE, linked);
        if (identity.discordId() != null && !identity.discordId().isBlank()) {
          player.getPersistentDataContainer().set(PdcKeys.CLOUD_DISCORD_ID, PersistentDataType.STRING, identity.discordId());
        }
        api.capability(ZakumCapabilities.SOCIAL).ifPresent(social ->
          social.upsert(
            player.getUniqueId(),
            new SocialService.SocialSnapshot(
              identity.friends(),
              identity.allies(),
              identity.rivals(),
              System.currentTimeMillis()
            )
          )
        );
        tabRenderer.render(player);
        api.getAceEngine().executeScript(JOIN_TITLE_SCRIPT, AceEngine.ActionContext.of(player));
      }))
      .exceptionally(ex -> {
        logger.fine("Cloud identity fetch failed for " + player.getUniqueId() + ": " + ex.getMessage());
        return null;
      }));
  }
}
