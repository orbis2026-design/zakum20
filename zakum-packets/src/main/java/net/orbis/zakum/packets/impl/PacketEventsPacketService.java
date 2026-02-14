package net.orbis.zakum.packets.impl;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.EventManager;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.api.config.ZakumSettings;
import net.orbis.zakum.api.packets.PacketContext;
import net.orbis.zakum.api.packets.PacketDirection;
import net.orbis.zakum.api.packets.PacketHook;
import net.orbis.zakum.api.packets.PacketHookPriority;
import net.orbis.zakum.api.packets.PacketService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

/**
 * PacketService backed by PacketEvents.
 *
 * Important: hooks run on PacketEvents' packet thread (Netty).
 */
public final class PacketEventsPacketService implements PacketService, AutoCloseable {

  private final Plugin plugin;
  private final ZakumApi zakum;
  private final ZakumSettings.Packets cfg;

  // Owner -> hooks (for cleanup)
  private final Map<Plugin, Set<RegisteredHook>> ownerHooks = new ConcurrentHashMap<>();

  // direction -> priority -> listeners
  private final EnumMap<PacketDirection, EnumMap<PacketHookPriority, CopyOnWriteArrayList<RegisteredHook>>> hooks = new EnumMap<>(PacketDirection.class);

  private final PacketListenerAbstract listener;

  public PacketEventsPacketService(Plugin plugin, ZakumApi zakum, ZakumSettings.Packets cfg) {
    this.plugin = plugin;
    this.zakum = zakum;
    this.cfg = cfg;

    this.hooks.put(PacketDirection.INBOUND, new EnumMap<>(PacketHookPriority.class));
    this.hooks.put(PacketDirection.OUTBOUND, new EnumMap<>(PacketHookPriority.class));
    for (var dir : PacketDirection.values()) {
      for (var pr : PacketHookPriority.values()) {
        this.hooks.get(dir).put(pr, new CopyOnWriteArrayList<>());
      }
    }

    // Register PacketEvents listener at the lowest priority so our registry can implement its own priority routing.
    this.listener = new PacketListenerAbstract(PacketListenerPriority.LOWEST) {
      @Override public void onPacketReceive(PacketReceiveEvent event) { handleReceive(event); }
      @Override public void onPacketSend(PacketSendEvent event) { handleSend(event); }
    };

    EventManager em = PacketEvents.getAPI().getEventManager();
    em.registerListener(this.listener);
  }

  @Override
  public void close() {
    try {
      PacketEvents.getAPI().getEventManager().unregisterListener(this.listener);
    } catch (Throwable ignored) {
      // Best-effort cleanup.
    }
    ownerHooks.clear();
    for (var dir : PacketDirection.values()) {
      for (var pr : PacketHookPriority.values()) {
        hooks.get(dir).get(pr).clear();
      }
    }
  }

  @Override
  public void registerHook(Plugin owner, PacketHook hook) {
    if (owner == null) throw new IllegalArgumentException("owner");
    if (hook == null) throw new IllegalArgumentException("hook");

    int max = cfg.maxHooksPerPlugin();
    if (max > 0) {
      int current = ownerHooks.getOrDefault(owner, Set.of()).size();
      if (current >= max) {
        throw new IllegalStateException("Max hooks reached for " + owner.getName() + " (" + max + ")");
      }
    }

    RegisteredHook reg = new RegisteredHook(owner, hook);
    ownerHooks.computeIfAbsent(owner, p -> ConcurrentHashMap.newKeySet()).add(reg);
    hooks.get(hook.direction()).get(hook.priority()).add(reg);
  }

  @Override
  public void unregisterHooks(Plugin owner) {
    if (owner == null) return;
    Set<RegisteredHook> set = ownerHooks.remove(owner);
    if (set == null || set.isEmpty()) return;

    for (RegisteredHook reg : set) {
      hooks.get(reg.hook.direction()).get(reg.hook.priority()).remove(reg);
    }
  }

  @Override
  public int hookCount() {
    int total = 0;
    for (var dir : PacketDirection.values()) {
      for (var pr : PacketHookPriority.values()) {
        total += hooks.get(dir).get(pr).size();
      }
    }
    return total;
  }

