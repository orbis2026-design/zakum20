package net.orbis.zakum.core;

import net.orbis.zakum.api.ServerIdentity;
import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.api.action.AceEngine;
import net.orbis.zakum.api.actions.ActionBus;
import net.orbis.zakum.api.asset.AssetManager;
import net.orbis.zakum.api.bridge.BridgeManager;
import net.orbis.zakum.api.concurrent.ZakumScheduler;
import net.orbis.zakum.api.config.ZakumSettings;
import net.orbis.zakum.api.boosters.BoosterService;
import net.orbis.zakum.api.capability.CapabilityRegistry;
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

final class ZakumApiImpl implements ZakumApi {

  private final Plugin plugin;
  private final ServerIdentity server;
  private final Clock clock;
  private final Executor async;
  private final ZakumDatabase database;
  private final Optional<ControlPlaneClient> controlPlane;
  private final ActionBus actions;
  private final EntitlementService entitlements;
  private final BoosterService boosters;
  private final AceEngine aceEngine;
  private final ZakumScheduler scheduler;
  private final StorageService storage;
  private final AnimationService animations;
  private final BridgeManager bridgeManager;
  private final ProgressionService progression;
  private final AssetManager assetManager;
  private final GuiBridge gui;
  private final ZakumSettings settings;
  private final CapabilityRegistry capabilities;

  ZakumApiImpl(
    Plugin plugin,
    ServerIdentity server,
    Clock clock,
    Executor async,
    ZakumDatabase database,
    Optional<ControlPlaneClient> controlPlane,
    ActionBus actions,
    EntitlementService entitlements,
    BoosterService boosters,
    AceEngine aceEngine,
    ZakumScheduler scheduler,
    StorageService storage,
    AnimationService animations,
    BridgeManager bridgeManager,
    ProgressionService progression,
    AssetManager assetManager,
    GuiBridge gui,
    ZakumSettings settings,
    CapabilityRegistry capabilities
  ) {
    this.plugin = plugin;
    this.server = server;
    this.clock = clock;
    this.async = async;
    this.database = database;
    this.controlPlane = controlPlane;
    this.actions = actions;
    this.entitlements = entitlements;
    this.boosters = boosters;
    this.aceEngine = aceEngine;
    this.scheduler = scheduler;
    this.storage = storage;
    this.animations = animations;
    this.bridgeManager = bridgeManager;
    this.progression = progression;
    this.assetManager = assetManager;
    this.gui = gui;
    this.settings = settings;
    this.capabilities = capabilities;
  }

  @Override public Plugin plugin() { return plugin; }

  @Override public ServerIdentity server() { return server; }

  @Override public Clock clock() { return clock; }

  @Override public Executor async() { return async; }

  @Override public ZakumDatabase database() { return database; }

  @Override public ActionBus actions() { return actions; }

  @Override public EntitlementService entitlements() { return entitlements; }

  @Override public BoosterService boosters() { return boosters; }

  @Override public AceEngine getAceEngine() { return aceEngine; }

  @Override public ZakumScheduler getScheduler() { return scheduler; }

  @Override public StorageService getStorage() { return storage; }

  @Override public AnimationService getAnimations() { return animations; }

  @Override public BridgeManager getBridgeManager() { return bridgeManager; }

  @Override public ProgressionService getProgression() { return progression; }

  @Override public AssetManager getAssetManager() { return assetManager; }

  @Override public GuiBridge getGui() { return gui; }

  @Override public CapabilityRegistry capabilities() { return capabilities; }

  @Override public Optional<ControlPlaneClient> controlPlane() { return controlPlane; }

  @Override public ZakumSettings settings() { return settings; }
}
