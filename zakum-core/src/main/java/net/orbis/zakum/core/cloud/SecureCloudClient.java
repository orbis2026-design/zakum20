package net.orbis.zakum.core.cloud;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.api.action.AceEngine;
import net.orbis.zakum.api.concurrent.ZakumScheduler;
import net.orbis.zakum.api.config.ZakumSettings;
import net.orbis.zakum.core.metrics.MetricsMonitor;
import org.bson.BsonArray;
import org.bson.BsonValue;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

public final class SecureCloudClient implements AutoCloseable {

  private static final String EMPTY_BODY = "";

  private final ZakumApi api;
  private final Plugin plugin;
  private final ZakumScheduler scheduler;
  private final Logger logger;
  private final String baseUrl;
  private final String serverId;
  private final String networkSecret;
  private final Duration requestTimeout;
  private final boolean ackEnabled;
  private final String ackPath;
  private final int ackBatchSize;
  private final int ackFlushSeconds;
  private final int ackMaxAttempts;
  private final long dedupeTtlSeconds;
  private final long inflightTtlSeconds;
  private final int maxFailureAttempts;
  private final HttpClient httpClient;
  private final MetricsMonitor metrics;
  private final AtomicLong lastPollAttemptMs;
  private final AtomicLong lastPollSuccessMs;
  private final AtomicInteger lastHttpStatus;
  private final AtomicLong lastBatchSize;
  private final AtomicLong totalQueueActions;
  private final AtomicLong duplicateQueueSkips;
  private final AtomicLong inflightQueueSkips;
  private final AtomicLong offlineQueueSkips;
  private final AtomicLong invalidQueueSkips;
  private final AtomicLong queueFailures;
  private final AtomicReference<String> lastError;
  private final Cache<String, Long> processedQueueIds;
  private final Cache<String, Boolean> inflightQueueIds;
  private final Cache<String, Integer> failureCounts;
  private final ConcurrentLinkedQueue<QueueAck> pendingAcks;
  private final AtomicBoolean ackFlushRunning;
  private final AtomicLong ackQueued;
  private final AtomicLong ackSent;
  private final AtomicLong ackFailed;
  private final AtomicLong ackDropped;
  private final AtomicLong ackRetried;
  private final AtomicLong lastAckAttemptMs;
  private final AtomicLong lastAckSuccessMs;
  private final AtomicInteger lastAckStatus;
  private final AtomicReference<String> lastAckError;
  private final boolean dedupePersistEnabled;
  private final String dedupePersistFile;
  private final int dedupePersistFlushSeconds;
  private final AtomicLong lastDedupeLoadMs;
  private final AtomicLong lastDedupePersistMs;
  private final AtomicLong dedupePersistErrors;
  private final AtomicReference<String> lastDedupePersistError;
  private volatile boolean ackDisabled;
  private volatile int ackTaskId;
  private volatile int dedupePersistTaskId;

