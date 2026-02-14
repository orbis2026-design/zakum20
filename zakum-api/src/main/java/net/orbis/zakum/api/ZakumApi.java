package net.orbis.zakum.api;

import net.orbis.zakum.api.action.AceEngine;
import net.orbis.zakum.api.actions.ActionBus;
import net.orbis.zakum.api.asset.AssetManager;
import net.orbis.zakum.api.boosters.BoosterService;
import net.orbis.zakum.api.bridge.BridgeManager;
import net.orbis.zakum.api.capability.Capability;
import net.orbis.zakum.api.capability.CapabilityRegistry;
import net.orbis.zakum.api.concurrent.ZakumScheduler;
import net.orbis.zakum.api.db.ZakumDatabase;
import net.orbis.zakum.api.entitlements.EntitlementService;
import net.orbis.zakum.api.net.ControlPlaneClient;
import net.orbis.zakum.api.packet.AnimationService;
import net.orbis.zakum.api.progression.ProgressionService;
import net.orbis.zakum.api.storage.StorageService;
import net.orbis.zakum.api.ui.GuiBridge;
import org.bukkit.plugin.Plugin;

import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Stable API consumed by other plugins.
 *
 * Consumers resolve via Bukkit ServicesManager:
 *   ZakumApi api = Bukkit.getServicesManager().load(ZakumApi.class);
 */
public interface ZakumApi {

  static ZakumApi get() { return ZakumApiProvider.get(); }

  Plugin plugin();

  ServerIdentity server();

  Clock clock();

  Executor async();

  ZakumDatabase database();

  ActionBus actions();

  EntitlementService entitlements();

  BoosterService boosters();

  AceEngine getAceEngine();

  ZakumScheduler getScheduler();

  StorageService getStorage();

  AnimationService getAnimations();

  BridgeManager getBridgeManager();

  ProgressionService getProgression();

  AssetManager getAssetManager();

  GuiBridge getGui();

  /**
   * Runtime lookup surface for optional integrations and pluggable systems.
   */
  CapabilityRegistry capabilities();

  /**
   * Convenience lookup for optional capabilities.
   */
  default <T> Optional<T> capability(Capability<T> capability) {
    return capabilities().get(capability);
  }

  /**
   * Optional: Orbis Cloud Bot / control-plane integration.
   * Keep this interface stable; implementation can evolve.
   */
  Optional<ControlPlaneClient> controlPlane();

  /** Central typed configuration snapshot. */
  net.orbis.zakum.api.config.ZakumSettings settings();
}
