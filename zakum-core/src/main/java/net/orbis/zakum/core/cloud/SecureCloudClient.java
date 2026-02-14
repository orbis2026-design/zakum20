package net.orbis.zakum.core.cloud;

import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.api.action.AceEngine;
import net.orbis.zakum.api.config.ZakumSettings;
import net.orbis.zakum.core.metrics.MetricsMonitor;
import org.bson.BsonArray;
import org.bson.BsonValue;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

public final class SecureCloudClient {

  private static final String EMPTY_BODY = "";

  private final ZakumApi api;
  private final Logger logger;
  private final String baseUrl;
  private final String serverId;
  private final String networkSecret;
  private final Duration requestTimeout;
  private final HttpClient httpClient;
  private final MetricsMonitor metrics;
  private final AtomicLong lastPollAttemptMs;
  private final AtomicLong lastPollSuccessMs;
  private final AtomicInteger lastHttpStatus;
  private final AtomicLong lastBatchSize;
  private final AtomicLong totalQueueActions;
  private final AtomicReference<String> lastError;

  public SecureCloudClient(ZakumApi api, ZakumSettings.Cloud cloud, Logger logger, MetricsMonitor metrics) {
    this.api = api;
    this.logger = logger;
    this.baseUrl = normalizeBaseUrl(cloud.baseUrl());
    this.serverId = cloud.serverId();
    this.networkSecret = cloud.networkSecret();
    this.requestTimeout = Duration.ofMillis(cloud.requestTimeoutMs());
    this.metrics = metrics;
    this.lastPollAttemptMs = new AtomicLong(0L);
    this.lastPollSuccessMs = new AtomicLong(0L);
    this.lastHttpStatus = new AtomicInteger(0);
    this.lastBatchSize = new AtomicLong(0L);
    this.totalQueueActions = new AtomicLong(0L);
    this.lastError = new AtomicReference<>("");
    this.httpClient = HttpClient.newBuilder()
      .executor(api.getScheduler().asyncExecutor())
      .connectTimeout(this.requestTimeout)
      .build();
  }

  public void poll() {
    if (baseUrl.isBlank() || networkSecret == null || networkSecret.isBlank()) return;
    lastPollAttemptMs.set(System.currentTimeMillis());
    String encodedServerId = encodePath(serverId);
    HttpRequest request = signedGet("/v1/agent/queue/" + encodedServerId);
    httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
      .thenAccept(response -> {
        lastHttpStatus.set(response.statusCode());
        record("cloud_poll");
        if (response.statusCode() == 204) {
          lastPollSuccessMs.set(System.currentTimeMillis());
          lastBatchSize.set(0L);
          lastError.set("");
          return;
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
          lastError.set("HTTP " + response.statusCode());
          logger.fine("Cloud queue poll returned status=" + response.statusCode());
          return;
        }
        List<Document> docs = parseQueueDocuments(response.body());
        lastPollSuccessMs.set(System.currentTimeMillis());
        lastBatchSize.set(docs.size());
        lastError.set("");
        for (Document document : docs) {
          applyQueueEntry(document);
        }
      })
      .exceptionally(ex -> {
        lastError.set(ex.getMessage() == null ? "poll_error" : ex.getMessage());
        logger.warning("Cloud queue poll failed: " + ex.getMessage());
        return null;
      });
  }

  public CompletableFuture<IdentitySnapshot> fetchIdentity(UUID uuid) {
    if (uuid == null) return CompletableFuture.completedFuture(IdentitySnapshot.EMPTY);
    if (baseUrl.isBlank() || networkSecret == null || networkSecret.isBlank()) {
      return CompletableFuture.completedFuture(IdentitySnapshot.EMPTY);
    }

    HttpRequest request = signedGet("/v1/identity/" + encodePath(uuid.toString()));
    return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
      .handle((response, error) -> {
        if (error != null || response == null) {
          if (error != null) logger.fine("Cloud identity fetch failed: " + error.getMessage());
          return IdentitySnapshot.EMPTY;
        }
        record("cloud_identity_fetch");
        if (response.statusCode() < 200 || response.statusCode() >= 300) return IdentitySnapshot.EMPTY;
        return parseIdentity(response.body());
      });
  }

  private void applyQueueEntry(Document document) {
    if (document == null || document.isEmpty()) return;

    Player player = resolvePlayer(document);
    if (player == null || !player.isOnline()) return;

    List<String> script = parseScript(document.get("script"));
    if (script.isEmpty()) script = parseScript(document.get("ace_script"));
    if (script.isEmpty()) return;

    Map<String, Object> metadata = new HashMap<>();
    copyObject(document.get("metadata"), metadata);
    copyObject(document.get("context"), metadata);
    copyValue(document, "id", metadata, "cloud_id");
    copyValue(document, "type", metadata, "cloud_type");

    List<String> finalScript = script;
    api.getScheduler().runAtEntity(player, () -> {
      if (!player.isOnline()) return;
      api.getAceEngine().executeScript(finalScript, new AceEngine.ActionContext(player, Optional.empty(), metadata));
      totalQueueActions.incrementAndGet();
      record("cloud_queue_ace");
    });
  }

