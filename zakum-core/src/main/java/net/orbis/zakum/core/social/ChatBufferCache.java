package net.orbis.zakum.core.social;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.time.Duration;
import java.util.concurrent.atomic.LongAdder;

/**
 * Caches MiniMessage parse output to reduce parse churn on repeated messages.
 */
public final class ChatBufferCache {

  private final boolean enabled;
  private final MiniMessage miniMessage;
  private final Cache<String, Component> cache;
  private final LongAdder requests;
  private final LongAdder hits;
  private final LongAdder misses;

  public ChatBufferCache(boolean enabled, long maximumSize, long expireAfterAccessSeconds) {
    this.enabled = enabled;
    this.miniMessage = MiniMessage.miniMessage();
    this.cache = Caffeine.newBuilder()
      .maximumSize(Math.max(1_000L, maximumSize))
      .expireAfterAccess(Duration.ofSeconds(Math.max(5L, expireAfterAccessSeconds)))
      .build();
    this.requests = new LongAdder();
    this.hits = new LongAdder();
    this.misses = new LongAdder();
  }

  public Component parse(String input) {
    if (input == null || input.isBlank()) return Component.empty();
    requests.increment();
    if (!enabled) return miniMessage.deserialize(input);

    Component cached = cache.getIfPresent(input);
    if (cached != null) {
      hits.increment();
      return cached;
    }

    misses.increment();
    Component parsed = miniMessage.deserialize(input);
    cache.put(input, parsed);
    return parsed;
  }

  public Stats stats() {
    return new Stats(
      enabled,
      requests.sum(),
      hits.sum(),
      misses.sum(),
      cache.estimatedSize()
    );
  }

  public record Stats(
    boolean enabled,
    long requests,
    long hits,
    long misses,
    long estimatedSize
  ) {}
}
