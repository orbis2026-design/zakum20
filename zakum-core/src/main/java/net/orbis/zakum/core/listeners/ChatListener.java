package net.orbis.zakum.core.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.core.moderation.ToxicityModerationService;
import net.orbis.zakum.core.social.OrbisChatRenderer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Applies the Orbis chat pipeline to messages and name rendering.
 */
public final class ChatListener implements Listener {

  private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

  private final ZakumApi api;
  private final OrbisChatRenderer renderer;
  private final ToxicityModerationService moderation;

  public ChatListener(ZakumApi api, OrbisChatRenderer renderer, ToxicityModerationService moderation) {
    this.api = api;
    this.renderer = renderer;
    this.moderation = moderation;
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onChat(AsyncChatEvent event) {
    Player source = event.getPlayer();
    String plain = PLAIN.serialize(event.message());
    var decision = moderation == null ? ToxicityModerationService.Decision.SAFE : moderation.evaluate(plain);
    if (decision.flagged()) {
      moderation.onFlag(api, source, plain, decision);
      if (decision.cancel()) {
        event.setCancelled(true);
        return;
      }
    }

    Component parsedMessage = renderer.resolveMessage(event.message());
    event.message(parsedMessage);
    event.renderer((chatSource, sourceDisplayName, message, viewer) -> {
      Player viewerPlayer = viewer instanceof Player p ? p : null;
      return renderer.renderLine(chatSource, message, viewerPlayer);
    });
  }
}
