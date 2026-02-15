package net.orbis.zakum.core.storage;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import net.orbis.zakum.api.concurrent.ZakumScheduler;
import net.orbis.zakum.api.storage.DataStore;
import net.orbis.zakum.core.perf.ThreadGuard;
import org.bson.Document;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Mongo-backed profile store with Redis-backed session state.
 */
public final class MongoDataStore implements DataStore, AutoCloseable {

  private static final long DEFAULT_SESSION_TTL_SECONDS = 1_800L;
  private static final int DEFAULT_MAX_SESSION_KEY_LENGTH = 96;

  private final ZakumScheduler scheduler;
  private final MongoClient mongoClient;
  private final MongoCollection<Document> profiles;
  private final JedisPool jedisPool;
  private final ThreadGuard threadGuard;
  private final String sessionKeyPrefix;
  private final long sessionTtlSeconds;
  private final int maxSessionKeyLength;

  public MongoDataStore(
    MongoClient mongoClient,
    JedisPool jedisPool,
    String databaseName,
    ZakumScheduler scheduler,
    ThreadGuard threadGuard,
    String sessionKeyPrefix
  ) {
    this(
      mongoClient,
      jedisPool,
      databaseName,
      scheduler,
      threadGuard,
      sessionKeyPrefix,
      DEFAULT_SESSION_TTL_SECONDS,
      DEFAULT_MAX_SESSION_KEY_LENGTH
    );
  }

  public MongoDataStore(
    MongoClient mongoClient,
    JedisPool jedisPool,
    String databaseName,
    ZakumScheduler scheduler,
    ThreadGuard threadGuard,
    String sessionKeyPrefix,
    long sessionTtlSeconds,
    int maxSessionKeyLength
  ) {
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    this.mongoClient = Objects.requireNonNull(mongoClient, "mongoClient");
    this.jedisPool = Objects.requireNonNull(jedisPool, "jedisPool");
    this.threadGuard = Objects.requireNonNull(threadGuard, "threadGuard");
    String prefix = Objects.requireNonNull(sessionKeyPrefix, "sessionKeyPrefix").trim();
    this.sessionKeyPrefix = prefix.isBlank() ? "zakum:session" : prefix;
    this.sessionTtlSeconds = Math.max(60L, sessionTtlSeconds);
    this.maxSessionKeyLength = Math.max(16, Math.min(256, maxSessionKeyLength));
    this.profiles = this.mongoClient
      .getDatabase(Objects.requireNonNull(databaseName, "databaseName"))
      .getCollection("player_profiles");
  }

  @Override
  public CompletableFuture<Void> saveProfile(UUID uuid, String jsonData) {
    Objects.requireNonNull(uuid, "uuid");
    Objects.requireNonNull(jsonData, "jsonData");
    return scheduler.supplyAsync(() -> {
      threadGuard.checkAsync("mongo.saveProfile");
      Document doc = Document.parse(jsonData);
      doc.put("_id", uuid.toString());
      profiles.replaceOne(new Document("_id", uuid.toString()), doc, new ReplaceOptions().upsert(true));
      return null;
    });
  }

  @Override
  public CompletableFuture<String> loadProfile(UUID uuid) {
    Objects.requireNonNull(uuid, "uuid");
    return scheduler.supplyAsync(() -> {
      threadGuard.checkAsync("mongo.loadProfile");
      Document doc = profiles.find(new Document("_id", uuid.toString())).first();
      return doc != null ? doc.toJson() : "{}";
    });
  }

  @Override
  public void setSessionData(UUID uuid, String key, String value) {
    Objects.requireNonNull(uuid, "uuid");
    Objects.requireNonNull(key, "key");
    String safeKey = normalizeSessionKey(key);
    String redisKey = sessionKey(uuid, safeKey);
    threadGuard.checkAsync("redis.setSessionData");
    try (Jedis jedis = jedisPool.getResource()) {
      if (value == null) {
        jedis.del(redisKey);
      } else {
        jedis.setex(redisKey, (int) Math.min(Integer.MAX_VALUE, sessionTtlSeconds), value);
      }
    }
  }

  @Override
  public String getSessionData(UUID uuid, String key) {
    Objects.requireNonNull(uuid, "uuid");
    Objects.requireNonNull(key, "key");
    String safeKey = normalizeSessionKey(key);
    String redisKey = sessionKey(uuid, safeKey);
    threadGuard.checkAsync("redis.getSessionData");
    try (Jedis jedis = jedisPool.getResource()) {
      String value = jedis.get(redisKey);
      if (value != null) {
        jedis.expire(redisKey, (int) Math.min(Integer.MAX_VALUE, sessionTtlSeconds));
      }
      return value;
    }
  }

  @Override
  public void close() {
    try {
      jedisPool.close();
    } finally {
      mongoClient.close();
    }
  }

  private String sessionKey(UUID uuid, String key) {
    return sessionKeyPrefix + ":" + uuid + ":" + key;
  }

  private String normalizeSessionKey(String raw) {
    if (raw == null || raw.isBlank()) return "key";
    String trimmed = raw.trim();
    StringBuilder out = new StringBuilder(Math.min(trimmed.length(), maxSessionKeyLength));
    for (int i = 0; i < trimmed.length(); i++) {
      char c = trimmed.charAt(i);
      if ((c >= 'a' && c <= 'z')
        || (c >= 'A' && c <= 'Z')
        || (c >= '0' && c <= '9')
        || c == '_' || c == '-' || c == '.') {
        out.append(c);
      } else {
        out.append('_');
      }
      if (out.length() >= maxSessionKeyLength) {
        break;
      }
    }
    if (out.length() == 0) return "key";
    return out.toString();
  }
}
