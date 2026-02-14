package net.orbis.zakum.core.anticheat;

import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.api.action.AceEngine;
import net.orbis.zakum.core.metrics.MetricsMonitor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
  private final List<String> script;
  private final Logger logger;
  private final MetricsMonitor metrics;

  public GrimFlagBridge(ZakumApi api, Plugin plugin, List<String> script, Logger logger, MetricsMonitor metrics) {
    this.api = api;
    this.plugin = plugin;
    this.script = script == null ? List.of() : List.copyOf(script);
    this.logger = logger;
    this.metrics = metrics;
  }

  public boolean register() {
    Class<? extends Event> eventClass = resolveEventClass();
    if (eventClass == null) {
      logger.warning("Grim bridge enabled but no compatible Grim flag event class was found.");
      return false;
    }
    Bukkit.getPluginManager().registerEvent(eventClass, this, EventPriority.MONITOR, this::onFlagEvent, plugin, true);
    logger.info("Grim bridge active on " + eventClass.getName());
    return true;
  }

  private void onFlagEvent(Listener ignored, Event event) {
    if (event == null || script.isEmpty()) return;

    Player player = resolvePlayer(event);
    if (player == null || !player.isOnline()) return;

    Map<String, Object> metadata = collectMetadata(event, player);
    api.getScheduler().runAtEntity(player, () -> {
      if (!player.isOnline()) return;
      api.getAceEngine().executeScript(script, new AceEngine.ActionContext(player, Optional.empty(), metadata));
      if (metrics != null) metrics.recordAction("grim_flag_ace");
    });
  }

  private Map<String, Object> collectMetadata(Event event, Player player) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("check", firstString(event, "getCheckName", "getCheck", "checkName", "check", "getHackType"));
    metadata.put("verbose", firstString(event, "getVerbose", "verbose", "getReason", "reason"));
    metadata.put("vl", firstNumber(event, "getVl", "vl", "getViolations", "violations", "getViolationLevel"));
    metadata.put("player", player.getName());
    metadata.put("uuid", player.getUniqueId().toString());
    metadata.put("event_class", event.getClass().getSimpleName());
    metadata.values().removeIf(v -> v == null || String.valueOf(v).isBlank());
    return metadata;
  }

  private Player resolvePlayer(Object event) {
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
}