  public SecureCloudClient(ZakumApi api, ZakumSettings.Cloud cloud, Plugin plugin, Logger logger, MetricsMonitor metrics) {
    this.api = api;
    this.plugin = plugin;
    this.scheduler = api.getScheduler();
    this.logger = logger;
    this.baseUrl = normalizeBaseUrl(cloud.baseUrl());
    this.serverId = cloud.serverId();
    this.networkSecret = cloud.networkSecret();
    this.requestTimeout = Duration.ofMillis(cloud.requestTimeoutMs());
    this.ackEnabled = cloud.ackEnabled();
    this.ackPath = cloud.ackPath();
    this.ackBatchSize = Math.max(1, cloud.ackBatchSize());
    this.ackFlushSeconds = Math.max(1, cloud.ackFlushSeconds());
    this.ackMaxAttempts = Math.max(1, cloud.ackMaxAttempts());
    this.dedupeTtlSeconds = Math.max(10L, cloud.dedupeTtlSeconds());
    this.inflightTtlSeconds = Math.max(5L, cloud.inflightTtlSeconds());
    this.maxFailureAttempts = Math.max(0, cloud.maxFailureAttempts());
    this.dedupePersistEnabled = cloud.dedupeEnabled() && cloud.dedupePersistEnabled();
    this.dedupePersistFile = cloud.dedupePersistFile() == null ? "cloud-dedupe.yml" : cloud.dedupePersistFile().trim();
    this.dedupePersistFlushSeconds = Math.max(5, cloud.dedupePersistFlushSeconds());
    this.metrics = metrics;
    this.lastPollAttemptMs = new AtomicLong(0L);
    this.lastPollSuccessMs = new AtomicLong(0L);
    this.lastHttpStatus = new AtomicInteger(0);
    this.lastBatchSize = new AtomicLong(0L);
    this.totalQueueActions = new AtomicLong(0L);
    this.duplicateQueueSkips = new AtomicLong(0L);
    this.inflightQueueSkips = new AtomicLong(0L);
    this.offlineQueueSkips = new AtomicLong(0L);
    this.invalidQueueSkips = new AtomicLong(0L);
    this.queueFailures = new AtomicLong(0L);
    this.lastError = new AtomicReference<>("");
    this.processedQueueIds = cloud.dedupeEnabled()
      ? Caffeine.newBuilder()
      .maximumSize(Math.max(100L, cloud.dedupeMaximumSize()))
      .expireAfterWrite(Duration.ofSeconds(Math.max(10L, cloud.dedupeTtlSeconds())))
      .build()
      : null;
    this.inflightQueueIds = cloud.dedupeEnabled()
      ? Caffeine.newBuilder()
      .maximumSize(Math.max(100L, cloud.dedupeMaximumSize()))
      .expireAfterWrite(Duration.ofSeconds(inflightTtlSeconds))
      .build()
      : null;
    this.failureCounts = cloud.dedupeEnabled() && maxFailureAttempts > 0
      ? Caffeine.newBuilder()
      .maximumSize(Math.max(100L, cloud.dedupeMaximumSize()))
      .expireAfterWrite(Duration.ofSeconds(Math.max(30L, cloud.dedupeTtlSeconds())))
      .build()
      : null;
    this.pendingAcks = new ConcurrentLinkedQueue<>();
    this.ackFlushRunning = new AtomicBoolean(false);
    this.ackQueued = new AtomicLong(0L);
    this.ackSent = new AtomicLong(0L);
    this.ackFailed = new AtomicLong(0L);
    this.ackDropped = new AtomicLong(0L);
    this.ackRetried = new AtomicLong(0L);
    this.lastAckAttemptMs = new AtomicLong(0L);
    this.lastAckSuccessMs = new AtomicLong(0L);
    this.lastAckStatus = new AtomicInteger(0);
    this.lastAckError = new AtomicReference<>("");
    this.lastDedupeLoadMs = new AtomicLong(0L);
    this.lastDedupePersistMs = new AtomicLong(0L);
    this.dedupePersistErrors = new AtomicLong(0L);
    this.lastDedupePersistError = new AtomicReference<>("");
    this.ackDisabled = false;
    this.ackTaskId = -1;
    this.dedupePersistTaskId = -1;
    this.httpClient = HttpClient.newBuilder()
      .executor(api.getScheduler().asyncExecutor())
      .connectTimeout(this.requestTimeout)
      .build();

    if (dedupePersistEnabled) {
      loadPersistedDedupe();
    }
  }

  public void start() {
    if (plugin == null || scheduler == null) return;
    if (ackEnabled && ackTaskId <= 0) {
      long ticks = Math.max(1L, ackFlushSeconds) * 20L;
      ackTaskId = scheduler.runTaskTimer(plugin, this::flushAcks, ticks, ticks);
    }
    if (dedupePersistEnabled && processedQueueIds != null && dedupePersistTaskId <= 0) {
      long ticks = Math.max(5L, dedupePersistFlushSeconds) * 20L;
      dedupePersistTaskId = scheduler.runTaskTimerAsynchronously(plugin, this::flushPersistedDedupe, ticks, ticks);
    }
  }

