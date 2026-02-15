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

  private final ZakumScheduler scheduler;
  private final MongoClient mongoClient;
  private final MongoCollection<Document> profiles;
  private final JedisPool jedisPool;
  private final ThreadGuard threadGuard;

  public MongoDataStore(
    MongoClient mongoClient,
    JedisPool jedisPool,
    String databaseName,
    ZakumScheduler scheduler,
    ThreadGuard threadGuard
  ) {
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    this.mongoClient = Objects.requireNonNull(mongoClient, "mongoClient");
    this.jedisPool = Objects.requireNonNull(jedisPool, "jedisPool");
    this.threadGuard = Objects.requireNonNull(threadGuard, "threadGuard");
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
    threadGuard.checkAsync("redis.setSessionData");
    try (Jedis jedis = jedisPool.getResource()) {
      if (value == null) {
        jedis.del(sessionKey(uuid, key));
      } else {
        jedis.set(sessionKey(uuid, key), value);
      }
    }
  }

  @Override
  public String getSessionData(UUID uuid, String key) {
    Objects.requireNonNull(uuid, "uuid");
    Objects.requireNonNull(key, "key");
    threadGuard.checkAsync("redis.getSessionData");
    try (Jedis jedis = jedisPool.getResource()) {
      return jedis.get(sessionKey(uuid, key));
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

  private static String sessionKey(UUID uuid, String key) {
    return "zakum:session:" + uuid + ":" + key;
  }
}
