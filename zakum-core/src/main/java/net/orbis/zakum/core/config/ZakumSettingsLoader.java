package net.orbis.zakum.core.config;

import net.orbis.zakum.api.config.ZakumSettings;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Parses config.yml into a typed ZakumSettings snapshot.
 *
 * This is intentionally strict on ranges to prevent footguns from
 * turning into long-uptime problems (e.g. infinite retries, huge caches,
 * too many HTTP requests, etc).
 */
public final class ZakumSettingsLoader {

  private ZakumSettingsLoader() {}

  public static ZakumSettings load(FileConfiguration cfg) {
    String serverId = str(cfg, "server.id", "").trim();
    if (serverId.isBlank()) serverId = "unknown";

    var db = loadDb(cfg);
    var cp = new ZakumSettings.ControlPlane(
      bool(cfg, "controlPlane.enabled", false),
      str(cfg, "controlPlane.baseUrl", "").trim(),
      str(cfg, "controlPlane.apiKey", "")
    );
    var cloud = loadCloud(cfg, serverId, cp);

    var http = loadHttp(cfg);
    var cache = loadCache(cfg);
    var obs = loadObservability(cfg);
    var ent = loadEntitlements(cfg);
    var boosters = loadBoosters(cfg);
    var actions = loadActions(cfg);
    var chat = loadChat(cfg);
    var visuals = loadVisuals(cfg);
    var packets = loadPackets(cfg);

    return new ZakumSettings(
      new ZakumSettings.Server(serverId),
      db,
      cp,
      cloud,
      http,
      cache,
      obs,
      ent,
      boosters,
      actions,
      chat,
      visuals,
      packets
    );
  }

  private static ZakumSettings.Boosters loadBoosters(FileConfiguration cfg) {
    int refreshSeconds = clampI(cfg.getInt("boosters.refreshSeconds", 60), 10, 3600);

    boolean purgeEnabled = bool(cfg, "boosters.purge.enabled", true);
    int purgeInterval = clampI(cfg.getInt("boosters.purge.intervalSeconds", 600), 60, 86_400);
    int deleteLimit = clampI(cfg.getInt("boosters.purge.deleteLimit", 5000), 100, 50_000);

    return new ZakumSettings.Boosters(
      refreshSeconds,
      new ZakumSettings.Boosters.Purge(purgeEnabled, purgeInterval, deleteLimit)
    );
  }

  private static ZakumSettings.Database loadDb(FileConfiguration cfg) {
    boolean enabled = bool(cfg, "database.enabled", true);

    String host = str(cfg, "database.host", "127.0.0.1");
    int port = clampI(cfg.getInt("database.port", 3306), 1, 65535);
    String database = str(cfg, "database.database", "zakum");
    String user = str(cfg, "database.user", "root");
    String password = str(cfg, "database.password", "");
    String params = str(cfg, "database.params", "");

    int maxPool = clampI(cfg.getInt("database.pool.maxPoolSize", 10), 1, 100);
    int minIdle = clampI(cfg.getInt("database.pool.minIdle", 2), 0, maxPool);
    long connTimeout = clampL(cfg.getLong("database.pool.connectionTimeoutMs", 5000), 250, 60_000);
    long valTimeout = clampL(cfg.getLong("database.pool.validationTimeoutMs", 2500), 250, 60_000);
    long idleTimeout = clampL(cfg.getLong("database.pool.idleTimeoutMs", 600_000), 10_000, 86_400_000);
    long maxLife = clampL(cfg.getLong("database.pool.maxLifetimeMs", 1_800_000), 30_000, 86_400_000);
    long leakMs = clampL(cfg.getLong("database.pool.leakDetectionMs", 0), 0, 600_000);

    long retry = clampL(cfg.getLong("database.failover.retrySeconds", 30), 1, 3600);

    return new ZakumSettings.Database(
      enabled,
      host, port, database, user, password, params,
      new ZakumSettings.Database.Pool(
        maxPool, minIdle, connTimeout, valTimeout, idleTimeout, maxLife, leakMs
      ),
      new ZakumSettings.Database.Failover(retry)
    );
  }

  private static ZakumSettings.Cloud loadCloud(FileConfiguration cfg, String defaultServerId, ZakumSettings.ControlPlane cp) {
    boolean enabled = bool(cfg, "cloud.enabled", false);
    String baseUrl = firstNonBlank(
      str(cfg, "cloud.baseUrl", "").trim(),
      str(cfg, "cloud.base_url", "").trim(),
      cp.baseUrl()
    );
    String networkSecret = firstNonBlank(
      str(cfg, "cloud.networkSecret", "").trim(),
      str(cfg, "cloud.network_secret", "").trim()
    );
    String serverId = firstNonBlank(
      str(cfg, "cloud.serverId", "").trim(),
      str(cfg, "cloud.server_id", "").trim(),
      defaultServerId
    );
    if (serverId.isBlank()) serverId = defaultServerId;

    int pollIntervalTicks = clampI(cfg.getInt("cloud.pollIntervalTicks", 20), 1, 20 * 60);
    long requestTimeoutMs = clampL(cfg.getLong("cloud.requestTimeoutMs", 6000), 250, 120_000);
    boolean identityOnJoin = bool(cfg, "cloud.identityOnJoin", true);

    return new ZakumSettings.Cloud(
      enabled,
      baseUrl,
      networkSecret,
      serverId,
      pollIntervalTicks,
      requestTimeoutMs,
      identityOnJoin
    );
  }