  private Player resolvePlayer(Document document) {
    for (String key : List.of("player_uuid", "uuid", "playerUuid", "playerUUID")) {
      UUID uuid = parseUuid(document.get(key));
      if (uuid == null) continue;
      Player player = Bukkit.getPlayer(uuid);
      if (player != null) return player;
    }
    for (String key : List.of("player", "player_name", "name", "username")) {
      String name = asString(document.get(key));
      if (name == null || name.isBlank()) continue;
      Player exact = Bukkit.getPlayerExact(name);
      if (exact != null) return exact;
      Player fuzzy = Bukkit.getPlayer(name);
      if (fuzzy != null) return fuzzy;
    }
    return null;
  }

  private List<Document> parseQueueDocuments(String json) {
    if (json == null || json.isBlank()) return List.of();
    String trimmed = json.trim();
    List<Document> out = new ArrayList<>();

    try {
      if (trimmed.startsWith("[")) {
        BsonArray arr = BsonArray.parse(trimmed);
        for (BsonValue value : arr) {
          Document doc = toDocument(value);
          if (doc != null) out.add(doc);
        }
        return out;
      }

      Document root = Document.parse(trimmed);
      for (String key : List.of("items", "queue", "data")) {
        Object list = root.get(key);
        if (list instanceof Iterable<?> iterable) {
          for (Object entry : iterable) {
            Document doc = toDocument(entry);
            if (doc != null) out.add(doc);
          }
          if (!out.isEmpty()) return out;
        }
      }
      if (!root.isEmpty()) out.add(root);
    } catch (Throwable ex) {
      logger.fine("Cloud queue parse failed: " + ex.getMessage());
    }
    return out;
  }

  private IdentitySnapshot parseIdentity(String body) {
    if (body == null || body.isBlank()) return IdentitySnapshot.EMPTY;
    try {
      Document doc = Document.parse(body);
      String rank = asString(doc.get("rank"));
      if (rank == null || rank.isBlank()) rank = "DEFAULT";
      boolean discordLinked = parseBoolean(doc.get("discord_linked"), false);
      String discordId = firstNonBlank(asString(doc.get("discord_id")), asString(doc.get("discordId")));
      Set<UUID> friends = parseUuidSet(doc.get("friends"));
      Set<UUID> allies = parseUuidSet(firstNonNull(doc.get("allies"), doc.get("alliance")));
      Set<UUID> rivals = parseUuidSet(doc.get("rivals"));
      return new IdentitySnapshot(rank, discordLinked, discordId, friends, allies, rivals);
    } catch (Throwable ex) {
      logger.fine("Cloud identity parse failed: " + ex.getMessage());
      return IdentitySnapshot.EMPTY;
    }
  }

