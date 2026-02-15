package net.orbis.zakum.core.anticheat;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.api.action.AceEngine;
import net.orbis.zakum.api.config.ZakumSettings;
import net.orbis.zakum.core.metrics.MetricsMonitor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * Optional GrimAC bridge loaded through reflection.
 * If Grim classes are present, flag events are converted into ACE executions.
 */
public final class GrimFlagBridge implements Listener {

  private static final List<String> EVENT_CANDIDATES = List.of(
    "ac.grim.grimac.api.events.FlagEvent",
    "ac.grim.grimac.api.events.PlayerFlagEvent",
    "ac.grim.grimac.api.events.GrimFlagEvent",
    "ac.grim.grimac.api.events.player.GrimPlayerFlagEvent",
    "ac.grim.grimac.api.events.player.PlayerFlagEvent"
  );

  private final ZakumApi api;
  private final Plugin plugin;
  private final ZakumSettings.Anticheat.Grim config;
  private final List<String> script;
  private final Logger logger;
  private final MetricsMonitor metrics;
  private final Cache<String, Long> cooldownByCheck;
  private final Cache<UUID, RateWindow> rateByPlayer;
  private final AtomicBoolean listenerActive;
  private final AtomicBoolean runtimeEnabled;
  private final AtomicReference<String> eventClass;
  private final AtomicLong flagsObserved;
  private final AtomicLong flagsExecuted;
  private final AtomicLong cooldownSkips;
  private final AtomicLong rateLimitedSkips;
  private final AtomicLong runtimeDisabledSkips;
  private final AtomicLong missingPlayerSkips;
  private final AtomicLong executionFailures;
  private final AtomicLong lastFlagAtMs;
  private final AtomicLong lastExecutionAtMs;
  private final AtomicReference<String> lastError;

  public GrimFlagBridge(
    ZakumApi api,
    Plugin plugin,
    ZakumSettings.Anticheat.Grim config,
    Logger logger,
    MetricsMonitor metrics
  ) {
    this.api = api;
    this.plugin = plugin;
    this.config = config == null
      ? new ZakumSettings.Anticheat.Grim(false, 0L, 0, true, List.of())
      : config;
    this.script = normalizeScript(this.config.aceScript());
    this.logger = logger;
    this.metrics = metrics;
    long cooldownTtlMs = Math.max(60_000L, this.config.cooldownMsPerCheck() * 10L);
    this.cooldownByCheck = Caffeine.newBuilder()
      .maximumSize(200_000L)
      .expireAfterWrite(Duration.ofMillis(cooldownTtlMs))
      .build();
    this.rateByPlayer = Caffeine.newBuilder()
      .maximumSize(100_000L)
      .expireAfterAccess(Duration.ofMinutes(2))
      .build();
    this.listenerActive = new AtomicBoolean(false);
    this.runtimeEnabled = new AtomicBoolean(this.config.enabled());
    this.eventClass = new AtomicReference<>("");
    this.flagsObserved = new AtomicLong(0L);
    this.flagsExecuted = new AtomicLong(0L);
    this.cooldownSkips = new AtomicLong(0L);
    this.rateLimitedSkips = new AtomicLong(0L);
    this.runtimeDisabledSkips = new AtomicLong(0L);
    this.missingPlayerSkips = new AtomicLong(0L);
    this.executionFailures = new AtomicLong(0L);
    this.lastFlagAtMs = new AtomicLong(0L);
    this.lastExecutionAtMs = new AtomicLong(0L);
    this.lastError = new AtomicReference<>("");
  }

  public boolean register() {
    Class<? extends Event> eventClass = resolveEventClass();
    if (eventClass == null) {
      listenerActive.set(false);
      this.eventClass.set("");
      logger.warning("Grim bridge enabled but no compatible Grim flag event class was found.");
      return false;
    }
    Bukkit.getPluginManager().registerEvent(eventClass, this, EventPriority.MONITOR, this::onFlagEvent, plugin, true);
    this.eventClass.set(eventClass.getName());
    listenerActive.set(true);
    logger.info("Grim bridge active on " + eventClass.getName());
    return true;
  }

  public boolean setRuntimeEnabled(boolean enabled) {
    runtimeEnabled.set(enabled);
    return runtimeEnabled.get();
  }

  public boolean runtimeEnabled() {
    return runtimeEnabled.get();
  }