  @Override
  public String backend() {
    return "packetevents";
  }

  private void handleReceive(PacketReceiveEvent event) {
    if (!cfg.inbound()) return;
    Player player = castPlayer(event.getPlayer());
    if (player == null) return;

    String name = safe(event.getPacketName()).trim().toUpperCase(java.util.Locale.ROOT);
    int id = event.getPacketId();

    long ts = System.currentTimeMillis();

    boolean cancelled = false;
    for (var pr : PacketHookPriority.values()) {
      for (RegisteredHook reg : hooks.get(PacketDirection.INBOUND).get(pr)) {
        PacketHook hook = reg.hook;
        if (!hook.matches(name)) continue;

        PacketContext ctx = new PacketEventsContext(player, PacketDirection.INBOUND, name, id, event, ts, event.getLastUsedWrapper());
        try {
          hook.handler().handle(ctx);
        } catch (Throwable t) {
          plugin.getLogger().log(Level.WARNING, "Packet hook error from " + reg.owner.getName() + " (" + name + ")", t);
        }

        if (pr != PacketHookPriority.MONITOR && ctx.cancelled()) {
          cancelled = true;
          event.setCancelled(true);
        }
      }
    }

    // If cancelled, PacketEvents will drop the packet.
    if (cancelled) event.setCancelled(true);
  }

  private void handleSend(PacketSendEvent event) {
    if (!cfg.outbound()) return;
    Player player = castPlayer(event.getPlayer());
    if (player == null) return;

    String name = safe(event.getPacketName()).trim().toUpperCase(java.util.Locale.ROOT);
    int id = event.getPacketId();

    long ts = System.currentTimeMillis();

    boolean cancelled = false;
    for (var pr : PacketHookPriority.values()) {
      for (RegisteredHook reg : hooks.get(PacketDirection.OUTBOUND).get(pr)) {
        PacketHook hook = reg.hook;
        if (!hook.matches(name)) continue;

        PacketContext ctx = new PacketEventsContext(player, PacketDirection.OUTBOUND, name, id, event, ts, event.getLastUsedWrapper());
        try {
          hook.handler().handle(ctx);
        } catch (Throwable t) {
          plugin.getLogger().log(Level.WARNING, "Packet hook error from " + reg.owner.getName() + " (" + name + ")", t);
        }

        if (pr != PacketHookPriority.MONITOR && ctx.cancelled()) {
          cancelled = true;
          event.setCancelled(true);
        }
      }
    }

    if (cancelled) event.setCancelled(true);
  }

  private static Player castPlayer(Object o) {
    if (o instanceof Player p) return p;
    return null;
  }

  private static String safe(String s) {
    return s == null ? "" : s;
  }

  private record RegisteredHook(Plugin owner, PacketHook hook) {}

  /**
   * Lightweight PacketContext implementation backed by PacketEvents.
   */
  private static final class PacketEventsContext implements PacketContext {
    private final Player player;
    private final PacketDirection direction;
    private final String packetName;
    private final int packetId;
    private final Object nativeEvent;
    private final long timestamp;
    private final Object nativePacket;
    private volatile boolean cancelled;

    private PacketEventsContext(Player player, PacketDirection direction, String packetName, int packetId, Object nativeEvent, long timestamp, Object nativePacket) {
      this.player = player;
      this.direction = direction;
      this.packetName = packetName;
      this.packetId = packetId;
      this.nativeEvent = nativeEvent;
      this.timestamp = timestamp;
      this.nativePacket = nativePacket;
    }

    @Override public Player player() { return player; }
    @Override public PacketDirection direction() { return direction; }
    @Override public String packetName() { return packetName; }
    @Override public int packetId() { return packetId; }
    @Override public long timestampMs() { return timestamp; }
    @Override public boolean cancelled() { return cancelled; }
    @Override public void cancel() { this.cancelled = true; }
    @Override public Object nativeEvent() { return nativeEvent; }
    @Override public Object nativePacket() { return nativePacket; }
  }
}
