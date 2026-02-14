package net.orbis.zakum.api.capability;

import java.util.Optional;
import java.util.Set;

/**
 * Runtime capability lookup for optional integrations.
 *
 * Implementations should be lightweight and safe to call in hot paths.
 */
public interface CapabilityRegistry {

  <T> Optional<T> get(Capability<T> capability);

  boolean has(Capability<?> capability);

  Set<Capability<?>> known();

  default <T> T require(Capability<T> capability) {
    return get(capability).orElseThrow(
      () -> new IllegalStateException("Missing capability: " + capability.id())
    );
  }
}