  private void onFlagEvent(Listener ignored, Event event) {
    if (event == null || script.isEmpty()) return;

    long now = System.currentTimeMillis();
    flagsObserved.incrementAndGet();
    lastFlagAtMs.set(now);
    record("grim_flag_event");

    if (!runtimeEnabled.get()) {
      runtimeDisabledSkips.incrementAndGet();
      record("grim_flag_disabled_skip");
      return;
    }

    Player player = resolvePlayer(event);
    if (player == null || !player.isOnline()) {
      missingPlayerSkips.incrementAndGet();
      record("grim_flag_no_player");
      return;
    }

    String checkName = firstString(event, "getCheckName", "getCheck", "checkName", "check", "getHackType");
    if (isOnCooldown(player.getUniqueId(), checkName, now)) {
      cooldownSkips.incrementAndGet();
      record("grim_flag_cooldown_skip");
      return;
    }

    if (isRateLimited(player.getUniqueId(), now)) {
      rateLimitedSkips.incrementAndGet();
      record("grim_flag_rate_limit_skip");
      return;
    }

    Map<String, Object> metadata = collectMetadata(event, player, checkName);
    api.getScheduler().runAtEntity(player, () -> {
      if (!player.isOnline()) return;
      try {
        api.getAceEngine().executeScript(script, new AceEngine.ActionContext(player, Optional.empty(), metadata));
        flagsExecuted.incrementAndGet();
        lastExecutionAtMs.set(System.currentTimeMillis());
        lastError.set("");
        record("grim_flag_ace");
      } catch (Throwable ex) {
        executionFailures.incrementAndGet();
        String error = summarize(ex);
        lastError.set(error);
        record("grim_flag_error");
        logger.warning("Grim bridge ACE execution failed: " + error);
      }
    });
  }

  private boolean isOnCooldown(UUID playerId, String checkName, long now) {
    long cooldownMs = config.cooldownMsPerCheck();
    if (cooldownMs <= 0L || playerId == null || checkName == null || checkName.isBlank()) return false;
    String key = playerId + "|" + checkName.trim().toLowerCase(Locale.ROOT);
    Long last = cooldownByCheck.getIfPresent(key);
    if (last != null && (now - last) < cooldownMs) {
      return true;
    }
    cooldownByCheck.put(key, now);
    return false;
  }

  private boolean isRateLimited(UUID playerId, long now) {
    int limit = config.maxFlagsPerMinutePerPlayer();
    if (limit <= 0 || playerId == null) return false;
    RateWindow current = rateByPlayer.getIfPresent(playerId);
    if (current == null || (now - current.windowStartMs()) >= 60_000L) {
      rateByPlayer.put(playerId, new RateWindow(now, 1));
      return false;
    }
    int nextCount = current.count() + 1;
    rateByPlayer.put(playerId, new RateWindow(current.windowStartMs(), nextCount));
    return nextCount > limit;
  }

