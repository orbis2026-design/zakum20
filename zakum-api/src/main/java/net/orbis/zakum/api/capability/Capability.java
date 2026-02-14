package net.orbis.zakum.api.capability;

import java.util.Objects;

/**
 * Typed capability descriptor.
 *
 * Capabilities represent optional or pluggable services that may be provided
 * by Zakum itself or bridge plugins.
 */
public record Capability<T>(String id, Class<T> type) {

  public Capability {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("Capability id must not be blank");
    }
    type = Objects.requireNonNull(type, "type");
  }

  public static <T> Capability<T> of(String id, Class<T> type) {
    return new Capability<>(id, type);
  }
}
