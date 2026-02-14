package net.orbis.zakum.core.social;

import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

/**
 * Optional packet-backed chat sender for pre-serialized JSON payloads.
 *
 * Uses reflection so zakum-core stays runtime-optional against PacketEvents.
 */
final class PacketChatTransport {

  private final boolean enabled;
  private final Logger logger;
  private final AtomicBoolean warnedUnavailable;
  private final LongAdder packetSends;
  private final LongAdder fallbackSends;
  private final LongAdder sendFailures;

  private volatile boolean initialized;
  private volatile boolean available;
  private volatile Constructor<?> jsonOverlayCtor;
  private volatile Constructor<?> chatTypeJsonCtor;
  private volatile Object chatTypeSystem;
  private volatile Class<?> packetEventsClass;
  private volatile Method getApiMethod;
  private volatile Method getPlayerManagerMethod;
  private volatile Method sendPacketMethod;

  PacketChatTransport(boolean enabled, Logger logger) {
    this.enabled = enabled;
    this.logger = logger;
    this.warnedUnavailable = new AtomicBoolean(false);
    this.packetSends = new LongAdder();
    this.fallbackSends = new LongAdder();
    this.sendFailures = new LongAdder();
  }

  boolean enabled() {
    return enabled;
  }

  boolean sendSystem(Player viewer, byte[] serializedJson) {
    return sendSystem(viewer, serializedJson, false);
  }

  boolean sendSystem(Player viewer, byte[] serializedJson, boolean overlay) {
    if (!enabled || viewer == null || !viewer.isOnline()) return false;
    if (serializedJson == null || serializedJson.length == 0) return false;
    if (!ensureInitialized()) return false;

    String json = new String(serializedJson, StandardCharsets.UTF_8);
    if (json.isBlank()) return false;

    try {
      Object packet = newPacket(json, overlay);
      if (packet == null) return false;
      Object api = getApiMethod.invoke(null);
      if (api == null) return false;
      Object playerManager = getPlayerManagerMethod.invoke(api);
      if (playerManager == null) return false;
      sendPacketMethod.invoke(playerManager, viewer, packet);
      packetSends.increment();
      return true;
    } catch (Throwable ex) {
      sendFailures.increment();
      warnUnavailable("Packet chat dispatch failed, falling back to Adventure: " + ex.getMessage());
      return false;
    }
  }

  void recordFallback() {
    fallbackSends.increment();
  }

  long packetSends() {
    return packetSends.sum();
  }

  long fallbackSends() {
    return fallbackSends.sum();
  }

  long sendFailures() {
    return sendFailures.sum();
  }

  boolean available() {
    return enabled && available;
  }

  private boolean ensureInitialized() {
    if (!enabled) return false;
    if (initialized) return available;
    synchronized (this) {
      if (initialized) return available;
      try {
        Class<?> packetEvents = Class.forName("com.github.retrooper.packetevents.PacketEvents");
        Method getApi = packetEvents.getMethod("getAPI");
        Object api = getApi.invoke(null);
        if (api == null) {
          initialized = true;
          available = false;
          return false;
        }

        Class<?> wrapperClass = Class.forName(
          "com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSystemChatMessage"
        );
        Constructor<?> overlayCtor = null;
        Constructor<?> typeCtor = null;
        for (Constructor<?> ctor : wrapperClass.getConstructors()) {
          Class<?>[] params = ctor.getParameterTypes();
          if (params.length != 2) continue;
          if (params[0] == boolean.class && params[1] == String.class) {
            overlayCtor = ctor;
          }
          if (params[1] == String.class && params[0].getName().equals("com.github.retrooper.packetevents.protocol.chat.ChatType")) {
            typeCtor = ctor;
          }
        }

        Class<?> chatTypes = Class.forName("com.github.retrooper.packetevents.protocol.chat.ChatTypes");
        Object systemType = null;
        try {
          systemType = chatTypes.getField("SYSTEM").get(null);
        } catch (NoSuchFieldException ignored) {
          // Optional for legacy constructor path.
        }

        Method playerManagerMethod = api.getClass().getMethod("getPlayerManager");
        Object playerManager = playerManagerMethod.invoke(api);
        if (playerManager == null) {
          initialized = true;
          available = false;
          return false;
        }
        Method sendMethod = findSendPacketMethod(playerManager.getClass());
        if (sendMethod == null) {
          initialized = true;
          available = false;
          return false;
        }

        this.packetEventsClass = packetEvents;
        this.getApiMethod = getApi;
        this.getPlayerManagerMethod = playerManagerMethod;
        this.sendPacketMethod = sendMethod;
        this.jsonOverlayCtor = overlayCtor;
        this.chatTypeJsonCtor = typeCtor;
        this.chatTypeSystem = systemType;
        this.available = overlayCtor != null || (typeCtor != null && systemType != null);
      } catch (Throwable ex) {
        this.available = false;
      } finally {
        this.initialized = true;
      }

      if (!available) {
        warnUnavailable("Packet chat dispatch unavailable. Install/enable PacketEvents to use pre-serialized packet sends.");
      }
      return available;
    }
  }

  private Object newPacket(String json, boolean overlay) throws Exception {
    if (jsonOverlayCtor != null) {
      return jsonOverlayCtor.newInstance(overlay, json);
    }
    if (chatTypeJsonCtor != null && chatTypeSystem != null) {
      return chatTypeJsonCtor.newInstance(chatTypeSystem, json);
    }
    return null;
  }

  private Method findSendPacketMethod(Class<?> playerManagerClass) {
    for (Method method : playerManagerClass.getMethods()) {
      if (!method.getName().equals("sendPacket")) continue;
      if (method.getParameterCount() != 2) continue;
      Class<?>[] params = method.getParameterTypes();
      if (!Player.class.isAssignableFrom(params[0])) continue;
      return method;
    }
    return null;
  }

  private void warnUnavailable(String message) {
    if (!warnedUnavailable.compareAndSet(false, true)) return;
    logger.fine(message);
  }
}