  private Map<String, Object> collectMetadata(Event event, Player player, String checkName) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("check", checkName);
    metadata.put("vl", firstNumber(event, "getVl", "vl", "getViolations", "violations", "getViolationLevel"));
    if (config.includeVerboseMetadata()) {
      metadata.put("verbose", firstString(event, "getVerbose", "verbose", "getReason", "reason"));
    }
    metadata.put("player", player.getName());
    metadata.put("uuid", player.getUniqueId().toString());
    metadata.put("event_class", event.getClass().getSimpleName());
    metadata.values().removeIf(v -> v == null || String.valueOf(v).isBlank());
    return metadata;
  }

  public Snapshot snapshot() {
    return new Snapshot(
      config.enabled(),
      runtimeEnabled.get(),
      listenerActive.get(),
      eventClass.get(),
      script.size(),
      config.cooldownMsPerCheck(),
      config.maxFlagsPerMinutePerPlayer(),
      config.includeVerboseMetadata(),
      flagsObserved.get(),
      flagsExecuted.get(),
      cooldownSkips.get(),
      rateLimitedSkips.get(),
      runtimeDisabledSkips.get(),
      missingPlayerSkips.get(),
      executionFailures.get(),
      lastFlagAtMs.get(),
      lastExecutionAtMs.get(),
      lastError.get()
    );
  }

  public record Snapshot(
    boolean configuredEnabled,
    boolean runtimeEnabled,
    boolean listenerActive,
    String eventClass,
    int scriptLines,
    long cooldownMsPerCheck,
    int maxFlagsPerMinutePerPlayer,
    boolean includeVerboseMetadata,
    long flagsObserved,
    long flagsExecuted,
    long cooldownSkips,
    long rateLimitedSkips,
    long runtimeDisabledSkips,
    long missingPlayerSkips,
    long executionFailures,
    long lastFlagAtMs,
    long lastExecutionAtMs,
    String lastError
  ) {}

  private static Player resolvePlayer(Object event) {
    Object direct = invokeAny(event, "getPlayer", "player", "getBukkitPlayer");
    Player fromDirect = castPlayer(direct);
    if (fromDirect != null) return fromDirect;

    Object uuid = invokeAny(event, "getPlayerUuid", "playerUuid", "getUuid", "uuid", "getUniqueId");
    Player fromUuid = findPlayer(uuid);
    if (fromUuid != null) return fromUuid;

    Object holder = invokeAny(event, "getUser", "user", "getData", "data", "getGrimPlayer", "grimPlayer");
    if (holder == null) return null;

    Object nestedPlayer = invokeAny(holder, "getPlayer", "player", "getBukkitPlayer");
    Player fromNested = castPlayer(nestedPlayer);
    if (fromNested != null) return fromNested;

    Object nestedUuid = invokeAny(holder, "getUniqueId", "uniqueId", "getUuid", "uuid");
    return findPlayer(nestedUuid);
  }

  private static Player castPlayer(Object value) {
    return value instanceof Player player ? player : null;
  }

  private static Player findPlayer(Object value) {
    if (value == null) return null;
    if (value instanceof UUID uuid) return Bukkit.getPlayer(uuid);
    try {
      return Bukkit.getPlayer(UUID.fromString(String.valueOf(value).trim()));
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private static String firstString(Object target, String... methodNames) {
    Object value = invokeAny(target, methodNames);
    if (value == null) return null;
    String text = String.valueOf(value).trim();
    return text.isBlank() ? null : text;
  }

  private static Number firstNumber(Object target, String... methodNames) {
    Object value = invokeAny(target, methodNames);
    if (value instanceof Number n) return n;
    if (value == null) return null;
    try {
      return Double.parseDouble(String.valueOf(value).trim());
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private static Object invokeAny(Object target, String... methodNames) {
    if (target == null || methodNames == null) return null;
    Class<?> type = target.getClass();
    for (String methodName : methodNames) {
      if (methodName == null || methodName.isBlank()) continue;
      Method method = findZeroArg(type, methodName);
      if (method == null) continue;
      try {
        method.setAccessible(true);
        return method.invoke(target);
      } catch (Throwable ignored) {
        // Probe next method.
      }
    }
    return null;
  }

  private static Method findZeroArg(Class<?> type, String name) {
    try {
      return type.getMethod(name);
    } catch (NoSuchMethodException ignored) {
      for (Method method : type.getMethods()) {
        if (method.getName().equals(name) && method.getParameterCount() == 0) return method;
      }
      for (Method method : type.getDeclaredMethods()) {
        if (method.getName().equals(name) && method.getParameterCount() == 0) return method;
      }
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  private static Class<? extends Event> resolveEventClass() {
    for (String candidate : EVENT_CANDIDATES) {
      try {
        Class<?> raw = Class.forName(candidate);
        if (Event.class.isAssignableFrom(raw)) {
          return (Class<? extends Event>) raw;
        }
      } catch (Throwable ignored) {
        // Try next candidate.
      }
    }
    return null;
  }

  public static List<String> normalizeScript(List<String> configuredScript) {
    List<String> normalized = new ArrayList<>();
    if (configuredScript != null) {
      for (String line : configuredScript) {
        if (line == null || line.isBlank()) continue;
        normalized.add(line.trim());
      }
    }
    if (normalized.isEmpty()) {
      normalized = List.of(
        "[MESSAGE] <red>Anticheat flag: <gold>%check%</gold>",
        "[TITLE] <red>Flagged</red>|<gray>%check%</gray> {stay=20 fadein=5 fadeout=5}"
      );
    }
    return normalized;
  }

  private void record(String actionType) {
    if (metrics != null) {
      metrics.recordAction(actionType);
    }
  }

  private static String summarize(Throwable error) {
    if (error == null) return "";
    String message = error.getMessage();
    if (message == null || message.isBlank()) {
      message = error.getClass().getSimpleName();
    }
    String clean = message.replace('\n', ' ').replace('\r', ' ').trim();
    if (clean.length() <= 240) return clean;
    return clean.substring(0, 240) + "...";
  }

  private record RateWindow(long windowStartMs, int count) {}
}
