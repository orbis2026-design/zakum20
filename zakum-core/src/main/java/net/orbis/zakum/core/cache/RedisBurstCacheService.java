package net.orbis.zakum.core.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.orbis.zakum.api.cache.BurstCacheService;
import net.orbis.zakum.api.concurrent.ZakumScheduler;
import net.orbis.zakum.core.metrics.MetricsMonitor;
import net.orbis.zakum.core.perf.ThreadGuard;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.net.URI;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * Shared Redis-backed burst cache with local in-memory fallback.
 */
public final class RedisBurstCacheService implements BurstCacheService, AutoCloseable {

  private final ZakumScheduler scheduler;
  private final ThreadGuard threadGuard;
  private final MetricsMonitor metrics;
  private final Logger logger;
  private final boolean configuredEnabled;
  private final String redisUri;
  private final String keyPrefix;
  private final long defaultTtlSeconds;
  private final long maximumLocalEntries;
  private final JedisPool jedisPool;
  private final Cache<String, LocalEntry> localFallback;
  private final AtomicBoolean runtimeEnabled;
  private final AtomicBoolean redisOnline;
  private final AtomicLong gets;
  private final AtomicLong puts;
  private final AtomicLong increments;
  private final AtomicLong removes;
  private final AtomicLong redisHits;
  private final AtomicLong localHits;
  private final AtomicLong redisFailures;
  private final AtomicLong lastFailureAtMs;
  private final AtomicReference<String> lastError;

  public RedisBurstCacheService(
    ZakumScheduler scheduler,
    ThreadGuard threadGuard,
    MetricsMonitor metrics,
    Logger logger,
    boolean configuredEnabled,
    String redisUri,
    String keyPrefix,
    long defaultTtlSeconds,
    long maximumLocalEntries
  ) {
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    this.threadGuard = Objects.requireNonNull(threadGuard, "threadGuard");
    this.metrics = metrics;
    this.logger = logger;
    this.configuredEnabled = configuredEnabled;
    this.redisUri = redisUri == null ? "" : redisUri.trim();
    this.keyPrefix = keyPrefix == null || keyPrefix.isBlank() ? "zakum:burst" : keyPrefix.trim();
    this.defaultTtlSeconds = Math.max(1L, defaultTtlSeconds);
    this.maximumLocalEntries = Math.max(100L, maximumLocalEntries);
    this.localFallback = Caffeine.newBuilder()
      .maximumSize(this.maximumLocalEntries)
      .build();
    this.runtimeEnabled = new AtomicBoolean(configuredEnabled);
    this.redisOnline = new AtomicBoolean(false);
    this.gets = new AtomicLong();
    this.puts = new AtomicLong();
    this.increments = new AtomicLong();
    this.removes = new AtomicLong();
    this.redisHits = new AtomicLong();
    this.localHits = new AtomicLong();
    this.redisFailures = new AtomicLong();
    this.lastFailureAtMs = new AtomicLong(0L);
    this.lastError = new AtomicReference<>("");
    this.jedisPool = buildPool(this.redisUri);
  }

  private JedisPool buildPool(String uri) {
    if (uri == null || uri.isBlank()) return null;
    try {
      return new JedisPool(URI.create(uri));
    } catch (Throwable ex) {
      onRedisError("init", ex);
      return null;
    }
  }

  @Override
  public boolean configuredEnabled() {
    return configuredEnabled;
  }

  @Override
  public boolean runtimeEnabled() {
    return runtimeEnabled.get();
  }

  @Override
  public void setRuntimeEnabled(boolean enabled) {
    runtimeEnabled.set(enabled);
  }

  @Override
  public boolean available() {
    return runtimeEnabled() && jedisPool != null;
  }

  @Override
  public CompletableFuture<String> get(String namespace, String key) {
    return scheduler.supplyAsync(() -> doGet(namespace, key));
  }

  @Override
  public CompletableFuture<Void> put(String namespace, String key, String value, long ttlSeconds) {
    return scheduler.supplyAsync(() -> {
      doPut(namespace, key, value, ttlSeconds);
      return null;
    });
  }

  @Override
  public CompletableFuture<Long> increment(String namespace, String key, long delta, long ttlSeconds) {
    return scheduler.supplyAsync(() -> doIncrement(namespace, key, delta, ttlSeconds));
  }

  @Override
  public CompletableFuture<Void> remove(String namespace, String key) {
    return scheduler.supplyAsync(() -> {
      doRemove(namespace, key);
      return null;
    });
  }

  private String doGet(String namespace, String key) {
    gets.incrementAndGet();
    String redisKey = redisKey(namespace, key);
    if (available()) {
      try (Jedis jedis = jedisPool.getResource()) {
        threadGuard.checkAsync("redisBurst.get");
        String value = jedis.get(redisKey);
        redisOnline.set(true);
        if (value != null) {
          redisHits.incrementAndGet();
          localPut(redisKey, value, defaultTtlSeconds);
          recordMetric("burst_cache_get_redis");
          return value;
        }
      } catch (Throwable ex) {
        onRedisError("get", ex);
      }
    }

    String local = localGet(redisKey);
    if (local != null) {
      localHits.incrementAndGet();
      recordMetric("burst_cache_get_local");
    } else {
      recordMetric("burst_cache_get_miss");
    }
    return local;
  }

