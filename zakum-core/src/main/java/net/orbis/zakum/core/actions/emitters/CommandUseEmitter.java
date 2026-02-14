package net.orbis.zakum.core.actions.emitters;

import net.orbis.zakum.api.actions.ActionBus;
import net.orbis.zakum.api.actions.ActionEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;
import java.util.Set;

/**
 * Potentially noisy/sensitive. Use allowlist.
 */
public final class CommandUseEmitter implements Listener {

  private final ActionBus bus;
  private final Set<String> allowlist;

  public CommandUseEmitter(ActionBus bus, Set<String> allowlist) {
    this.bus = bus;
    this.allowlist = allowlist;
  }

  @EventHandler(ignoreCancelled = true)
  public void onCmd(PlayerCommandPreprocessEvent e) {
    String msg = e.getMessage();
    if (msg == null || msg.isBlank()) return;

    // "/cmd arg1 arg2" -> "cmd"
    String raw = msg.charAt(0) == '/' ? msg.substring(1) : msg;
    String root = raw.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
    if (root.isBlank()) return;

    if (!allowlist.isEmpty() && !allowlist.contains(root)) return;

    bus.publish(new ActionEvent(
      "command_use",
      e.getPlayer().getUniqueId(),
      1,
      "cmd",
      root
    ));
  }
}
