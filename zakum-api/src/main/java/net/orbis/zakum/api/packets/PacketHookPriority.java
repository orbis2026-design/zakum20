package net.orbis.zakum.api.packets;

/**
 * Listener priority for packet hooks.
 *
 * Semantics are intentionally similar to Bukkit event priorities:
 * - LOWEST runs first
 * - MONITOR runs last (should not mutate/cancel)
 */
public enum PacketHookPriority {
  LOWEST,
  LOW,
  NORMAL,
  HIGH,
  HIGHEST,
  MONITOR
}