  @Override
  public void close() {
    if (ackTaskId > 0 && scheduler != null) {
      scheduler.cancelTask(ackTaskId);
    }
    ackTaskId = -1;
    if (dedupePersistTaskId > 0 && scheduler != null) {
      scheduler.cancelTask(dedupePersistTaskId);
    }
    dedupePersistTaskId = -1;
    flushPersistedDedupe();
    pendingAcks.clear();
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

  public boolean requestAckFlush() {
    if (!ackEnabled || ackDisabled || scheduler == null) return false;
    scheduler.runAsync(this::flushAcks);
    return true;
  }

  public boolean requestDedupePersist() {
    if (!dedupePersistEnabled || processedQueueIds == null || scheduler == null) return false;
    scheduler.runAsync(this::flushPersistedDedupe);
    return true;
  }

  private void flushAcks() {
    if (!ackEnabled || ackDisabled) return;
    if (baseUrl.isBlank() || networkSecret == null || networkSecret.isBlank()) return;
    if (!ackFlushRunning.compareAndSet(false, true)) return;
    if (ackPath == null || ackPath.isBlank()) {
      ackDisabled = true;
      ackFlushRunning.set(false);
      lastAckError.set("ack_path_blank");
      return;
    }

    List<QueueAck> batch = new ArrayList<>();
    for (int i = 0; i < ackBatchSize; i++) {
      QueueAck ack = pendingAcks.poll();
      if (ack == null) break;
      batch.add(ack);
    }
    if (batch.isEmpty()) {
      ackFlushRunning.set(false);
      return;
    }

    String payload = buildAckPayload(batch);
    if (payload == null || payload.isBlank()) {
      ackFlushRunning.set(false);
      return;
    }

    long now = System.currentTimeMillis();
    lastAckAttemptMs.set(now);
    HttpRequest request = signedPost(resolveAckPath(), payload);
    httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
      .handle((response, error) -> {
        if (error != null || response == null) {
          lastAckError.set(error == null ? "ack_error" : error.getMessage());
          lastAckStatus.set(0);
          record("cloud_ack_error");
          requeueAcks(batch, "exception");
          return null;
        }

        int status = response.statusCode();
        lastAckStatus.set(status);
        if (status >= 200 && status < 300) {
          lastAckSuccessMs.set(System.currentTimeMillis());
          ackSent.addAndGet(batch.size());
          record("cloud_ack_sent");
          return null;
        }

        lastAckError.set("HTTP " + status);
        if (status == 404 || status == 501) {
          ackDisabled = true;
          ackFailed.addAndGet(batch.size());
          record("cloud_ack_disabled");
          pendingAcks.clear();
          return null;
        }

        ackFailed.addAndGet(batch.size());
        record("cloud_ack_failed");
        requeueAcks(batch, "http_" + status);
        return null;
      })
      .whenComplete((response, error) -> ackFlushRunning.set(false));
  }

  private File resolveDedupeFile() {
    String file = dedupePersistFile == null ? "" : dedupePersistFile.trim();
    if (file.isBlank()) file = "cloud-dedupe.yml";
    if (plugin == null) return new File(file);
    return new File(plugin.getDataFolder(), file);
  }

  private void loadPersistedDedupe() {
    if (!dedupePersistEnabled || processedQueueIds == null) return;
    File file = resolveDedupeFile();
    if (file == null || !file.exists()) return;
    long now = System.currentTimeMillis();
    long ttlMs = Math.max(1L, dedupeTtlSeconds) * 1000L;

    try {
      YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
      java.util.List<java.util.Map<?, ?>> entries = yaml.getMapList("processed");
      if (entries != null) {
        for (java.util.Map<?, ?> entry : entries) {
          if (entry == null) continue;
          String id = stringValue(entry.get("id"));
          if (id == null || id.isBlank()) continue;
          long ts = longValue(entry.get("ts"), 0L);
          if (ts <= 0L) continue;
          if ((now - ts) > ttlMs) continue;
          processedQueueIds.put(id, ts);
        }
      }
      lastDedupeLoadMs.set(now);
      if (metrics != null) metrics.recordAction("cloud_dedupe_persist_load");
    } catch (Throwable ex) {
      dedupePersistErrors.incrementAndGet();
      lastDedupePersistError.set(ex.getMessage() == null ? "dedupe_load_error" : ex.getMessage());
      if (metrics != null) metrics.recordAction("cloud_dedupe_persist_error");
    }
  }

  private void flushPersistedDedupe() {
    if (!dedupePersistEnabled || processedQueueIds == null) return;
    File file = resolveDedupeFile();
    if (file == null) return;
    File parent = file.getParentFile();
    if (parent != null && !parent.exists()) {
      parent.mkdirs();
    }

    YamlConfiguration yaml = new YamlConfiguration();
    java.util.List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
    for (java.util.Map.Entry<String, Long> entry : processedQueueIds.asMap().entrySet()) {
      if (entry == null) continue;
      String id = entry.getKey();
      Long ts = entry.getValue();
      if (id == null || id.isBlank() || ts == null) continue;
      java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
      row.put("id", id);
      row.put("ts", ts);
      rows.add(row);
    }
    yaml.set("savedAtMs", System.currentTimeMillis());
    yaml.set("processed", rows);

    try {
      yaml.save(file);
      lastDedupePersistMs.set(System.currentTimeMillis());
      lastDedupePersistError.set("");
      if (metrics != null) metrics.recordAction("cloud_dedupe_persist_save");
    } catch (Exception ex) {
      dedupePersistErrors.incrementAndGet();
      lastDedupePersistError.set(ex.getMessage() == null ? "dedupe_save_error" : ex.getMessage());
      if (metrics != null) metrics.recordAction("cloud_dedupe_persist_error");
    }
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

    String queueId = extractQueueId(document);
    if (queueId != null) {
      if (isProcessed(queueId)) {
        duplicateQueueSkips.incrementAndGet();
        record("cloud_queue_duplicate");
        enqueueAck(queueId, "duplicate", "processed");
        return;
      }
      if (isInflight(queueId)) {
        inflightQueueSkips.incrementAndGet();
        record("cloud_queue_inflight");
        return;
      }
    }

    Player player = resolvePlayer(document);
    if (player == null || !player.isOnline()) {
      offlineQueueSkips.incrementAndGet();
      record("cloud_queue_offline");
      return;
    }

    List<String> script = parseScript(document.get("script"));
    if (script.isEmpty()) script = parseScript(document.get("ace_script"));
    if (script.isEmpty()) {
      invalidQueueSkips.incrementAndGet();
      record("cloud_queue_invalid");
      markProcessed(queueId);
      enqueueAck(queueId, "invalid", "empty_script");
      return;
    }

    Map<String, Object> metadata = new HashMap<>();
    copyObject(document.get("metadata"), metadata);
    copyObject(document.get("context"), metadata);
    copyValue(document, "id", metadata, "cloud_id");
    copyValue(document, "type", metadata, "cloud_type");

    if (queueId != null) {
      markInflight(queueId);
    }
    List<String> finalScript = script;
    api.getScheduler().runAtEntity(player, () -> {
      if (!player.isOnline()) {
        offlineQueueSkips.incrementAndGet();
        record("cloud_queue_offline");
        clearInflight(queueId);
        return;
      }
      try {
        api.getAceEngine().executeScript(finalScript, new AceEngine.ActionContext(player, Optional.empty(), metadata));
        totalQueueActions.incrementAndGet();
        record("cloud_queue_ace");
        markProcessed(queueId);
        clearInflight(queueId);
        enqueueAck(queueId, "applied", null);
      } catch (Throwable ex) {
        queueFailures.incrementAndGet();
        record("cloud_queue_failure");
        clearInflight(queueId);
        boolean terminal = shouldAckFailure(queueId);
        if (terminal) {
          markProcessed(queueId);
          enqueueAck(queueId, "failed", ex.getMessage());
        }
      }
    });
  }

  private boolean isProcessed(String queueId) {
    if (queueId == null || queueId.isBlank() || processedQueueIds == null) return false;
    return processedQueueIds.getIfPresent(queueId) != null;
  }

  private boolean isInflight(String queueId) {
    if (queueId == null || queueId.isBlank() || inflightQueueIds == null) return false;
    return inflightQueueIds.getIfPresent(queueId) != null;
  }

  private void markProcessed(String queueId) {
    if (queueId == null || queueId.isBlank() || processedQueueIds == null) return;
    processedQueueIds.put(queueId, System.currentTimeMillis());
    if (failureCounts != null) {
      failureCounts.invalidate(queueId);
    }
  }

  private void markInflight(String queueId) {
    if (queueId == null || queueId.isBlank() || inflightQueueIds == null) return;
    inflightQueueIds.put(queueId, Boolean.TRUE);
  }

  private void clearInflight(String queueId) {
    if (queueId == null || queueId.isBlank() || inflightQueueIds == null) return;
    inflightQueueIds.invalidate(queueId);
  }

  private boolean shouldAckFailure(String queueId) {
    if (queueId == null || queueId.isBlank()) return false;
    if (maxFailureAttempts <= 0 || failureCounts == null) return false;

    Integer current = failureCounts.getIfPresent(queueId);
    int next = current == null ? 1 : current + 1;
    if (next >= maxFailureAttempts) {
      failureCounts.invalidate(queueId);
      return true;
    }
    failureCounts.put(queueId, next);
    return false;
  }

  private void enqueueAck(String queueId, String status, String reason) {
    if (!ackEnabled || ackDisabled) return;
    if (queueId == null || queueId.isBlank()) return;
    String finalStatus = status == null || status.isBlank() ? "applied" : status.trim().toLowerCase(Locale.ROOT);
    String finalReason = reason == null || reason.isBlank() ? null : reason.trim();
    pendingAcks.add(new QueueAck(queueId, finalStatus, finalReason, 1));
    ackQueued.incrementAndGet();
    record("cloud_ack_queued");
  }

  private void requeueAcks(List<QueueAck> batch, String error) {
    if (batch == null || batch.isEmpty()) return;
    for (QueueAck ack : batch) {
      if (ack == null) continue;
      int nextAttempt = ack.attempt() + 1;
      if (nextAttempt > ackMaxAttempts) {
        ackDropped.incrementAndGet();
        record("cloud_ack_dropped");
        continue;
      }
      ackRetried.incrementAndGet();
      pendingAcks.add(new QueueAck(ack.queueId(), ack.status(), ack.reason(), nextAttempt));
      record("cloud_ack_retry");
    }
    if (error != null && !error.isBlank()) {
      lastAckError.set(error);
    }
  }

  private String buildAckPayload(List<QueueAck> batch) {
    if (batch == null || batch.isEmpty()) return "";
    List<Document> docs = new ArrayList<>(batch.size());
    for (QueueAck ack : batch) {
      if (ack == null) continue;
      Document doc = new Document("id", ack.queueId())
        .append("status", ack.status())
        .append("attempt", ack.attempt());
      if (ack.reason() != null && !ack.reason().isBlank()) {
        doc.append("reason", ack.reason());
      }
      docs.add(doc);
    }
    Document root = new Document("server_id", serverId)
      .append("serverId", serverId)
      .append("acks", docs);
    return root.toJson();
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

  private HttpRequest signedPost(String path, String body) {
    long ts = System.currentTimeMillis();
    String nonce = Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), 16);
    String payload = body == null ? "" : body;
    String signature = generateSignature(ts + nonce + payload);

    return HttpRequest.newBuilder()
      .uri(resolveUri(path))
      .timeout(requestTimeout)
      .header("Accept", "application/json")
      .header("Content-Type", "application/json")
      .header("X-Orbis-Signature", signature)
      .header("X-Orbis-TS", String.valueOf(ts))
      .header("X-Orbis-Nonce", nonce)
      .POST(HttpRequest.BodyPublishers.ofString(payload))
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

  private String resolveAckPath() {
    String path = ackPath == null ? "" : ackPath.trim();
    if (path.contains("{serverId}")) {
      path = path.replace("{serverId}", encodePath(serverId));
    }
    if (path.isBlank()) {
      return "/v1/agent/queue/ack";
    }
    if (path.startsWith("http://") || path.startsWith("https://")) {
      return path;
    }
    if (!path.startsWith("/")) {
      return "/" + path;
    }
    return path;
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

  private static String stringValue(Object value) {
    String text = asString(value);
    if (text == null) return null;
    String trimmed = text.trim();
    return trimmed.isBlank() ? null : trimmed;
  }

  private static long longValue(Object value, long fallback) {
    if (value == null) return fallback;
    if (value instanceof Number n) return n.longValue();
    try {
      return Long.parseLong(String.valueOf(value).trim());
    } catch (NumberFormatException ignored) {
      return fallback;
    }
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

  private static String extractQueueId(Document doc) {
    if (doc == null) return null;
    for (String key : List.of("id", "queue_id", "event_id", "transaction_id", "delivery_id")) {
      String value = asString(doc.get(key));
      if (value == null || value.isBlank()) continue;
      return value.trim();
    }
    return null;
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

  private record QueueAck(String queueId, String status, String reason, int attempt) {}

  public CloudStatusSnapshot statusSnapshot() {
    long processedSize = processedQueueIds == null ? 0L : processedQueueIds.estimatedSize();
    long inflightSize = inflightQueueIds == null ? 0L : inflightQueueIds.estimatedSize();
    int pendingSize = pendingAcks.size();
    return new CloudStatusSnapshot(
      serverId,
      baseUrl,
      lastPollAttemptMs.get(),
      lastPollSuccessMs.get(),
      lastHttpStatus.get(),
      lastBatchSize.get(),
      totalQueueActions.get(),
      duplicateQueueSkips.get(),
      inflightQueueSkips.get(),
      offlineQueueSkips.get(),
      invalidQueueSkips.get(),
      queueFailures.get(),
      processedSize,
      inflightSize,
      dedupePersistEnabled,
      dedupePersistFile,
      dedupePersistFlushSeconds,
      lastDedupeLoadMs.get(),
      lastDedupePersistMs.get(),
      dedupePersistErrors.get(),
      lastDedupePersistError.get(),
      ackEnabled,
      ackDisabled,
      pendingSize,
      ackQueued.get(),
      ackSent.get(),
      ackFailed.get(),
      ackRetried.get(),
      ackDropped.get(),
      lastAckAttemptMs.get(),
      lastAckSuccessMs.get(),
      lastAckStatus.get(),
      lastAckError.get(),
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
    long duplicateQueueSkips,
    long inflightQueueSkips,
    long offlineQueueSkips,
    long invalidQueueSkips,
    long queueFailures,
    long processedIds,
    long inflightIds,
    boolean dedupePersistEnabled,
    String dedupePersistFile,
    int dedupePersistFlushSeconds,
    long lastDedupeLoadMs,
    long lastDedupePersistMs,
    long dedupePersistErrors,
    String lastDedupePersistError,
    boolean ackEnabled,
    boolean ackDisabled,
    int pendingAcks,
    long ackQueued,
    long ackSent,
    long ackFailed,
    long ackRetried,
    long ackDropped,
    long lastAckAttemptMs,
    long lastAckSuccessMs,
    int lastAckStatus,
    String lastAckError,
    String lastError
  ) {}
}