  private HttpRequest signedGet(String path) {
    long ts = System.currentTimeMillis();
    String nonce = Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), 16);
    String signature = generateSignature(ts + nonce + EMPTY_BODY);

    return HttpRequest.newBuilder()
      .uri(resolveUri(path))
      .timeout(requestTimeout)
      .header("Accept", "application/json")
      .header("X-Orbis-Signature", signature)
      .header("X-Orbis-TS", String.valueOf(ts))
      .header("X-Orbis-Nonce", nonce)
      .GET()
      .build();
  }

  private String generateSignature(String data) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(networkSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      logger.warning("Failed to generate cloud signature: " + ex.getMessage());
      return "";
    }
  }

  private URI resolveUri(String path) {
    String normalizedPath = path == null ? "" : path.trim();
    if (normalizedPath.startsWith("http://") || normalizedPath.startsWith("https://")) {
      return URI.create(normalizedPath);
    }
    if (!normalizedPath.startsWith("/")) normalizedPath = "/" + normalizedPath;
    return URI.create(baseUrl + normalizedPath);
  }

  private static String normalizeBaseUrl(String raw) {
    if (raw == null) return "";
    String trimmed = raw.trim();
    if (trimmed.endsWith("/")) {
      return trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed;
  }

  private static Document toDocument(Object value) {
    if (value == null) return null;
    if (value instanceof Document document) return document;
    if (value instanceof BsonValue bsonValue) {
      if (!bsonValue.isDocument()) return null;
      return Document.parse(bsonValue.asDocument().toJson());
    }
    if (value instanceof Map<?, ?> map) {
      Document out = new Document();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (entry.getKey() == null) continue;
        out.put(String.valueOf(entry.getKey()), entry.getValue());
      }
      return out;
    }
    if (value instanceof String json) {
      String trimmed = json.trim();
      if (trimmed.startsWith("{")) return Document.parse(trimmed);
    }
    return null;
  }

  private static List<String> parseScript(Object value) {
    List<String> out = new ArrayList<>();
    if (value == null) return out;
    if (value instanceof Iterable<?> iterable) {
      for (Object line : iterable) {
        if (line == null) continue;
        String text = String.valueOf(line).trim();
        if (!text.isBlank()) out.add(text);
      }
      return out;
    }
    String raw = String.valueOf(value).trim();
    if (raw.isBlank()) return out;
    if (raw.startsWith("[")) {
      try {
        BsonArray arr = BsonArray.parse(raw);
        for (BsonValue line : arr) {
          String text = line.isString() ? line.asString().getValue() : line.toString();
          if (text != null && !text.isBlank()) out.add(text.trim());
        }
        return out;
      } catch (Throwable ignored) {}
    }
    if (raw.contains("\n")) {
      for (String line : raw.split("\\R")) {
        String text = line.trim();
        if (!text.isBlank()) out.add(text);
      }
      return out;
    }
    out.add(raw);
    return out;
  }

  private static UUID parseUuid(Object value) {
    if (value == null) return null;
    try {
      return UUID.fromString(String.valueOf(value).trim());
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private static Set<UUID> parseUuidSet(Object value) {
    if (value == null) return Set.of();
    LinkedHashSet<UUID> out = new LinkedHashSet<>();

    if (value instanceof Iterable<?> iterable) {
      for (Object item : iterable) {
        UUID uuid = parseUuid(item);
        if (uuid != null) out.add(uuid);
      }
      return out;
    }

    String raw = String.valueOf(value).trim();
    if (raw.isBlank()) return Set.of();
    for (String token : raw.split("[,;\\s]+")) {
      UUID uuid = parseUuid(token);
      if (uuid != null) out.add(uuid);
    }
    return out;
  }

  private static String asString(Object value) {
    if (value == null) return null;
    return String.valueOf(value);
  }

  private static boolean parseBoolean(Object value, boolean fallback) {
    if (value == null) return fallback;
    if (value instanceof Boolean b) return b;
    if (value instanceof Number n) return n.intValue() != 0;
    String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
    if (text.isBlank()) return fallback;
    if (text.equals("true") || text.equals("1") || text.equals("yes")) return true;
    if (text.equals("false") || text.equals("0") || text.equals("no")) return false;
    return fallback;
  }

  private static String encodePath(String value) {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private static void copyObject(Object source, Map<String, Object> metadata) {
    if (source == null) return;
    Document document = toDocument(source);
    if (document == null) return;
    metadata.putAll(document);
  }

  private static void copyValue(Document doc, String sourceKey, Map<String, Object> metadata, String targetKey) {
    Object value = doc.get(sourceKey);
    if (value == null) return;
    metadata.put(targetKey, value);
  }

  private void record(String actionType) {
    if (metrics != null) {
      metrics.recordAction(actionType);
    }
  }

  private static String firstNonBlank(String a, String b) {
    if (a != null && !a.isBlank()) return a;
    return (b != null && !b.isBlank()) ? b : null;
  }

  private static Object firstNonNull(Object a, Object b) {
    return a != null ? a : b;
  }

  public record IdentitySnapshot(String rank, boolean discordLinked, String discordId,
                                 Set<UUID> friends, Set<UUID> allies, Set<UUID> rivals) {
    public static final IdentitySnapshot EMPTY = new IdentitySnapshot("DEFAULT", false, null, Set.of(), Set.of(), Set.of());

    public IdentitySnapshot {
      friends = friends == null ? Set.of() : Set.copyOf(friends);
      allies = allies == null ? Set.of() : Set.copyOf(allies);
      rivals = rivals == null ? Set.of() : Set.copyOf(rivals);
    }
  }

  public CloudStatusSnapshot statusSnapshot() {
    return new CloudStatusSnapshot(
      serverId,
      baseUrl,
      lastPollAttemptMs.get(),
      lastPollSuccessMs.get(),
      lastHttpStatus.get(),
      lastBatchSize.get(),
      totalQueueActions.get(),
      lastError.get()
    );
  }

  public record CloudStatusSnapshot(
    String serverId,
    String baseUrl,
    long lastPollAttemptMs,
    long lastPollSuccessMs,
    int lastHttpStatus,
    long lastBatchSize,
    long totalQueueActions,
    String lastError
  ) {}
}