  private static ZakumSettings.Http loadHttp(FileConfiguration cfg) {
    long connect = clampL(cfg.getLong("http.connectTimeoutMs", 3000), 250, 60_000);
    long call = clampL(cfg.getLong("http.callTimeoutMs", 6000), 250, 120_000);
    long read = clampL(cfg.getLong("http.readTimeoutMs", 6000), 250, 120_000);
    long write = clampL(cfg.getLong("http.writeTimeoutMs", 6000), 250, 120_000);

    int maxReq = clampI(cfg.getInt("http.maxRequests", 256), 1, 4096);
    int maxHost = clampI(cfg.getInt("http.maxRequestsPerHost", 64), 1, 1024);

    boolean resEnabled = bool(cfg, "http.resilience.enabled", true);

    float failureRate = clampF((float) cfg.getDouble("http.resilience.circuitBreaker.failureRateThreshold", 50), 1, 100);
    float slowRate = clampF((float) cfg.getDouble("http.resilience.circuitBreaker.slowCallRateThreshold", 50), 1, 100);
    long slowMs = clampL(cfg.getLong("http.resilience.circuitBreaker.slowCallDurationMs", 1500), 1, 120_000);
    int win = clampI(cfg.getInt("http.resilience.circuitBreaker.slidingWindowSize", 50), 5, 500);
    int minCalls = clampI(cfg.getInt("http.resilience.circuitBreaker.minimumNumberOfCalls", 20), 1, win);
    long openMs = clampL(cfg.getLong("http.resilience.circuitBreaker.waitDurationInOpenStateMs", 10_000), 250, 600_000);

    int attempts = clampI(cfg.getInt("http.resilience.retry.maxAttempts", 2), 1, 6);
    long waitMs = clampL(cfg.getLong("http.resilience.retry.waitDurationMs", 150), 0, 30_000);

    return new ZakumSettings.Http(
      connect, call, read, write,
      maxReq, maxHost,
      new ZakumSettings.Http.Resilience(
        resEnabled,
        new ZakumSettings.Http.Resilience.CircuitBreaker(
          failureRate, slowRate, slowMs, win, minCalls, openMs
        ),
        new ZakumSettings.Http.Resilience.Retry(attempts, waitMs)
      )
    );
  }

  private static ZakumSettings.Cache loadCache(FileConfiguration cfg) {
    long max = clampL(cfg.getLong("cache.defaults.maximumSize", 100_000), 1000, 5_000_000);
    long eaw = clampL(cfg.getLong("cache.defaults.expireAfterWriteSeconds", 60), 0, 86_400);
    long eaa = clampL(cfg.getLong("cache.defaults.expireAfterAccessSeconds", 0), 0, 86_400);

    return new ZakumSettings.Cache(new ZakumSettings.Cache.Defaults(max, eaw, eaa));
  }

  private static ZakumSettings.Observability loadObservability(FileConfiguration cfg) {
    boolean enabled = bool(cfg, "observability.metrics.enabled", false);
    String host = str(cfg, "observability.metrics.bindHost", "127.0.0.1");
    int port = clampI(cfg.getInt("observability.metrics.port", 9100), 1, 65535);
    String path = str(cfg, "observability.metrics.path", "/metrics");
    boolean jvm = bool(cfg, "observability.metrics.includeJvm", true);

    return new ZakumSettings.Observability(
      new ZakumSettings.Observability.Metrics(enabled, host, port, path, jvm)
    );
  }

  private static ZakumSettings.Entitlements loadEntitlements(FileConfiguration cfg) {
    long max = clampL(cfg.getLong("entitlements.cache.maximumSize", 50_000), 1_000, 5_000_000);
    long ttl = clampL(cfg.getLong("entitlements.cache.ttlSeconds", 30), 1, 86_400);
    return new ZakumSettings.Entitlements(new ZakumSettings.Entitlements.CacheConfig(max, ttl));
  }

