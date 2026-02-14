package net.orbis.zakum.core.bridge;

import net.orbis.zakum.api.bridge.BridgeManager;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class SimpleBridgeManager implements BridgeManager {

  private final Set<String> active;

  public SimpleBridgeManager() {
    this.active = ConcurrentHashMap.newKeySet();
  }

  @Override
  public void registerBridge(String bridgeId) {
    if (bridgeId == null || bridgeId.isBlank()) return;
    active.add(bridgeId.toLowerCase());
  }

  @Override
  public void unregisterBridge(String bridgeId) {
    if (bridgeId == null || bridgeId.isBlank()) return;
    active.remove(bridgeId.toLowerCase());
  }

  @Override
  public boolean isActive(String bridgeId) {
    if (bridgeId == null || bridgeId.isBlank()) return false;
    return active.contains(bridgeId.toLowerCase());
  }

  @Override
  public Set<String> activeBridges() {
    return Collections.unmodifiableSet(new HashSet<>(active));
  }
}
