package net.orbis.zakum.core;

import net.orbis.zakum.api.capability.Capability;
import net.orbis.zakum.api.capability.CapabilityRegistry;
import net.orbis.zakum.api.capability.ZakumCapabilities;
import net.orbis.zakum.api.net.ControlPlaneClient;
import org.bukkit.plugin.ServicesManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

final class ServicesManagerCapabilityRegistry implements CapabilityRegistry {

  private final ServicesManager services;
  private final Map<Capability<?>, Supplier<Optional<?>>> localResolvers;
  private final Set<Capability<?>> known;

  ServicesManagerCapabilityRegistry(ServicesManager services, Optional<ControlPlaneClient> controlPlane) {
    this.services = Objects.requireNonNull(services, "services");

    var local = new HashMap<Capability<?>, Supplier<Optional<?>>>();
    local.put(ZakumCapabilities.CONTROL_PLANE, () -> controlPlane.map(it -> it));
    this.localResolvers = Map.copyOf(local);

    this.known = Set.of(
      ZakumCapabilities.ACTION_BUS,
      ZakumCapabilities.DEFERRED_ACTIONS,
      ZakumCapabilities.ENTITLEMENTS,
      ZakumCapabilities.BOOSTERS,
      ZakumCapabilities.CONTROL_PLANE,
      ZakumCapabilities.DATA_STORE,
      ZakumCapabilities.SOCIAL,
      ZakumCapabilities.PACKETS,
      ZakumCapabilities.PLACEHOLDERS,
      ZakumCapabilities.ECONOMY,
      ZakumCapabilities.LUCKPERMS
    );
  }

  @Override
  public <T> Optional<T> get(Capability<T> capability) {
    Objects.requireNonNull(capability, "capability");

    var local = localResolvers.get(capability);
    if (local != null) {
      return castOptional(capability.type(), local.get());
    }

    return Optional.ofNullable(services.load(capability.type()));
  }

  @Override
  public boolean has(Capability<?> capability) {
    return getUnchecked(capability).isPresent();
  }

  @Override
  public Set<Capability<?>> known() {
    return known;
  }

  private static <T> Optional<T> castOptional(Class<T> type, Optional<?> value) {
    return value.map(type::cast);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Optional<?> getUnchecked(Capability<?> capability) {
    return (Optional) get((Capability) capability);
  }
}
