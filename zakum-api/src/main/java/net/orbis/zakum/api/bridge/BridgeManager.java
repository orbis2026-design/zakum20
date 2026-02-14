package net.orbis.zakum.api.bridge;

import java.util.Set;

public interface BridgeManager {

  void registerBridge(String bridgeId);

  void unregisterBridge(String bridgeId);

  boolean isActive(String bridgeId);

  Set<String> activeBridges();
}
