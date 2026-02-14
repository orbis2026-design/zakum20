package net.orbis.zakum.core.social;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.time.Duration;

/**
 * Caches MiniMessage parse output to reduce parse churn on repeated messages.
 */
public final class ChatBufferCache {

  private final boolean enabled;
  private final MiniMessage miniMessage;
  private final Cache<String, Component> cache;

  public ChatBufferCache(boolean enabled, long maximumSize, long expireAfterAccessSeconds) {
    this.enabled = enabled;
    this.miniMessage = MiniMessage.miniMessage();
    this.cache = Caffeine.newBuilder()
      .maximumSize(Math.max(1_000L, maximumSize))
      .expireAfterAccess(Duration.ofSeconds(Math.max(5L, expireAfterAccessSeconds)))
      .build();
  }

  public Component parse(String input) {
    if (input == null || input.isBlank()) return Component.empty();
    if (!enabled) return miniMessage.deserialize(input);
    return cache.get(input, miniMessage::deserialize);
  }
}
