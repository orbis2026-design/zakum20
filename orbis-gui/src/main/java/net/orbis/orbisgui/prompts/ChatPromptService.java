package net.orbis.orbisgui.prompts;

import org.bukkit.plugin.Plugin;
import org.bukkit.event.Listener;

import java.util.Objects;

/**
 * Placeholder prompt service.
 * Runtime prompt workflows are wired incrementally, but this keeps plugin lifecycle stable.
 */
public final class ChatPromptService implements Listener {

  private final Plugin plugin;
  private volatile int timeoutSeconds;

  public ChatPromptService(Plugin plugin) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    reloadFromConfig();
  }

  public void reloadFromConfig() {
    this.timeoutSeconds = Math.max(5, plugin.getConfig().getInt("chat-fetcher-timeout-seconds", 60));
  }

  public int timeoutSeconds() {
    return timeoutSeconds;
  }

  public void shutdown() {
    // Reserved for future prompt cancellation state.
  }
}
