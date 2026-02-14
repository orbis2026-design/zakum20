package net.orbis.zakum.core.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.orbis.zakum.core.social.OrbisChatRenderer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Applies the Orbis chat pipeline to messages and name rendering.
 */
public final class ChatListener implements Listener {

  private final OrbisChatRenderer renderer;

  public ChatListener(OrbisChatRenderer renderer) {
    this.renderer = renderer;
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onChat(AsyncChatEvent event) {
    Component parsedMessage = renderer.resolveMessage(event.message());
    event.message(parsedMessage);
    event.renderer((source, sourceDisplayName, message, viewer) -> renderer.renderLine(source, message));
  }
}
