package net.orbis.zakum.api.packets;

import org.bukkit.entity.Player;

/**
 * Context passed to packet hooks.
 *
 * Threading:
 * - Packet hooks run on the underlying packet thread (Netty), NOT the Bukkit main thread.
 * - Only use thread-safe operations. Use Bukkit scheduler for sync work if needed.
 */
public interface PacketContext {

  Player player();

  PacketDirection direction();

  /**
   * Packet name as provided by the underlying packet library.
   * For PacketEvents this corresponds to ProtocolPacketEvent#getPacketName().
   */
  String packetName();

  /**
   * Packet id as provided by the underlying packet library.
   * For PacketEvents this corresponds to ProtocolPacketEvent#getPacketId().
   */
  int packetId();

  long timestampMs();

  boolean cancelled();

  void cancel();

  /**
   * Raw library event object. Intended as an escape hatch.
   * For PacketEvents this is PacketReceiveEvent / PacketSendEvent.
   */
  Object nativeEvent();

  /**
   * Raw packet wrapper/object for advanced integrations.
   * For PacketEvents, use PacketEvents wrappers via nativeEvent/getLastUsedWrapper if needed.
   */
  Object nativePacket();
}
