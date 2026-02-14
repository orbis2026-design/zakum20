package net.orbis.zakum.api.actions;

/**
 * An action payload without UUID attribution (used for offline queues).
 */
public record DeferredAction(
  String type,
  long amount,
  String key,
  String value
) {
  public DeferredAction {
    if (type == null || type.isBlank()) throw new IllegalArgumentException("type");
    if (amount <= 0) throw new IllegalArgumentException("amount must be > 0");
    if (key == null) key = "";
    if (value == null) value = "";
  }
}
