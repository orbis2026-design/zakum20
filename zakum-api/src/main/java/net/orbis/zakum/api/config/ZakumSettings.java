package net.orbis.zakum.api.config;

import java.util.Map;
import java.util.Set;

/**
 * Central, typed configuration snapshot for the Zakum runtime.
 *
 * NOTE:
 * - This is an immutable snapshot (reload replaces the whole object).
 * - Everything here is validated/clamped in the loader.
 *
 * Categories mirror the config.yml structure.
 */
public record ZakumSettings(
  Server server,
  Database database,
  ControlPlane controlPlane,
  Cloud cloud,
  Http http,
  Cache cache,
  Observability observability,
  Entitlements entitlements,
  Boosters boosters,
  Actions actions,
  Operations operations,
  Chat chat,
  Visuals visuals,
  Packets packets
) {

  public record Server(
    String id
  ) {}

  public record Database(
    boolean enabled,
    String host,
    int port,
    String database,
    String user,
    String password,
    String params,
    Pool pool,
    Failover failover
  ) {
    public record Pool(
      int maxPoolSize,
      int minIdle,
      long connectionTimeoutMs,
      long validationTimeoutMs,
      long idleTimeoutMs,
      long maxLifetimeMs,
      long leakDetectionMs
    ) {}

    public record Failover(
      long retrySeconds
    ) {}
  }

  public record ControlPlane(
    boolean enabled,
    String baseUrl,
    String apiKey
  ) {}

  public record Cloud(
    boolean enabled,
    String baseUrl,
    String networkSecret,
    String serverId,
    int pollIntervalTicks,
    long requestTimeoutMs,
    boolean identityOnJoin
  ) {}

  public record Http(
    long connectTimeoutMs,
    long callTimeoutMs,
    long readTimeoutMs,
    long writeTimeoutMs,
    int maxRequests,
    int maxRequestsPerHost,
    Resilience resilience
  ) {
    public record Resilience(
      boolean enabled,
      CircuitBreaker circuitBreaker,
      Retry retry
    ) {
      public record CircuitBreaker(
        float failureRateThreshold,
        float slowCallRateThreshold,
        long slowCallDurationMs,
        int slidingWindowSize,
        int minimumNumberOfCalls,
        long waitDurationInOpenStateMs
      ) {}

      public record Retry(
        int maxAttempts,
        long waitDurationMs
      ) {}
    }
  }

  public record Cache(
    Defaults defaults
  ) {
    public record Defaults(
      long maximumSize,
      long expireAfterWriteSeconds,
      long expireAfterAccessSeconds
    ) {}
  }

  public record Observability(
    Metrics metrics
  ) {
    public record Metrics(
      boolean enabled,
      String bindHost,
      int port,
      String path,
      boolean includeJvm
    ) {}
  }

  public record Entitlements(
    CacheConfig cache
  ) {
    public record CacheConfig(
      long maximumSize,
      long ttlSeconds
    ) {}
  }

  public record Boosters(
    int refreshSeconds,
    Purge purge
  ) {
    public record Purge(
      boolean enabled,
      int intervalSeconds,
      int deleteLimit
    ) {}
  }

  public record Operations(
    CircuitBreaker circuitBreaker,
    Stress stress
  ) {
    public record CircuitBreaker(
      boolean enabled,
      double disableBelowTps,
      double resumeAboveTps,
      int sampleTicks,
      int stableSamplesToClose
    ) {}

    public record Stress(
      boolean enabled,
      int defaultIterations,
      int maxIterations,
      int cooldownSeconds
    ) {}
  }

  public record Chat(
    BufferCache bufferCache,
    Localization localization
  ) {
    public record BufferCache(
      boolean enabled,
      long maximumSize,
      long expireAfterAccessSeconds
    ) {}

    public record Localization(
      boolean enabled,
      String defaultLocale,
      Set<String> supportedLocales,
      long preparedMaximumSize,
      boolean warmupOnStart,
      Map<String, Map<String, String>> templates
    ) {
      public Localization {
        supportedLocales = supportedLocales == null ? Set.of() : Set.copyOf(supportedLocales);
        templates = copyTemplates(templates);
      }

      private static Map<String, Map<String, String>> copyTemplates(Map<String, Map<String, String>> input) {
        if (input == null || input.isEmpty()) return Map.of();
        java.util.LinkedHashMap<String, Map<String, String>> out = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : input.entrySet()) {
          String key = entry.getKey();
          if (key == null || key.isBlank()) continue;

          Map<String, String> locales = entry.getValue();
          java.util.LinkedHashMap<String, String> localized = new java.util.LinkedHashMap<>();
          if (locales != null) {
            for (Map.Entry<String, String> locEntry : locales.entrySet()) {
              String locale = locEntry.getKey();
              String text = locEntry.getValue();
              if (locale == null || locale.isBlank()) continue;
              if (text == null || text.isBlank()) continue;
              localized.put(locale, text);
            }
          }
          out.put(key, Map.copyOf(localized));
        }
        return Map.copyOf(out);
      }
    }
  }

  public record Visuals(
    Lod lod
  ) {
    public record Lod(
      boolean enabled,
      int maxPingMs,
      double minTps
    ) {}
  }


  public record Packets(
    boolean enabled,
    Backend backend,
    boolean inbound,
    boolean outbound,
    int maxHooksPerPlugin
  ) {
    public enum Backend {
      NONE,
      PACKETEVENTS
    }
  }
  public record Actions(
    boolean enabled,
    Emitters emitters,
    Movement movement,
    DeferredReplay deferredReplay
  ) {
    public record Emitters(
      boolean joinQuit,
      boolean onlineTime,
      boolean blockBreak,
      boolean blockPlace,
      boolean mobKill,
      boolean playerDeath,
      boolean playerKill,
      boolean xpGain,
      boolean levelChange,
      boolean itemCraft,
      boolean smeltExtract,
      boolean fishCatch,
      boolean itemEnchant,
      boolean itemConsume,
      boolean advancement,
      CommandUse commandUse
    ) {
      public record CommandUse(
        boolean enabled,
        Set<String> allowlist
      ) {}
    }

    public record Movement(
      boolean enabled,
      int sampleTicks,
      long maxCmPerSample
    ) {}

    public record DeferredReplay(
      boolean enabled,
      int claimLimit
    ) {}
  }
}
