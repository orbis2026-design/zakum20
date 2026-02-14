package net.orbis.zakum.api.packets;

import java.util.Set;

/**
 * A single packet hook.
 *
 * Performance notes:
 * - Prefer providing a non-empty packetNames allowlist (exact match) whenever possible.
 * - Keep handlers small; do NOT call Bukkit world APIs from the packet thread.
 */
public record PacketHook(
  PacketDirection direction,
  PacketHookPriority priority,
  Set<String> packetNames,
  PacketHookHandler handler
) {

    public PacketHook {
    if (direction == null) throw new IllegalArgumentException("direction");
    if (priority == null) throw new IllegalArgumentException("priority");
    if (packetNames == null) throw new IllegalArgumentException("packetNames");
    if (handler == null) throw new IllegalArgumentException("handler");

    // Normalize to uppercase exact-match keys for predictable matching.
    if (!packetNames.isEmpty()) {
      java.util.HashSet<String> norm = new java.util.HashSet<>(packetNames.size());
      for (String s : packetNames) {
        if (s == null) continue;
        String x = s.trim().toUpperCase(java.util.Locale.ROOT);
        if (!x.isBlank()) norm.add(x);
      }
      packetNames = java.util.Set.copyOf(norm);
    }
  }

  public boolean matches(String packetName) {
    if (packetNames.isEmpty()) return true;
    if (packetName == null) return false;
    return packetNames.contains(packetName.trim().toUpperCase(java.util.Locale.ROOT));
  }
}