  private void doPut(String namespace, String key, String value, long ttlSeconds) {
    puts.incrementAndGet();
    String redisKey = redisKey(namespace, key);
    String payload = value == null ? "" : value;
    long ttl = normalizeTtl(ttlSeconds);
    if (available()) {
      try (Jedis jedis = jedisPool.getResource()) {
        threadGuard.checkAsync("redisBurst.put");
        if (ttl > 0L) {
          jedis.setex(redisKey, (int) Math.min(Integer.MAX_VALUE, ttl), payload);
        } else {
          jedis.set(redisKey, payload);
        }
        redisOnline.set(true);
        recordMetric("burst_cache_put_redis");
      } catch (Throwable ex) {
        onRedisError("put", ex);
      }
    }
    localPut(redisKey, payload, ttl);
  }

  private long doIncrement(String namespace, String key, long delta, long ttlSeconds) {
    increments.incrementAndGet();
    String redisKey = redisKey(namespace, key);
    long ttl = normalizeTtl(ttlSeconds);
    if (available()) {
      try (Jedis jedis = jedisPool.getResource()) {
        threadGuard.checkAsync("redisBurst.increment");
        long value = jedis.incrBy(redisKey, delta);
        if (ttl > 0L) {
          jedis.expire(redisKey, (int) Math.min(Integer.MAX_VALUE, ttl));
        }
        redisOnline.set(true);
        localPut(redisKey, String.valueOf(value), ttl);
        recordMetric("burst_cache_incr_redis");
        return value;
      } catch (Throwable ex) {
        onRedisError("increment", ex);
      }
    }

    long fallback = parseLong(localGet(redisKey), 0L) + delta;
    localPut(redisKey, String.valueOf(fallback), ttl);
    recordMetric("burst_cache_incr_local");
    return fallback;
  }

  private void doRemove(String namespace, String key) {
    removes.incrementAndGet();
    String redisKey = redisKey(namespace, key);
    if (available()) {
      try (Jedis jedis = jedisPool.getResource()) {
        threadGuard.checkAsync("redisBurst.remove");
        jedis.del(redisKey);
        redisOnline.set(true);
        recordMetric("burst_cache_del_redis");
      } catch (Throwable ex) {
        onRedisError("remove", ex);
      }
    }
    localFallback.invalidate(redisKey);
  }

  private void localPut(String redisKey, String value, long ttlSeconds) {
    long ttl = ttlSeconds <= 0L ? defaultTtlSeconds : ttlSeconds;
    long expiresAt = System.currentTimeMillis() + (Math.max(1L, ttl) * 1000L);
    localFallback.put(redisKey, new LocalEntry(value, expiresAt));
  }

  private String localGet(String redisKey) {
    LocalEntry entry = localFallback.getIfPresent(redisKey);
    if (entry == null) return null;
    if (entry.expiresAtMs() <= System.currentTimeMillis()) {
      localFallback.invalidate(redisKey);
      return null;
    }
    return entry.value();
  }

  private String redisKey(String namespace, String key) {
    String ns = sanitizeSegment(namespace, "global");
    String k = sanitizeSegment(key, "key");
    return keyPrefix + ":" + ns + ":" + k;
  }

  private String sanitizeSegment(String raw, String fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    StringBuilder out = new StringBuilder(raw.length());
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if ((c >= 'a' && c <= 'z')
        || (c >= 'A' && c <= 'Z')
        || (c >= '0' && c <= '9')
        || c == '_' || c == '-' || c == '.') {
        out.append(c);
      } else {
        out.append('_');
      }
    }
    String value = out.toString();
    return value.isBlank() ? fallback : value;
  }

  private void onRedisError(String operation, Throwable ex) {
    redisFailures.incrementAndGet();
    redisOnline.set(false);
    lastFailureAtMs.set(System.currentTimeMillis());
    String message = ex == null ? operation + "_error" : ex.getMessage();
    lastError.set(message == null || message.isBlank() ? operation + "_error" : message);
    if (logger != null) {
      logger.warning("Burst cache redis " + operation + " failed: " + lastError.get());
    }
    recordMetric("burst_cache_redis_error");
  }

  private long normalizeTtl(long ttlSeconds) {
    return ttlSeconds <= 0L ? defaultTtlSeconds : ttlSeconds;
  }

  private long parseLong(String text, long fallback) {
    if (text == null || text.isBlank()) return fallback;
    try {
      return Long.parseLong(text.trim());
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  private void recordMetric(String action) {
    if (metrics != null && action != null && !action.isBlank()) {
      metrics.recordAction(action);
    }
  }

  @Override
  public Snapshot snapshot() {
    boolean available = available() && (redisOnline.get() || redisFailures.get() == 0L);
    return new Snapshot(
      configuredEnabled,
      runtimeEnabled(),
      available,
      redactUri(redisUri),
      keyPrefix,
      defaultTtlSeconds,
      maximumLocalEntries,
      gets.get(),
      puts.get(),
      increments.get(),
      removes.get(),
      redisHits.get(),
      localHits.get(),
      redisFailures.get(),
      lastFailureAtMs.get(),
      lastError.get()
    );
  }

  @Override
  public void close() {
    if (jedisPool != null) {
      jedisPool.close();
    }
    localFallback.invalidateAll();
  }

  private String redactUri(String raw) {
    if (raw == null || raw.isBlank()) return "";
    int scheme = raw.indexOf("://");
    if (scheme < 0) return raw;
    int at = raw.indexOf('@');
    if (at <= scheme) return raw;
    return raw.substring(0, scheme + 3) + "***@" + raw.substring(at + 1);
  }

  private record LocalEntry(String value, long expiresAtMs) {}
}
