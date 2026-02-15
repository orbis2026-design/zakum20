package net.orbis.zakum.api.cache;

import java.util.concurrent.CompletableFuture;

/**
 * Shared transient cache primitive for bursty cross-module workloads.
 *
 * Contract notes:
 * - Async-only API: callers must not assume in-thread completion.
 * - Implementations may use Redis + local fallback.
 */
public interface BurstCacheService {

  boolean configuredEnabled();

  boolean runtimeEnabled();

  void setRuntimeEnabled(boolean enabled);

  boolean available();

  CompletableFuture<String> get(String namespace, String key);

  CompletableFuture<Void> put(String namespace, String key, String value, long ttlSeconds);

  CompletableFuture<Long> increment(String namespace, String key, long delta, long ttlSeconds);

  CompletableFuture<Void> remove(String namespace, String key);

  Snapshot snapshot();

  record Snapshot(
    boolean configuredEnabled,
    boolean runtimeEnabled,
    boolean available,
    String redisUri,
    String keyPrefix,
    long defaultTtlSeconds,
    long maximumLocalEntries,
    long gets,
    long puts,
    long increments,
    long removes,
    long redisHits,
    long localHits,
    long redisFailures,
    long lastFailureAtMs,
    String lastError
  ) {}
}
