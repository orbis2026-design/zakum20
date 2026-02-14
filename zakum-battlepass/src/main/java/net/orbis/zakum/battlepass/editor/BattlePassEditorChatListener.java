package net.orbis.zakum.battlepass.editor;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;

/**
 * Captures chat input for the admin editor prompts.
 *
 * Handles both Paper AsyncChatEvent and legacy AsyncPlayerChatEvent.
 */
@SuppressWarnings("deprecation")
public final class BattlePassEditorChatListener implements Listener {

  private final Plugin plugin;
  private final BattlePassEditor editor;

  public BattlePassEditorChatListener(Plugin plugin, BattlePassEditor editor) {
    this.plugin = plugin;
    this.editor = editor;
  }

  @EventHandler(ignoreCancelled = true)
  public void onPaperChat(AsyncChatEvent e) {
    Player p = e.getPlayer();
    if (p == null) return;
    if (editor.sessions().get(p.getUniqueId()) == null) return;

    String msg = PlainTextComponentSerializer.plainText().serialize(e.message());
    e.setCancelled(true);

    Bukkit.getScheduler().runTask(plugin, () -> editor.handleChatInput(p, msg));
  }

  @EventHandler(ignoreCancelled = true)
  public void onLegacyChat(AsyncPlayerChatEvent e) {
    Player p = e.getPlayer();
    if (p == null) return;
    if (editor.sessions().get(p.getUniqueId()) == null) return;

    String msg = e.getMessage();
    e.setCancelled(true);

    Bukkit.getScheduler().runTask(plugin, () -> editor.handleChatInput(p, msg));
  }
}
