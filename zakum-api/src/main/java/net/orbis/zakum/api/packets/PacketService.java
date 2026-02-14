package net.orbis.zakum.api.packets;

import org.bukkit.plugin.Plugin;

/**
 * Packet hook service.
 *
 * The implementation is provided by a backend plugin (e.g. ZakumPackets using PacketEvents).
 */
public interface PacketService {

  /**
   * Register a hook owned by a plugin.
   * Hooks are automatically removed when the owner plugin disables (best-effort).
   */
  void registerHook(Plugin owner, PacketHook hook);

  /**
   * Unregister all hooks owned by the given plugin.
   */
  void unregisterHooks(Plugin owner);

  /**
   * @return current number of registered hooks (all owners)
   */
  int hookCount();

  /**
   * @return backend identifier (ex: "packetevents")
   */
  String backend();
}