  private static ZakumSettings.Actions loadActions(FileConfiguration cfg) {
    boolean enabled = bool(cfg, "actions.enabled", true);

    boolean joinQuit = bool(cfg, "actions.emitters.joinQuit", true);
    boolean onlineTime = bool(cfg, "actions.emitters.onlineTime", true);
    boolean blockBreak = bool(cfg, "actions.emitters.blockBreak", true);
    boolean blockPlace = bool(cfg, "actions.emitters.blockPlace", true);
    boolean mobKill = bool(cfg, "actions.emitters.mobKill", true);
    boolean playerDeath = bool(cfg, "actions.emitters.playerDeath", true);
    boolean playerKill = bool(cfg, "actions.emitters.playerKill", true);
    boolean xpGain = bool(cfg, "actions.emitters.xpGain", true);
    boolean levelChange = bool(cfg, "actions.emitters.levelChange", true);
    boolean itemCraft = bool(cfg, "actions.emitters.itemCraft", true);
    boolean smeltExtract = bool(cfg, "actions.emitters.smeltExtract", true);
    boolean fishCatch = bool(cfg, "actions.emitters.fishCatch", true);
    boolean itemEnchant = bool(cfg, "actions.emitters.itemEnchant", true);
    boolean itemConsume = bool(cfg, "actions.emitters.itemConsume", true);
    boolean advancement = bool(cfg, "actions.emitters.advancement", false);

    boolean cmdEnabled = bool(cfg, "actions.emitters.commandUse.enabled", false);
    Set<String> allow = new HashSet<>();
    for (String s : cfg.getStringList("actions.emitters.commandUse.allowlist")) {
      if (s == null) continue;
      String x = s.trim().toLowerCase(Locale.ROOT);
      if (!x.isBlank()) allow.add(x);
    }

    var emitters = new ZakumSettings.Actions.Emitters(
      joinQuit, onlineTime, blockBreak, blockPlace, mobKill, playerDeath, playerKill,
      xpGain, levelChange, itemCraft, smeltExtract, fishCatch, itemEnchant, itemConsume,
      advancement,
      new ZakumSettings.Actions.Emitters.CommandUse(cmdEnabled, allow)
    );

    boolean moveEnabled = bool(cfg, "actions.movement.enabled", true);
    int ticks = clampI(cfg.getInt("actions.movement.sampleTicks", 20), 1, 200);
    long maxCm = clampL(cfg.getLong("actions.movement.maxCmPerSample", 5000), 1, 100_000);

    var movement = new ZakumSettings.Actions.Movement(moveEnabled, ticks, maxCm);

    boolean replay = bool(cfg, "actions.deferredReplay.enabled", true);
    int claim = clampI(cfg.getInt("actions.deferredReplay.claimLimit", 200), 1, 500);

    var deferred = new ZakumSettings.Actions.DeferredReplay(replay, claim);

    return new ZakumSettings.Actions(enabled, emitters, movement, deferred);
  }

  private static ZakumSettings.Chat loadChat(FileConfiguration cfg) {
    boolean enabled = bool(cfg, "chat.bufferCache.enabled", true);
    long max = clampL(cfg.getLong("chat.bufferCache.maximumSize", 100_000), 1_000, 5_000_000);
    long expireAfterAccess = clampL(cfg.getLong("chat.bufferCache.expireAfterAccessSeconds", 300), 5, 86_400);
    return new ZakumSettings.Chat(new ZakumSettings.Chat.BufferCache(enabled, max, expireAfterAccess));
  }

  private static ZakumSettings.Visuals loadVisuals(FileConfiguration cfg) {
    boolean enabled = bool(cfg, "visuals.lod.enabled", true);
    int maxPing = clampI(cfg.getInt("visuals.lod.maxPingMs", 180), 25, 2_000);
    double minTps = clampF((float) cfg.getDouble("visuals.lod.minTps", 18.5d), 5.0f, 20.0f);
    return new ZakumSettings.Visuals(new ZakumSettings.Visuals.Lod(enabled, maxPing, minTps));
  }

  private static ZakumSettings.Packets loadPackets(FileConfiguration cfg) {
    boolean enabled = bool(cfg, "packets.enabled", false);

    String backendRaw = str(cfg, "packets.backend", "NONE").trim().toUpperCase(Locale.ROOT);
    ZakumSettings.Packets.Backend backend;
    try {
      backend = ZakumSettings.Packets.Backend.valueOf(backendRaw);
    } catch (IllegalArgumentException ex) {
      backend = ZakumSettings.Packets.Backend.NONE;
    }

    boolean inbound = bool(cfg, "packets.inbound", true);
    boolean outbound = bool(cfg, "packets.outbound", true);
    int maxHooksPerPlugin = clampI(cfg.getInt("packets.maxHooksPerPlugin", 64), 0, 10_000);

    return new ZakumSettings.Packets(enabled, backend, inbound, outbound, maxHooksPerPlugin);
  }


  private static boolean bool(FileConfiguration cfg, String path, boolean def) {
    return cfg.getBoolean(path, def);
  }

  private static String str(FileConfiguration cfg, String path, String def) {
    String x = cfg.getString(path);
    return x == null ? def : x;
  }

  private static int clampI(int v, int min, int max) {
    if (v < min) return min;
    return Math.min(v, max);
  }

  private static long clampL(long v, long min, long max) {
    if (v < min) return min;
    return Math.min(v, max);
  }

  private static float clampF(float v, float min, float max) {
    if (v < min) return min;
    return Math.min(v, max);
  }

  private static String firstNonBlank(String... values) {
    if (values == null) return "";
    for (String value : values) {
      if (value != null && !value.isBlank()) return value;
    }
    return "";
  }
}
