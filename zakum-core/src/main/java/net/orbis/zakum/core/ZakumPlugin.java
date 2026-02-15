package net.orbis.zakum.core;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import net.orbis.zakum.api.ServerIdentity;
import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.api.ZakumApiProvider;
import net.orbis.zakum.api.action.AceEngine;
import net.orbis.zakum.api.bridge.BridgeManager;
import net.orbis.zakum.api.cache.BurstCacheService;
import net.orbis.zakum.api.chat.ChatPacketBuffer;
import net.orbis.zakum.api.config.ZakumSettings;
import net.orbis.zakum.api.actions.DeferredActionService;
import net.orbis.zakum.api.capability.CapabilityRegistry;
import net.orbis.zakum.api.concurrent.ZakumScheduler;
import net.orbis.zakum.api.packet.AnimationService;
import net.orbis.zakum.api.progression.ProgressionService;
import net.orbis.zakum.api.social.SocialService;
import net.orbis.zakum.api.storage.DataStore;
import net.orbis.zakum.api.storage.StorageService;
import net.orbis.zakum.api.ui.GuiBridge;
import net.orbis.zakum.api.vault.EconomyService;
import net.orbis.zakum.core.action.ZakumAceEngine;
import net.orbis.zakum.core.actions.DeferredActionReplayListener;
import net.orbis.zakum.core.config.ZakumSettingsLoader;
import net.orbis.zakum.core.actions.SimpleActionBus;
import net.orbis.zakum.core.actions.SqlDeferredActionService;
import net.orbis.zakum.core.actions.emitters.*;
import net.orbis.zakum.core.anticheat.GrimFlagBridge;
import net.orbis.zakum.core.asset.InMemoryAssetManager;
import net.orbis.zakum.core.boosters.SqlBoosterService;
import net.orbis.zakum.core.bridge.SimpleBridgeManager;
import net.orbis.zakum.core.cache.RedisBurstCacheService;
import net.orbis.zakum.core.cloud.SecureCloudClient;
import net.orbis.zakum.core.concurrent.EarlySchedulerRuntime;
import net.orbis.zakum.core.concurrent.ZakumSchedulerImpl;
import net.orbis.zakum.core.db.SqlManager;
import net.orbis.zakum.core.economy.RedisGlobalEconomyService;
import net.orbis.zakum.core.entitlements.SqlEntitlementService;
import net.orbis.zakum.core.listeners.ChatListener;
import net.orbis.zakum.core.listeners.CloudIdentityListener;
import net.orbis.zakum.core.metrics.MetricsMonitor;
import net.orbis.zakum.core.moderation.ToxicityModerationService;
import net.orbis.zakum.core.net.HttpControlPlaneClient;
import net.orbis.zakum.core.obs.MetricsService;
import net.orbis.zakum.core.ops.StressHarnessV2;
import net.orbis.zakum.core.ops.ModuleStartupValidator;
import net.orbis.zakum.core.perf.PacketCullingKernel;
import net.orbis.zakum.core.perf.PlayerVisualModeListener;
import net.orbis.zakum.core.perf.PlayerVisualModeService;
import net.orbis.zakum.core.perf.ThreadGuard;
import net.orbis.zakum.core.perf.VisualCircuitBreaker;
import net.orbis.zakum.core.profile.PlayerJoinListener;
import net.orbis.zakum.core.profile.ProfileProvider;
import net.orbis.zakum.core.progression.ProgressionServiceImpl;
import net.orbis.zakum.core.social.CaffeineSocialService;
import net.orbis.zakum.core.social.BedrockClientDetector;
import net.orbis.zakum.core.social.BedrockGlyphRemapper;
import net.orbis.zakum.core.social.ChatBufferCache;
import net.orbis.zakum.core.social.CloudTabRenderer;
import net.orbis.zakum.core.social.LocalizedChatPacketBuffer;
import net.orbis.zakum.core.social.OrbisChatRenderer;
import net.orbis.zakum.core.social.SocialSnapshotLifecycleListener;
import net.orbis.zakum.core.storage.MongoDataStore;
import net.orbis.zakum.core.storage.StorageServiceImpl;
import net.orbis.zakum.core.ui.NoopGuiBridge;
import net.orbis.zakum.core.ui.ServiceBackedGuiBridge;
import net.orbis.zakum.core.util.Async;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.OfflinePlayer;
import redis.clients.jedis.JedisPool;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public final class ZakumPlugin extends JavaPlugin {

  private ZakumApiImpl api;
  private ZakumSettings settings;
  private MetricsService metrics;
  private SqlManager sql;
  private ExecutorService asyncPool;

  private SimpleActionBus actionBus;
  private DeferredActionService deferred;
  private SqlEntitlementService entitlements;
  private SqlBoosterService boosters;
  private CapabilityRegistry capabilityRegistry;
  private AceEngine aceEngine;
  private StorageService storageService;
  private ZakumSchedulerImpl scheduler;
  private AnimationService animationService;
  private BridgeManager bridgeManager;
  private ProgressionService progressionService;
  private GuiBridge guiBridge;
  private ThreadGuard threadGuard;
  private MongoDataStore dataStore;
  private BurstCacheService burstCache;
  private PlayerJoinListener playerJoinListener;
  private ProfileProvider profileProvider;
  private SecureCloudClient cloudClient;
  private CloudIdentityListener cloudIdentityListener;
  private ChatListener chatListener;
  private int cloudPollTaskId = -1;
  private MetricsMonitor metricsMonitor;
  private CloudTabRenderer cloudTabRenderer;
  private OrbisChatRenderer chatRenderer;
  private ChatBufferCache chatBufferCache;
  private ChatPacketBuffer chatPacketBuffer;
  private SocialService socialService;
  private int socialRefreshTaskId = -1;
  private EconomyService economyService;
  private GrimFlagBridge grimFlagBridge;
  private ToxicityModerationService toxicityModerationService;
  private BedrockClientDetector bedrockDetector;
  private BedrockGlyphRemapper bedrockGlyphRemapper;
  private PlayerVisualModeService visualModeService;
  private PacketCullingKernel packetCullingKernel;
  private VisualCircuitBreaker visualCircuitBreaker;
  private StressHarnessV2 stressHarness;
  private ModuleStartupValidator moduleStartupValidator;

  private MovementSampler movementSampler;

  @Override
  public void onEnable() {
    saveDefaultConfig();
    reloadConfig();

    this.settings = ZakumSettingsLoader.load(getConfig());
    var serverId = settings.server().id();
    if (serverId == null || serverId.isBlank()) {
      getLogger().severe("config.yml server.id is required. Disabling Zakum.");
      getServer().getPluginManager().disablePlugin(this);
      return;
    }

    var clock = Clock.systemUTC();
    this.asyncPool = Async.newSharedPool(getLogger());
    var async = this.asyncPool;

    this.metrics = new MetricsService(getLogger(), settings.observability().metrics(), async);
    this.metrics.start();
    this.metricsMonitor = new MetricsMonitor(metrics.registry());

    this.threadGuard = new ThreadGuard(settings.operations().threadGuard(), getLogger(), metricsMonitor);
    this.sql = new SqlManager(this, async, clock, settings, metrics.registry(), threadGuard);
    this.sql.start();

    var controlPlane = HttpControlPlaneClient.fromSettings(settings, async);
    this.capabilityRegistry = new ServicesManagerCapabilityRegistry(Bukkit.getServicesManager(), controlPlane);

    this.actionBus = new SimpleActionBus();

    this.deferred = new SqlDeferredActionService(this, sql, async);

    long entCacheMax = settings.entitlements().cache().maximumSize();
    long entTtlSeconds = settings.entitlements().cache().ttlSeconds();
    this.entitlements = new SqlEntitlementService(sql, async, (int) entCacheMax, Duration.ofSeconds(entTtlSeconds));

    this.boosters = new SqlBoosterService(this, sql, async, settings.boosters());
    this.boosters.start();

    this.scheduler = new ZakumSchedulerImpl(
      this,
      EarlySchedulerRuntime.claimOrCreateExecutor(),
      settings.operations().async(),
      getLogger(),
      metricsMonitor
    );
    this.visualCircuitBreaker = new VisualCircuitBreaker(settings.operations().circuitBreaker(), getLogger(), metricsMonitor);
    this.visualCircuitBreaker.start(scheduler, this);
    this.visualModeService = new PlayerVisualModeService(scheduler, getLogger());
    getServer().getPluginManager().registerEvents(new PlayerVisualModeListener(visualModeService), this);
    this.packetCullingKernel = new PacketCullingKernel(
      this,
      scheduler,
      settings.packets().culling(),
      metricsMonitor,
      getLogger(),
      visualModeService
    );
    this.packetCullingKernel.start();
    this.aceEngine = new ZakumAceEngine(metricsMonitor);
    this.storageService = new StorageServiceImpl(sql);
    this.animationService = new net.orbis.zakum.core.packet.AnimationService(
      this,
      scheduler,
      settings.visuals(),
      metricsMonitor,
      visualModeService
    );
    this.bridgeManager = new SimpleBridgeManager();
    this.progressionService = new ProgressionServiceImpl();
    var assets = new InMemoryAssetManager();
    assets.init();
    this.bedrockDetector = new BedrockClientDetector(settings.chat().bedrock(), getLogger());
    this.bedrockGlyphRemapper = new BedrockGlyphRemapper(assets, settings.chat().bedrock());
    var chatBufferCfg = settings.chat().bufferCache();
    this.chatBufferCache = new ChatBufferCache(
      chatBufferCfg.enabled(),
      chatBufferCfg.maximumSize(),
      chatBufferCfg.expireAfterAccessSeconds()
    );
    var localizationCfg = settings.chat().localization();
    this.chatPacketBuffer = new LocalizedChatPacketBuffer(
      assets,
      this.chatBufferCache,
      localizationCfg,
      bedrockDetector,
      bedrockGlyphRemapper,
      getLogger()
    );
    if (localizationCfg.enabled() && localizationCfg.warmupOnStart()) {
      scheduler.runAsync(chatPacketBuffer::warmup);
    }
    this.guiBridge = new ServiceBackedGuiBridge(new NoopGuiBridge());

    this.api = new ZakumApiImpl(
      this,
      new ServerIdentity(serverId),
      clock,
      async,
      sql,
      controlPlane,
      actionBus,
      entitlements,
      boosters,
      aceEngine,
      scheduler,
      storageService,
      animationService,
      bridgeManager,
      progressionService,
      assets,
      guiBridge,
      settings,
      capabilityRegistry
    );
    this.cloudTabRenderer = new CloudTabRenderer(api, assets, bedrockDetector, bedrockGlyphRemapper);
    this.chatRenderer = new OrbisChatRenderer(assets, this.chatBufferCache, bedrockDetector, bedrockGlyphRemapper);
    this.toxicityModerationService = new ToxicityModerationService(settings.moderation().toxicity(), getLogger(), metricsMonitor);
    this.chatListener = new ChatListener(api, chatRenderer, toxicityModerationService);
    getServer().getPluginManager().registerEvents(chatListener, this);

    // Register Services
    var sm = Bukkit.getServicesManager();
    sm.register(ZakumApi.class, api, this, ServicePriority.Highest);
    sm.register(net.orbis.zakum.api.actions.ActionBus.class, actionBus, this, ServicePriority.Highest);
    sm.register(net.orbis.zakum.api.entitlements.EntitlementService.class, entitlements, this, ServicePriority.Highest);
    sm.register(net.orbis.zakum.api.boosters.BoosterService.class, boosters, this, ServicePriority.Highest);
    sm.register(DeferredActionService.class, deferred, this, ServicePriority.Highest);
    sm.register(AceEngine.class, aceEngine, this, ServicePriority.Highest);
    sm.register(ZakumScheduler.class, scheduler, this, ServicePriority.Highest);
    sm.register(StorageService.class, storageService, this, ServicePriority.Highest);
    sm.register(AnimationService.class, animationService, this, ServicePriority.Highest);
    sm.register(BridgeManager.class, bridgeManager, this, ServicePriority.Highest);
    sm.register(ProgressionService.class, progressionService, this, ServicePriority.Highest);
    sm.register(GuiBridge.class, guiBridge, this, ServicePriority.Highest);
    sm.register(CapabilityRegistry.class, capabilityRegistry, this, ServicePriority.Highest);
    sm.register(ChatPacketBuffer.class, chatPacketBuffer, this, ServicePriority.Highest);
    sm.register(PlayerVisualModeService.class, visualModeService, this, ServicePriority.Highest);
    this.economyService = createGlobalEconomyService();
    if (economyService != null) {
      sm.register(EconomyService.class, economyService, this, ServicePriority.Highest);
    }

    this.dataStore = createMongoDataStore();
    if (dataStore != null) {
      visualModeService.bindDataStore(dataStore);
      sm.register(DataStore.class, dataStore, this, ServicePriority.Highest);
      this.profileProvider = new ProfileProvider(dataStore);
      getServer().getPluginManager().registerEvents(profileProvider, this);
      this.playerJoinListener = new PlayerJoinListener(scheduler, dataStore, cloudTabRenderer, profileProvider);
      getServer().getPluginManager().registerEvents(playerJoinListener, this);
    }
    this.burstCache = createBurstCacheService();
    if (burstCache != null) {
      sm.register(BurstCacheService.class, burstCache, this, ServicePriority.Highest);
    }
    long socialTtl = settings.cache().defaults().expireAfterAccessSeconds();
    if (socialTtl <= 0L) socialTtl = 300L;
    this.socialService = new CaffeineSocialService(
      scheduler,
      dataStore,
      settings.cache().defaults().maximumSize(),
      Duration.ofSeconds(socialTtl)
    );
    sm.register(SocialService.class, socialService, this, ServicePriority.Highest);
    getServer().getPluginManager().registerEvents(new SocialSnapshotLifecycleListener(socialService), this);
    startSocialRefresh();

    ZakumApiProvider.set(api);
    this.stressHarness = new StressHarnessV2(this, api, settings.operations().stress(), metricsMonitor, getLogger());
    startCloudPolling();
    startGrimBridge();
    this.moduleStartupValidator = new ModuleStartupValidator(
      this,
      settings,
      capabilityRegistry,
      scheduler,
      metricsMonitor,
      getLogger()
    );
    moduleStartupValidator.start();

    registerCoreActionEmitters(clock);

    getLogger().info("Zakum enabled. server.id=" + serverId + " db=" + sql.state());
  }

  @Override
  public void onDisable() {
    var sm = Bukkit.getServicesManager();

    if (api != null) sm.unregister(ZakumApi.class, api);
    if (actionBus != null) sm.unregister(net.orbis.zakum.api.actions.ActionBus.class, actionBus);
    if (entitlements != null) sm.unregister(net.orbis.zakum.api.entitlements.EntitlementService.class, entitlements);
    if (boosters != null) sm.unregister(net.orbis.zakum.api.boosters.BoosterService.class, boosters);
    if (deferred != null) sm.unregister(DeferredActionService.class, deferred);
    if (aceEngine != null) sm.unregister(AceEngine.class, aceEngine);
    if (scheduler != null) sm.unregister(ZakumScheduler.class, scheduler);
    if (storageService != null) sm.unregister(StorageService.class, storageService);
    if (animationService != null) sm.unregister(AnimationService.class, animationService);
    if (bridgeManager != null) sm.unregister(BridgeManager.class, bridgeManager);
    if (progressionService != null) sm.unregister(ProgressionService.class, progressionService);
    if (guiBridge != null) sm.unregister(GuiBridge.class, guiBridge);
    if (capabilityRegistry != null) sm.unregister(CapabilityRegistry.class, capabilityRegistry);
    if (dataStore != null) sm.unregister(DataStore.class, dataStore);
    if (burstCache != null) sm.unregister(BurstCacheService.class, burstCache);
    if (socialService != null) sm.unregister(SocialService.class, socialService);
    if (chatPacketBuffer != null) sm.unregister(ChatPacketBuffer.class, chatPacketBuffer);
    if (visualModeService != null) sm.unregister(PlayerVisualModeService.class, visualModeService);
    if (economyService != null) sm.unregister(EconomyService.class, economyService);
    ZakumApiProvider.clear();

    if (movementSampler != null) {
      movementSampler.stop();
      movementSampler = null;
    }
    if (playerJoinListener != null) {
      playerJoinListener.close();
      playerJoinListener = null;
    }
    if (profileProvider != null) {
      profileProvider.clear();
      profileProvider = null;
    }
    if (packetCullingKernel != null) {
      packetCullingKernel.close();
      packetCullingKernel = null;
    }
    if (cloudPollTaskId > 0 && scheduler != null) {
      scheduler.cancelTask(cloudPollTaskId);
    }
    cloudPollTaskId = -1;
    if (socialRefreshTaskId > 0 && scheduler != null) {
      scheduler.cancelTask(socialRefreshTaskId);
    }
    socialRefreshTaskId = -1;
    cloudIdentityListener = null;
    if (cloudClient != null) {
      try { cloudClient.close(); } catch (Exception ignored) {}
    }
    cloudClient = null;
    cloudTabRenderer = null;
    chatRenderer = null;
    chatBufferCache = null;
    chatPacketBuffer = null;
    chatListener = null;
    metricsMonitor = null;
    if (economyService instanceof AutoCloseable closeable) {
      try {
        closeable.close();
      } catch (Exception ignored) {
        // ignore
      }
    }
    economyService = null;
    grimFlagBridge = null;
    toxicityModerationService = null;
    bedrockDetector = null;
    bedrockGlyphRemapper = null;
    visualModeService = null;
    if (visualCircuitBreaker != null) {
      visualCircuitBreaker.stop(scheduler);
      visualCircuitBreaker = null;
    }
    if (stressHarness != null) {
      try { stressHarness.close(); } catch (Throwable ignored) {}
      stressHarness = null;
    }
    if (moduleStartupValidator != null) {
      moduleStartupValidator.stop();
      moduleStartupValidator = null;
    }

    if (metrics != null) metrics.stop();

    if (boosters != null) boosters.shutdown();
    if (dataStore != null) {
      dataStore.close();
      dataStore = null;
    }
    if (burstCache instanceof AutoCloseable closeable) {
      try {
        closeable.close();
      } catch (Exception ignored) {}
      burstCache = null;
    }
    if (scheduler != null) scheduler.shutdown();

    if (sql != null) sql.shutdown();
    if (asyncPool != null) asyncPool.shutdownNow();

    api = null;
    settings = null;
    metrics = null;
    sql = null;
    asyncPool = null;
    actionBus = null;
    entitlements = null;
    boosters = null;
    deferred = null;
    capabilityRegistry = null;
    aceEngine = null;
    storageService = null;
    animationService = null;
    bridgeManager = null;
    progressionService = null;
    guiBridge = null;
    threadGuard = null;
    scheduler = null;
    socialService = null;
  }

  private void startCloudPolling() {
    var cloud = settings.cloud();
    if (!cloud.enabled()) return;

    if (cloud.baseUrl() == null || cloud.baseUrl().isBlank()) {
      getLogger().warning("Cloud polling enabled but cloud.baseUrl is blank.");
      return;
    }
    if (cloud.networkSecret() == null || cloud.networkSecret().isBlank()) {
      getLogger().warning("Cloud polling enabled but cloud.networkSecret is blank.");
      return;
    }
    if (cloud.serverId() == null || cloud.serverId().isBlank()) {
      getLogger().warning("Cloud polling enabled but cloud.serverId is blank.");
      return;
    }

    this.cloudClient = new SecureCloudClient(api, cloud, this, getLogger(), metricsMonitor);
    cloudClient.start();
    if (cloud.identityOnJoin()) {
      this.cloudIdentityListener = new CloudIdentityListener(api, cloudClient, cloudTabRenderer, getLogger());
      getServer().getPluginManager().registerEvents(cloudIdentityListener, this);
    }

    int intervalTicks = Math.max(1, cloud.pollIntervalTicks());
    this.cloudPollTaskId = scheduler.runTaskTimerAsynchronously(this, cloudClient::poll, intervalTicks, intervalTicks);
    scheduler.runAsync(cloudClient::poll);
  }

  private void startSocialRefresh() {
    if (socialService == null || scheduler == null) return;
    var periodic = settings.social().periodicRefresh();
    if (periodic == null || !periodic.enabled()) return;
    int intervalTicks = Math.max(20, periodic.intervalSeconds() * 20);
    this.socialRefreshTaskId = scheduler.runTaskTimerAsynchronously(
      this,
      this::refreshOnlineSocialSnapshots,
      intervalTicks,
      intervalTicks
    );
  }

  private void refreshOnlineSocialSnapshots() {
    if (socialService == null) return;
    for (Player player : Bukkit.getOnlinePlayers()) {
      socialService.refreshAsync(player.getUniqueId());
    }
    if (metricsMonitor != null) {
      metricsMonitor.recordAction("social_refresh_tick");
    }
  }

  private void startGrimBridge() {
    if (!getConfig().getBoolean("anticheat.grim.enabled", false)) return;
    if (Bukkit.getPluginManager().getPlugin("GrimAC") == null && Bukkit.getPluginManager().getPlugin("Grim") == null) {
      getLogger().warning("Grim bridge enabled but GrimAC is not installed.");
      return;
    }

    List<String> script = GrimFlagBridge.normalizeScript(getConfig().getStringList("anticheat.grim.aceScript"));
    this.grimFlagBridge = new GrimFlagBridge(api, this, script, getLogger(), metricsMonitor);
    grimFlagBridge.register();
  }

  private EconomyService createGlobalEconomyService() {
    var global = settings.economy().global();
    if (!global.enabled()) return null;
    if (global.redisUri() == null || global.redisUri().isBlank()) {
      getLogger().warning("Global economy enabled but economy.global.redisUri is blank.");
      return null;
    }

    try {
      JedisPool jedisPool = new JedisPool(URI.create(global.redisUri()));
      return new RedisGlobalEconomyService(
        jedisPool,
        global.keyPrefix(),
        global.scale(),
        global.updatesChannel(),
        metricsMonitor,
        getLogger(),
        threadGuard
      );
    } catch (Throwable ex) {
      getLogger().warning("Failed to initialize global economy: " + ex.getMessage());
      return null;
    }
  }

  private MongoDataStore createMongoDataStore() {
    var cfg = settings == null ? null : settings.dataStore();
    if (cfg == null || !cfg.enabled()) return null;

    String mongoUri = cfg.mongoUri();
    String mongoDatabase = cfg.mongoDatabase();
    String redisUri = cfg.redisUri();
    if (mongoUri.isBlank() || mongoDatabase.isBlank() || redisUri.isBlank()) {
      getLogger().warning("DataStore is enabled but datastore.mongoUri/datastore.mongoDatabase/datastore.redisUri are not fully configured.");
      return null;
    }

    try {
      MongoClient mongoClient = MongoClients.create(mongoUri);
      JedisPool jedisPool = new JedisPool(URI.create(redisUri));
      return new MongoDataStore(mongoClient, jedisPool, mongoDatabase, scheduler, threadGuard, cfg.sessionKeyPrefix());
    } catch (Throwable ex) {
      getLogger().warning("Failed to initialize Mongo DataStore: " + ex.getMessage());
      return null;
    }
  }

  private BurstCacheService createBurstCacheService() {
    var burst = settings == null || settings.cache() == null ? null : settings.cache().burst();
    if (burst == null || !burst.enabled()) return null;
    String redisUri = firstNonBlank(
      burst.redisUri(),
      settings.dataStore() == null ? "" : settings.dataStore().redisUri()
    );
    if (redisUri == null || redisUri.isBlank()) {
      getLogger().warning("Burst cache is enabled but cache.burst.redisUri (or datastore.redisUri fallback) is blank.");
    }
    return new RedisBurstCacheService(
      scheduler,
      threadGuard,
      metricsMonitor,
      getLogger(),
      burst.enabled(),
      redisUri,
      burst.keyPrefix(),
      burst.defaultTtlSeconds(),
      burst.maximumLocalEntries()
    );
  }

  private static String firstNonBlank(String... values) {
    if (values == null) return "";
    for (String value : values) {
      if (value != null && !value.isBlank()) return value.trim();
    }
    return "";
  }

  // --- Command Handling & Helpers omitted for brevity (kept standard) ---
  // The minimal necessary for compilation is onEnable/onDisable and cloud status.

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (command.getName().equalsIgnoreCase("perfmode")) {
      return handlePerfModeCommand(sender, args);
    }
    if (!command.getName().equalsIgnoreCase("zakum")) return false;

    if (args.length >= 2 && args[0].equalsIgnoreCase("cloud") && args[1].equalsIgnoreCase("status")) {
      if (!sender.hasPermission("zakum.admin")) {
        sender.sendMessage("No permission.");
        return true;
      }
      sendCloudStatus(sender);
      return true;
    }
    if (args.length >= 2 && args[0].equalsIgnoreCase("cloud") && args[1].equalsIgnoreCase("flush")) {
      if (!sender.hasPermission("zakum.admin")) {
        sender.sendMessage("No permission.");
        return true;
      }
      if (cloudClient == null) {
        sender.sendMessage("Cloud client is offline.");
        return true;
      }
      boolean ack = cloudClient.requestAckFlush();
      boolean dedupe = cloudClient.requestDedupePersist();
      sender.sendMessage("Cloud flush queued (ack=" + ack + ", dedupe=" + dedupe + ").");
      return true;
    }

    if (args.length >= 2 && args[0].equalsIgnoreCase("controlplane") && args[1].equalsIgnoreCase("status")) {
      if (!sender.hasPermission("zakum.admin")) {
        sender.sendMessage("No permission.");
        return true;
      }
      sendControlPlaneStatus(sender);
      return true;
    }

    if (args.length >= 2 && args[0].equalsIgnoreCase("perf") && args[1].equalsIgnoreCase("status")) {
      if (!sender.hasPermission("zakum.admin")) {
        sender.sendMessage("No permission.");
        return true;
      }
      sendPerfStatus(sender);
      return true;
    }

    if (args.length >= 2 && args[0].equalsIgnoreCase("stress")) {
      if (!sender.hasPermission("zakum.admin")) {
        sender.sendMessage("No permission.");
        return true;
      }
      return handleStressCommand(sender, args);
    }

    if (args.length >= 2 && args[0].equalsIgnoreCase("chatbuffer") && args[1].equalsIgnoreCase("status")) {
      if (!sender.hasPermission("zakum.admin")) {
        sender.sendMessage("No permission.");
        return true;
      }
      sendChatBufferStatus(sender);
      return true;
    }

    if (args.length >= 2 && args[0].equalsIgnoreCase("chatbuffer") && args[1].equalsIgnoreCase("warmup")) {
      if (!sender.hasPermission("zakum.admin")) {
        sender.sendMessage("No permission.");
        return true;
      }
      warmupChatBuffer(sender);
      return true;
    }

    if (args.length >= 2 && args[0].equalsIgnoreCase("economy")) {
      if (!sender.hasPermission("zakum.admin")) {
        sender.sendMessage("No permission.");
        return true;
      }
      return handleEconomyCommand(sender, args);
    }

    if (args.length >= 2 && args[0].equalsIgnoreCase("packetcull")) {
      if (!sender.hasPermission("zakum.admin")) {
        sender.sendMessage("No permission.");
        return true;
      }
      return handlePacketCullCommand(sender, args);
    }

    if (args.length >= 2 && args[0].equalsIgnoreCase("datahealth") && args[1].equalsIgnoreCase("status")) {
      if (!sender.hasPermission("zakum.admin")) {
        sender.sendMessage("No permission.");
        return true;
      }
      sendDataHealthStatus(sender);
      return true;
    }

    if (args.length >= 2 && args[0].equalsIgnoreCase("burstcache")) {
      if (!sender.hasPermission("zakum.admin")) {
        sender.sendMessage("No permission.");
        return true;
      }
      return handleBurstCacheCommand(sender, args);
    }

    if (args.length >= 2 && args[0].equalsIgnoreCase("modules")) {
      if (!sender.hasPermission("zakum.admin")) {
        sender.sendMessage("No permission.");
        return true;
      }
      return handleModulesCommand(sender, args);
    }

    if (args.length >= 2 && args[0].equalsIgnoreCase("tasks") && args[1].equalsIgnoreCase("status")) {
      if (!sender.hasPermission("zakum.admin")) {
        sender.sendMessage("No permission.");
        return true;
      }
      sendTaskRegistryStatus(sender);
      return true;
    }

    if (args.length >= 2 && args[0].equalsIgnoreCase("async")) {
      if (!sender.hasPermission("zakum.admin")) {
        sender.sendMessage("No permission.");
        return true;
      }
      return handleAsyncCommand(sender, args);
    }

    if (args.length >= 2 && args[0].equalsIgnoreCase("threadguard")) {
      if (!sender.hasPermission("zakum.admin")) {
        sender.sendMessage("No permission.");
        return true;
      }
      return handleThreadGuardCommand(sender, args);
    }

    sender.sendMessage("Usage: /" + label + " cloud status|flush");
    sender.sendMessage("Usage: /" + label + " controlplane status");
    sender.sendMessage("Usage: /" + label + " perf status");
    sender.sendMessage("Usage: /" + label + " stress start [iterations] [virtualPlayers]");
    sender.sendMessage("Usage: /" + label + " stress stop");
    sender.sendMessage("Usage: /" + label + " stress status");
    sender.sendMessage("Usage: /" + label + " chatbuffer status|warmup");
    sender.sendMessage("Usage: /" + label + " economy status|balance|set|add|take ...");
    sender.sendMessage("Usage: /" + label + " packetcull status|enable|disable");
    sender.sendMessage("Usage: /" + label + " datahealth status");
    sender.sendMessage("Usage: /" + label + " burstcache status|enable|disable");
    sender.sendMessage("Usage: /" + label + " modules status|validate");
    sender.sendMessage("Usage: /" + label + " tasks status");
    sender.sendMessage("Usage: /" + label + " async status|enable|disable");
    sender.sendMessage("Usage: /" + label + " threadguard status|enable|disable");
    sender.sendMessage("Usage: /perfmode <auto|on|off> [player]");
    return true;
  }

  public SecureCloudClient getCloudClient() {
    return cloudClient;
  }

  public int getCloudPollTaskId() {
    return cloudPollTaskId;
  }

  public VisualCircuitBreaker getVisualCircuitBreaker() {
    return visualCircuitBreaker;
  }

  public StressHarnessV2 getStressHarness() {
    return stressHarness;
  }

  public ChatBufferCache getChatBufferCache() {
    return chatBufferCache;
  }

  public ChatPacketBuffer getChatPacketBuffer() {
    return chatPacketBuffer;
  }

  public PacketCullingKernel getPacketCullingKernel() {
    return packetCullingKernel;
  }

  public ThreadGuard getThreadGuard() {
    return threadGuard;
  }

  public PlayerVisualModeService getVisualModeService() {
    return visualModeService;
  }

  public EconomyService getEconomyService() {
    return economyService;
  }

  public ZakumSchedulerImpl getSchedulerRuntime() {
    return scheduler;
  }

  public BurstCacheService getBurstCacheService() {
    return burstCache;
  }

  public ModuleStartupValidator getModuleStartupValidator() {
    return moduleStartupValidator;
  }

  public CompletableFuture<DataHealthSnapshot> collectDataHealth() {
    if (scheduler == null) {
      return CompletableFuture.completedFuture(DataHealthSnapshot.empty("scheduler_offline"));
    }
    return scheduler.supplyAsync(() -> {
      String sqlState = sql == null ? "OFFLINE" : String.valueOf(sql.state());
      int poolActive = 0;
      int poolIdle = 0;
      if (sql != null) {
        var pool = sql.poolStats();
        poolActive = pool.active();
        poolIdle = pool.idle();
      }

      boolean cloudConfigured = settings != null && settings.cloud().enabled();
      boolean cloudOnline = cloudClient != null;
      boolean datastoreOnline = dataStore != null;
      boolean socialOnline = socialService != null;
      boolean threadGuardOnline = threadGuard != null;

      boolean economyRegistered = economyService != null;
      boolean economyAvailable = false;
      String economyError = "";
      if (economyService != null) {
        try {
          economyAvailable = economyService.available();
        } catch (Throwable ex) {
          economyError = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        }
      }

      var packets = Bukkit.getServicesManager().load(net.orbis.zakum.api.packets.PacketService.class);
      boolean packetsOnline = packets != null;
      String packetBackend = packets == null ? "offline" : packets.backend();
      int packetHooks = packets == null ? 0 : packets.hookCount();

      long threadGuardViolations = threadGuard == null ? 0L : threadGuard.snapshot().violations();
      long asyncRejected = scheduler.asyncSnapshot().rejected();
      boolean moduleValidatorEnabled = false;
      boolean moduleValidatorHealthy = false;
      int moduleValidatorWarnings = 0;
      int moduleValidatorCriticals = 0;
      if (moduleStartupValidator != null) {
        var modSnap = moduleStartupValidator.snapshot();
        moduleValidatorEnabled = modSnap.configuredEnabled();
        moduleValidatorHealthy = modSnap.healthy();
        moduleValidatorWarnings = modSnap.warnings();
        moduleValidatorCriticals = modSnap.criticals();
      }

      if (metricsMonitor != null) {
        metricsMonitor.recordAction("datahealth_status");
      }

      return new DataHealthSnapshot(
        sqlState,
        poolActive,
        poolIdle,
        cloudConfigured,
        cloudOnline,
        datastoreOnline,
        socialOnline,
        economyRegistered,
        economyAvailable,
        economyError,
        packetsOnline,
        packetBackend,
        packetHooks,
        threadGuardOnline,
        threadGuardViolations,
        asyncRejected,
        moduleValidatorEnabled,
        moduleValidatorHealthy,
        moduleValidatorWarnings,
        moduleValidatorCriticals,
        ""
      );
    });
  }

  public List<String> dataHealthLines(DataHealthSnapshot snap) {
    if (snap == null) {
      return List.of("Data health unavailable.");
    }
    List<String> lines = new ArrayList<>();
    lines.add("Zakum Data Health");
    lines.add("sql.state=" + snap.sqlState());
    lines.add("sql.pool.active=" + snap.poolActive());
    lines.add("sql.pool.idle=" + snap.poolIdle());
    lines.add("cloud.configured=" + snap.cloudConfigured());
    lines.add("cloud.online=" + snap.cloudOnline());
    lines.add("datastore.online=" + snap.datastoreOnline());
    lines.add("social.online=" + snap.socialOnline());
    lines.add("economy.registered=" + snap.economyRegistered());
    lines.add("economy.available=" + snap.economyAvailable());
    if (snap.economyError() != null && !snap.economyError().isBlank()) {
      lines.add("economy.error=" + snap.economyError());
    }
    lines.add("packets.online=" + snap.packetsOnline());
    lines.add("packets.backend=" + snap.packetBackend());
    lines.add("packets.hooks=" + snap.packetHooks());
    lines.add("threadguard.online=" + snap.threadGuardOnline());
    lines.add("threadguard.violations=" + snap.threadGuardViolations());
    lines.add("async.rejected=" + snap.asyncRejected());
    lines.add("modules.validator.enabled=" + snap.moduleValidatorEnabled());
    lines.add("modules.validator.healthy=" + snap.moduleValidatorHealthy());
    lines.add("modules.validator.warnings=" + snap.moduleValidatorWarnings());
    lines.add("modules.validator.criticals=" + snap.moduleValidatorCriticals());
    if (snap.error() != null && !snap.error().isBlank()) {
      lines.add("error=" + snap.error());
    }
    return lines;
  }

  private boolean handlePerfModeCommand(CommandSender sender, String[] args) {
    if (visualModeService == null) {
      sender.sendMessage("Performance mode service is not available.");
      return true;
    }
    if (args.length < 1) {
      sender.sendMessage("Usage: /perfmode <auto|on|off> [player]");
      return true;
    }

    var mode = PlayerVisualModeService.Mode.fromInput(args[0]);
    Player target;
    if (args.length >= 2) {
      if (!sender.hasPermission("zakum.admin")) {
        sender.sendMessage("No permission.");
        return true;
      }
      target = Bukkit.getPlayerExact(args[1]);
      if (target == null) {
        sender.sendMessage("Player not found: " + args[1]);
        return true;
      }
    } else if (sender instanceof Player player) {
      if (!sender.hasPermission("zakum.perfmode")) {
        sender.sendMessage("No permission.");
        return true;
      }
      target = player;
    } else {
      sender.sendMessage("Console usage: /perfmode <auto|on|off> <player>");
      return true;
    }

    visualModeService.setMode(target.getUniqueId(), mode);
    sender.sendMessage("Performance mode for " + target.getName() + " set to " + mode.displayName() + ".");
    if (!target.equals(sender)) {
      target.sendMessage("Your performance mode is now " + mode.displayName() + ".");
    }
    return true;
  }

  private void sendCloudStatus(CommandSender sender) {
    sender.sendMessage("Zakum Cloud Status");
    sender.sendMessage("enabled=" + settings.cloud().enabled());
    sender.sendMessage("configuredServerId=" + settings.cloud().serverId());
    sender.sendMessage("pollTaskId=" + cloudPollTaskId);

    if (cloudClient == null) {
      sender.sendMessage("client=offline (cloud disabled or not configured)");
      return;
    }

    var snap = cloudClient.statusSnapshot();
    sender.sendMessage("client=online");
    sender.sendMessage("baseUrl=" + snap.baseUrl());
    sender.sendMessage("serverId=" + snap.serverId());
    sender.sendMessage("lastPollAttempt=" + formatEpochMillis(snap.lastPollAttemptMs()));
    sender.sendMessage("lastPollSuccess=" + formatEpochMillis(snap.lastPollSuccessMs()));
    sender.sendMessage("lastHttpStatus=" + snap.lastHttpStatus());
    sender.sendMessage("lastBatchSize=" + snap.lastBatchSize());
    sender.sendMessage("totalQueueActions=" + snap.totalQueueActions());
    sender.sendMessage("duplicateQueueSkips=" + snap.duplicateQueueSkips());
    sender.sendMessage("inflightQueueSkips=" + snap.inflightQueueSkips());
    sender.sendMessage("offlineQueueSkips=" + snap.offlineQueueSkips());
    sender.sendMessage("invalidQueueSkips=" + snap.invalidQueueSkips());
    sender.sendMessage("queueFailures=" + snap.queueFailures());
    sender.sendMessage("processedIds=" + snap.processedIds());
    sender.sendMessage("inflightIds=" + snap.inflightIds());
    sender.sendMessage("dedupe.persist.enabled=" + snap.dedupePersistEnabled());
    sender.sendMessage("dedupe.persist.file=" + snap.dedupePersistFile());
    sender.sendMessage("dedupe.persist.flushSeconds=" + snap.dedupePersistFlushSeconds());
    sender.sendMessage("dedupe.persist.lastLoad=" + formatEpochMillis(snap.lastDedupeLoadMs()));
    sender.sendMessage("dedupe.persist.lastSave=" + formatEpochMillis(snap.lastDedupePersistMs()));
    sender.sendMessage("dedupe.persist.errors=" + snap.dedupePersistErrors());
    String dedupeErr = snap.lastDedupePersistError();
    sender.sendMessage("dedupe.persist.lastError=" + (dedupeErr == null || dedupeErr.isBlank() ? "none" : dedupeErr));
    sender.sendMessage("ack.enabled=" + snap.ackEnabled());
    sender.sendMessage("ack.disabled=" + snap.ackDisabled());
    sender.sendMessage("ack.pending=" + snap.pendingAcks());
    sender.sendMessage("ack.queued=" + snap.ackQueued());
    sender.sendMessage("ack.sent=" + snap.ackSent());
    sender.sendMessage("ack.failed=" + snap.ackFailed());
    sender.sendMessage("ack.retried=" + snap.ackRetried());
    sender.sendMessage("ack.dropped=" + snap.ackDropped());
    sender.sendMessage("ack.lastAttempt=" + formatEpochMillis(snap.lastAckAttemptMs()));
    sender.sendMessage("ack.lastSuccess=" + formatEpochMillis(snap.lastAckSuccessMs()));
    sender.sendMessage("ack.lastStatus=" + snap.lastAckStatus());
    String ackErr = snap.lastAckError();
    sender.sendMessage("ack.lastError=" + (ackErr == null || ackErr.isBlank() ? "none" : ackErr));
    String err = snap.lastError();
    sender.sendMessage("lastError=" + (err == null || err.isBlank() ? "none" : err));
  }

  public List<String> controlPlaneStatusLines() {
    List<String> lines = new ArrayList<>();
    lines.add("Zakum ControlPlane Status");
    lines.add("configured.enabled=" + (settings != null && settings.controlPlane().enabled()));
    lines.add("configured.baseUrl=" + (settings == null ? "" : settings.controlPlane().baseUrl()));
    HttpControlPlaneClient client = resolveControlPlaneClient();
    if (client == null) {
      lines.add("client=offline");
      return lines;
    }

    var snap = client.snapshot();
    lines.add("client=online");
    lines.add("baseUrl=" + client.baseUrl());
    lines.add("resilience.enabled=" + snap.resilienceEnabled());
    lines.add("resilience.circuitState=" + snap.circuitState());
    lines.add("calls=" + snap.calls());
    lines.add("successes=" + snap.successes());
    lines.add("failures=" + snap.failures());
    lines.add("retries=" + snap.retries());
    lines.add("shortCircuits=" + snap.shortCircuits());
    lines.add("lastStatus=" + snap.lastStatus());
    lines.add("lastLatencyMs=" + snap.lastLatencyMs());
    lines.add("lastFailureAt=" + formatEpochMillis(snap.lastFailureAtMs()));
    String err = snap.lastError();
    lines.add("lastError=" + (err == null || err.isBlank() ? "none" : err));
    return lines;
  }

  private void sendControlPlaneStatus(CommandSender sender) {
    for (String line : controlPlaneStatusLines()) {
      sender.sendMessage(line);
    }
  }

  private HttpControlPlaneClient resolveControlPlaneClient() {
    if (api == null) return null;
    return api.controlPlane()
      .filter(HttpControlPlaneClient.class::isInstance)
      .map(HttpControlPlaneClient.class::cast)
      .orElse(null);
  }

  private void sendPerfStatus(CommandSender sender) {
    sender.sendMessage("Zakum Performance Status");
    var breakerCfg = settings.operations().circuitBreaker();
    sender.sendMessage("circuit.enabled=" + breakerCfg.enabled());

    if (visualCircuitBreaker == null) {
      sender.sendMessage("circuit=offline");
      return;
    }

    var snap = visualCircuitBreaker.snapshot();
    sender.sendMessage("circuit.open=" + snap.open());
    sender.sendMessage("circuit.lastTps=" + String.format(java.util.Locale.ROOT, "%.2f", snap.lastTps()));
    sender.sendMessage("circuit.reason=" + (snap.reason() == null || snap.reason().isBlank() ? "none" : snap.reason()));
    sender.sendMessage("circuit.changedAt=" + formatEpochMillis(snap.changedAtMs()));
    sender.sendMessage("circuit.taskId=" + snap.taskId());
  }

  private boolean handleStressCommand(CommandSender sender, String[] args) {
    if (stressHarness == null) {
      sender.sendMessage("Stress harness is not available.");
      return true;
    }
    if (args.length < 2) {
      sender.sendMessage("Usage: /zakum stress start [iterations] [virtualPlayers]");
      sender.sendMessage("Usage: /zakum stress stop");
      sender.sendMessage("Usage: /zakum stress status");
      sender.sendMessage("Usage: /zakum stress report [label]");
      return true;
    }

    String sub = args[1].toLowerCase(java.util.Locale.ROOT);
    if (sub.equals("start") || sub.equals("run")) {
      int iterations = parseInt(args.length >= 3 ? args[2] : null, settings.operations().stress().defaultIterations());
      Integer virtualPlayers = null;
      if (args.length >= 4) {
        int parsed = parseInt(args[3], -1);
        if (parsed > 0) virtualPlayers = parsed;
      }
      var result = stressHarness.start(iterations, virtualPlayers);
      sender.sendMessage(result.message());
      return true;
    }

    if (sub.equals("stop")) {
      var result = stressHarness.stop("command");
      sender.sendMessage(result.message());
      return true;
    }

    if (sub.equals("status")) {
      sendStressStatus(sender);
      return true;
    }

    if (sub.equals("report")) {
      String label = null;
      if (args.length >= 3) {
        StringBuilder sb = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
          if (i > 2) sb.append(' ');
          sb.append(args[i]);
        }
        label = sb.toString();
      }
      sender.sendMessage("Generating stress report...");
      String finalLabel = label;
      scheduler.runAsync(() -> {
        StressHarnessV2.ReportResult result = stressHarness.writeReport(finalLabel);
        scheduler.runGlobal(() -> sender.sendMessage(result.message()));
      });
      return true;
    }

    sender.sendMessage("Usage: /zakum stress start [iterations] [virtualPlayers]");
    sender.sendMessage("Usage: /zakum stress stop");
    sender.sendMessage("Usage: /zakum stress status");
    sender.sendMessage("Usage: /zakum stress report [label]");
    return true;
  }

  private void sendStressStatus(CommandSender sender) {
    if (stressHarness == null) {
      sender.sendMessage("Stress harness is not available.");
      return;
    }
    var snap = stressHarness.snapshot();
    sender.sendMessage("Zakum Stress Harness v2");
    sender.sendMessage("running=" + snap.running());
    sender.sendMessage("plannedIterations=" + snap.plannedIterations());
    sender.sendMessage("scheduledIterations=" + snap.scheduledIterations());
    sender.sendMessage("completedIterations=" + snap.completedIterations());
    sender.sendMessage("errors=" + snap.errorCount());
    sender.sendMessage("skippedNoPlayer=" + snap.skippedNoPlayer());
    sender.sendMessage("virtualPlayers=" + snap.virtualPlayers());
    sender.sendMessage("onlinePlayers=" + snap.onlinePlayers());
    sender.sendMessage("iterationsPerTick=" + snap.iterationsPerTick());
    sender.sendMessage("lastTps=" + String.format(java.util.Locale.ROOT, "%.2f", snap.lastTps()));
    sender.sendMessage("stopReason=" + snap.stopReason());
    sender.sendMessage("startedAt=" + formatEpochMillis(snap.startedAtMs()));
    sender.sendMessage("stopAt=" + formatEpochMillis(snap.stopAtMs()));
    sender.sendMessage("nextAllowedAt=" + formatEpochMillis(snap.nextAllowedAtMs()));

    String lastErr = snap.lastError();
    sender.sendMessage("lastError=" + (lastErr == null || lastErr.isBlank() ? "none" : lastErr));
    if (snap.lastErrorAtMs() > 0L) {
      sender.sendMessage("lastErrorAt=" + formatEpochMillis(snap.lastErrorAtMs()));
    }

    if (snap.scenarioCounts() != null && !snap.scenarioCounts().isEmpty()) {
      sender.sendMessage("scenarioCounts=" + snap.scenarioCounts());
    }
  }

  private void sendChatBufferStatus(CommandSender sender) {
    sender.sendMessage("Zakum Chat Buffer Status");
    if (chatBufferCache != null) {
      var parse = chatBufferCache.stats();
      sender.sendMessage("parse.enabled=" + parse.enabled());
      sender.sendMessage("parse.requests=" + parse.requests());
      sender.sendMessage("parse.hits=" + parse.hits());
      sender.sendMessage("parse.misses=" + parse.misses());
      sender.sendMessage("parse.estimatedSize=" + parse.estimatedSize());
    } else {
      sender.sendMessage("parse.cache=offline");
    }

    if (chatPacketBuffer instanceof LocalizedChatPacketBuffer localized) {
      var prepared = localized.stats();
      sender.sendMessage("prepared.templateKeys=" + prepared.templateKeys());
      sender.sendMessage("prepared.cacheSize=" + prepared.preparedCacheSize());
      sender.sendMessage("prepared.resolveRequests=" + prepared.resolveRequests());
      sender.sendMessage("prepared.resolveHits=" + prepared.resolveHits());
      sender.sendMessage("prepared.resolveMisses=" + prepared.resolveMisses());
      sender.sendMessage("prepared.sends=" + prepared.sends());
      sender.sendMessage("prepared.packetDispatchEnabled=" + prepared.packetDispatchEnabled());
      sender.sendMessage("prepared.packetDispatchAvailable=" + prepared.packetDispatchAvailable());
      sender.sendMessage("prepared.packetSends=" + prepared.packetSends());
      sender.sendMessage("prepared.fallbackSends=" + prepared.fallbackSends());
      sender.sendMessage("prepared.packetFailures=" + prepared.packetFailures());
      return;
    }
    sender.sendMessage("prepared.buffer=not-localized");
  }

  private void warmupChatBuffer(CommandSender sender) {
    if (chatPacketBuffer == null) {
      sender.sendMessage("Chat buffer is not available.");
      return;
    }
    scheduler.runAsync(() -> {
      chatPacketBuffer.warmup();
      scheduler.runGlobal(() -> sender.sendMessage("Chat buffer warmup finished."));
    });
    sender.sendMessage("Chat buffer warmup started.");
  }

  private boolean handleEconomyCommand(CommandSender sender, String[] args) {
    if (economyService == null) {
      sender.sendMessage("Economy capability is not registered.");
      return true;
    }

    String sub = args[1].toLowerCase(java.util.Locale.ROOT);
    if (sub.equals("status")) {
      sender.sendMessage("Economy status");
      sender.sendMessage("available=" + economyService.available());
      return true;
    }

    if (sub.equals("balance")) {
      if (args.length < 3) {
        sender.sendMessage("Usage: /zakum economy balance <player|uuid>");
        return true;
      }
      UUID playerId = resolveUuid(sender, args[2]);
      if (playerId == null) return true;
      double balance = economyService.balance(playerId);
      sender.sendMessage("balance[" + playerId + "]=" + formatMoney(balance));
      return true;
    }

    if (sub.equals("add") || sub.equals("take") || sub.equals("set")) {
      if (args.length < 4) {
        sender.sendMessage("Usage: /zakum economy " + sub + " <player|uuid> <amount>");
        return true;
      }
      UUID playerId = resolveUuid(sender, args[2]);
      if (playerId == null) return true;
      double amount = parseDouble(args[3], -1.0d);
      if (!Double.isFinite(amount) || amount < 0.0d) {
        sender.sendMessage("Amount must be a non-negative number.");
        return true;
      }

      if (sub.equals("set")) {
        double current = economyService.balance(playerId);
        double target = Math.max(0.0d, amount);
        if (target > current) {
          var result = economyService.deposit(playerId, target - current);
          if (!result.success()) {
            sender.sendMessage("Set failed: " + result.error());
            return true;
          }
        } else if (target < current) {
          var result = economyService.withdraw(playerId, current - target);
          if (!result.success()) {
            sender.sendMessage("Set failed: " + result.error());
            return true;
          }
        }
        sender.sendMessage("balance[" + playerId + "]=" + formatMoney(target));
        return true;
      }

      var result = sub.equals("add")
        ? economyService.deposit(playerId, amount)
        : economyService.withdraw(playerId, amount);
      if (!result.success()) {
        sender.sendMessage("Operation failed: " + result.error());
        return true;
      }
      sender.sendMessage("balance[" + playerId + "]=" + formatMoney(result.newBalance()));
      return true;
    }

    sender.sendMessage("Usage: /zakum economy status");
    sender.sendMessage("Usage: /zakum economy balance <player|uuid>");
    sender.sendMessage("Usage: /zakum economy set <player|uuid> <amount>");
    sender.sendMessage("Usage: /zakum economy add <player|uuid> <amount>");
    sender.sendMessage("Usage: /zakum economy take <player|uuid> <amount>");
    return true;
  }

  private boolean handlePacketCullCommand(CommandSender sender, String[] args) {
    if (packetCullingKernel == null) {
      sender.sendMessage("Packet culling kernel is offline.");
      return true;
    }
    if (args.length < 2) {
      sender.sendMessage("Usage: /zakum packetcull status|enable|disable|sample|refresh");
      return true;
    }
    String sub = args[1].toLowerCase(java.util.Locale.ROOT);
    if (sub.equals("status")) {
      sendPacketCullStatus(sender);
      return true;
    }
    if (sub.equals("enable")) {
      packetCullingKernel.setRuntimeEnabled(true);
      sender.sendMessage("Packet culling runtime enabled.");
      return true;
    }
    if (sub.equals("disable")) {
      packetCullingKernel.setRuntimeEnabled(false);
      sender.sendMessage("Packet culling runtime disabled.");
      return true;
    }
    if (sub.equals("refresh")) {
      Player target = resolvePacketCullTarget(sender, args);
      if (target == null) return true;
      packetCullingKernel.requestSample(target);
      sender.sendMessage("Packet culling sample queued for " + target.getName() + ".");
      return true;
    }
    if (sub.equals("sample")) {
      Player target = resolvePacketCullTarget(sender, args);
      if (target == null) return true;
      var sample = packetCullingKernel.sampleSnapshot(target.getUniqueId());
      if (sample == null) {
        packetCullingKernel.requestSample(target);
        sender.sendMessage("No sample yet for " + target.getName() + ". Sample queued; run again shortly.");
        return true;
      }
      long ageMs = Math.max(0L, System.currentTimeMillis() - sample.sampledAtMs());
      int threshold = packetCullingKernel.thresholdFor(sample.mode());
      boolean runtimeEnabled = packetCullingKernel.runtimeEnabled();
      boolean configuredEnabled = settings.packets().culling().enabled();
      boolean wouldDrop = runtimeEnabled
        && configuredEnabled
        && !sample.bypass()
        && (!settings.packets().culling().respectPerfMode() || sample.mode() != PlayerVisualModeService.Mode.QUALITY)
        && ageMs <= settings.packets().culling().maxSampleAgeMs()
        && sample.density() >= threshold;

      sender.sendMessage("Packet cull sample: " + target.getName());
      sender.sendMessage("density=" + sample.density());
      sender.sendMessage("ageMs=" + ageMs);
      sender.sendMessage("mode=" + sample.mode().displayName());
      sender.sendMessage("bypass=" + sample.bypass());
      sender.sendMessage("threshold=" + threshold);
      sender.sendMessage("configuredEnabled=" + configuredEnabled);
      sender.sendMessage("runtimeEnabled=" + runtimeEnabled);
      sender.sendMessage("wouldDrop=" + wouldDrop);
      return true;
    }
    sender.sendMessage("Usage: /zakum packetcull status|enable|disable|sample|refresh");
    return true;
  }

  private boolean handleThreadGuardCommand(CommandSender sender, String[] args) {
    if (threadGuard == null) {
      sender.sendMessage("Thread guard is not available.");
      return true;
    }
    if (args.length < 2) {
      sender.sendMessage("Usage: /zakum threadguard status|enable|disable");
      return true;
    }
    String sub = args[1].toLowerCase(java.util.Locale.ROOT);
    switch (sub) {
      case "status" -> sendThreadGuardStatus(sender);
      case "enable" -> {
        threadGuard.setRuntimeEnabled(true);
        sender.sendMessage("Thread guard runtime enabled.");
      }
      case "disable" -> {
        threadGuard.setRuntimeEnabled(false);
        sender.sendMessage("Thread guard runtime disabled.");
      }
      default -> sender.sendMessage("Usage: /zakum threadguard status|enable|disable");
    }
    return true;
  }

  private boolean handleAsyncCommand(CommandSender sender, String[] args) {
    if (scheduler == null) {
      sender.sendMessage("Scheduler is offline.");
      return true;
    }
    if (args.length < 2) {
      sender.sendMessage("Usage: /zakum async status|enable|disable");
      return true;
    }
    String sub = args[1].toLowerCase(java.util.Locale.ROOT);
    switch (sub) {
      case "status" -> sendAsyncStatus(sender);
      case "enable" -> {
        scheduler.setAsyncRuntimeEnabled(true);
        sender.sendMessage("Async backpressure runtime enabled.");
      }
      case "disable" -> {
        scheduler.setAsyncRuntimeEnabled(false);
        sender.sendMessage("Async backpressure runtime disabled.");
      }
      default -> sender.sendMessage("Usage: /zakum async status|enable|disable");
    }
    return true;
  }

  private boolean handleBurstCacheCommand(CommandSender sender, String[] args) {
    if (burstCache == null) {
      sender.sendMessage("Burst cache service is offline.");
      return true;
    }
    if (args.length < 2) {
      sender.sendMessage("Usage: /zakum burstcache status|enable|disable");
      return true;
    }
    String sub = args[1].toLowerCase(java.util.Locale.ROOT);
    switch (sub) {
      case "status" -> sendBurstCacheStatus(sender);
      case "enable" -> {
        burstCache.setRuntimeEnabled(true);
        sender.sendMessage("Burst cache runtime enabled.");
      }
      case "disable" -> {
        burstCache.setRuntimeEnabled(false);
        sender.sendMessage("Burst cache runtime disabled.");
      }
      default -> sender.sendMessage("Usage: /zakum burstcache status|enable|disable");
    }
    return true;
  }

  private boolean handleModulesCommand(CommandSender sender, String[] args) {
    if (moduleStartupValidator == null) {
      sender.sendMessage("Module startup validator is offline.");
      return true;
    }
    if (args.length < 2) {
      sender.sendMessage("Usage: /zakum modules status|validate");
      return true;
    }
    String sub = args[1].toLowerCase(java.util.Locale.ROOT);
    switch (sub) {
      case "status" -> sendModuleValidatorStatus(sender, false);
      case "validate" -> sendModuleValidatorStatus(sender, true);
      default -> sender.sendMessage("Usage: /zakum modules status|validate");
    }
    return true;
  }

  private Player resolvePacketCullTarget(CommandSender sender, String[] args) {
    if (args.length >= 3) {
      Player target = Bukkit.getPlayerExact(args[2]);
      if (target == null) {
        sender.sendMessage("Player not found: " + args[2]);
      }
      return target;
    }
    if (sender instanceof Player player) return player;
    sender.sendMessage("Console usage: /zakum packetcull <sample|refresh> <player>");
    return null;
  }

  private void sendPacketCullStatus(CommandSender sender) {
    if (packetCullingKernel == null) {
      sender.sendMessage("Packet culling kernel is offline.");
      return;
    }
    var snap = packetCullingKernel.snapshot();
    sender.sendMessage("Zakum Packet Culling");
    sender.sendMessage("configuredEnabled=" + snap.configuredEnabled());
    sender.sendMessage("runtimeEnabled=" + snap.runtimeEnabled());
    sender.sendMessage("hookRegistered=" + snap.hookRegistered());
    sender.sendMessage("backend=" + snap.backend());
    sender.sendMessage("hookCount=" + snap.hookCount());
    sender.sendMessage("probeIntervalTicks=" + snap.probeIntervalTicks());
    sender.sendMessage("sampleTaskId=" + snap.sampleTaskId());
    sender.sendMessage("probeTaskId=" + snap.probeTaskId());
    sender.sendMessage("hookLastChanged=" + formatEpochMillis(snap.hookLastChangedMs()));
    sender.sendMessage("radius=" + snap.radius());
    sender.sendMessage("densityThreshold=" + snap.densityThreshold());
    sender.sendMessage("maxSampleAgeMs=" + snap.maxSampleAgeMs());
    sender.sendMessage("lastOnlineCount=" + snap.lastOnlineCount());
    sender.sendMessage("lastSampleBatch=" + snap.lastSampleBatch());
    sender.sendMessage("respectPerfMode=" + snap.respectPerfMode());
    String bypass = snap.bypassPermission();
    sender.sendMessage("bypassPermission=" + (bypass == null || bypass.isBlank() ? "none" : bypass));
    sender.sendMessage("sampledPlayers=" + snap.sampledPlayers());
    sender.sendMessage("sampleRuns=" + snap.sampleRuns());
    sender.sendMessage("sampleUpdates=" + snap.sampleUpdates());
    sender.sendMessage("serviceProbeRuns=" + snap.serviceProbeRuns());
    sender.sendMessage("disabledSkips=" + snap.disabledSkips());
    sender.sendMessage("noPlayerSkips=" + snap.noPlayerSkips());
    sender.sendMessage("noSampleSkips=" + snap.noSampleSkips());
    sender.sendMessage("staleSkips=" + snap.staleSkips());
    sender.sendMessage("belowThresholdSkips=" + snap.belowThresholdSkips());
    sender.sendMessage("bypassSkips=" + snap.bypassSkips());
    sender.sendMessage("qualitySkips=" + snap.qualitySkips());
    sender.sendMessage("packetsObserved=" + snap.packetsObserved());
    sender.sendMessage("packetsDropped=" + snap.packetsDropped());
    sender.sendMessage("dropRate=" + String.format(java.util.Locale.ROOT, "%.2f%%", snap.dropRate() * 100.0d));
  }

  private void sendThreadGuardStatus(CommandSender sender) {
    if (threadGuard == null) {
      sender.sendMessage("Thread guard is offline.");
      return;
    }
    var snap = threadGuard.snapshot();
    sender.sendMessage("Zakum Thread Guard");
    sender.sendMessage("configuredEnabled=" + snap.configuredEnabled());
    sender.sendMessage("runtimeEnabled=" + snap.runtimeEnabled());
    sender.sendMessage("enabled=" + snap.enabled());
    sender.sendMessage("failOnViolation=" + snap.failOnViolation());
    sender.sendMessage("maxReportsPerMinute=" + snap.maxReportsPerMinute());
    sender.sendMessage("violations=" + snap.violations());
    sender.sendMessage("lastViolation=" + (snap.lastViolation() == null || snap.lastViolation().isBlank() ? "none" : snap.lastViolation()));
    sender.sendMessage("lastViolationAt=" + formatEpochMillis(snap.lastViolationAtMs()));
    if (snap.topOperations() != null && !snap.topOperations().isEmpty()) {
      sender.sendMessage("topOperations=" + snap.topOperations());
    }
  }

  private void sendAsyncStatus(CommandSender sender) {
    if (scheduler == null) {
      sender.sendMessage("Scheduler is offline.");
      return;
    }
    var snap = scheduler.asyncSnapshot();
    sender.sendMessage("Zakum Async Backpressure");
    sender.sendMessage("configuredEnabled=" + snap.configuredEnabled());
    sender.sendMessage("runtimeEnabled=" + snap.runtimeEnabled());
    sender.sendMessage("enabled=" + snap.enabled());
    sender.sendMessage("maxInFlight=" + snap.maxInFlight());
    sender.sendMessage("maxQueue=" + snap.maxQueue());
    sender.sendMessage("callerRunsOffMainThread=" + snap.callerRunsOffMainThread());
    sender.sendMessage("inFlight=" + snap.inFlight());
    sender.sendMessage("queued=" + snap.queued());
    sender.sendMessage("submitted=" + snap.submitted());
    sender.sendMessage("executed=" + snap.executed());
    sender.sendMessage("queuedTasks=" + snap.queuedTasks());
    sender.sendMessage("rejected=" + snap.rejected());
    sender.sendMessage("callerRuns=" + snap.callerRuns());
    sender.sendMessage("drainRuns=" + snap.drainRuns());
    sender.sendMessage("lastQueueAt=" + formatEpochMillis(snap.lastQueueAtMs()));
    sender.sendMessage("lastRejectAt=" + formatEpochMillis(snap.lastRejectAtMs()));
    sender.sendMessage("lastCallerRunAt=" + formatEpochMillis(snap.lastCallerRunAtMs()));
  }

  private void sendDataHealthStatus(CommandSender sender) {
    sender.sendMessage("Collecting data health...");
    collectDataHealth().whenComplete((snap, ex) -> {
      Runnable send = () -> {
        if (ex != null) {
          sender.sendMessage("Data health failed: " + ex.getMessage());
          return;
        }
        for (String line : dataHealthLines(snap)) {
          sender.sendMessage(line);
        }
      };
      if (scheduler != null) {
        scheduler.runGlobal(send);
      } else {
        send.run();
      }
    });
  }

  public List<String> taskRegistryLines() {
    if (scheduler == null) {
      return List.of("Task registry unavailable: scheduler offline.");
    }
    List<String> lines = new ArrayList<>();
    lines.add("Zakum Task Registry");
    lines.add("scheduler.tracked=" + scheduler.trackedTaskCount());
    appendTaskLines(lines, "cloud.poll", cloudPollTaskId);
    appendTaskLines(lines, "social.refresh", socialRefreshTaskId);

    if (visualCircuitBreaker != null) {
      var snap = visualCircuitBreaker.snapshot();
      appendTaskLines(lines, "visual.circuit", snap.taskId());
      lines.add("visual.circuit.open=" + snap.open());
    } else {
      lines.add("visual.circuit.taskId=-1");
      lines.add("visual.circuit.active=false");
    }

    if (packetCullingKernel != null) {
      var snap = packetCullingKernel.snapshot();
      appendTaskLines(lines, "packetcull.sample", snap.sampleTaskId());
      appendTaskLines(lines, "packetcull.probe", snap.probeTaskId());
      lines.add("packetcull.hookRegistered=" + snap.hookRegistered());
    } else {
      lines.add("packetcull.sample.taskId=-1");
      lines.add("packetcull.sample.active=false");
      lines.add("packetcull.probe.taskId=-1");
      lines.add("packetcull.probe.active=false");
    }

    if (stressHarness != null) {
      var snap = stressHarness.snapshot();
      appendTaskLines(lines, "stress.runner", snap.taskId());
      lines.add("stress.running=" + snap.running());
    } else {
      lines.add("stress.runner.taskId=-1");
      lines.add("stress.runner.active=false");
      lines.add("stress.running=false");
    }

    if (moduleStartupValidator != null) {
      var snap = moduleStartupValidator.snapshot();
      appendTaskLines(lines, "modules.validator", snap.startupTaskId());
      lines.add("modules.validator.enabled=" + snap.configuredEnabled());
      lines.add("modules.validator.healthy=" + snap.healthy());
    } else {
      lines.add("modules.validator.taskId=-1");
      lines.add("modules.validator.active=false");
      lines.add("modules.validator.enabled=false");
      lines.add("modules.validator.healthy=false");
    }
    return lines;
  }

  private void sendTaskRegistryStatus(CommandSender sender) {
    for (String line : taskRegistryLines()) {
      sender.sendMessage(line);
    }
  }

  private void sendBurstCacheStatus(CommandSender sender) {
    if (burstCache == null) {
      sender.sendMessage("Burst cache service is offline.");
      return;
    }
    var snap = burstCache.snapshot();
    sender.sendMessage("Zakum Burst Cache");
    sender.sendMessage("configuredEnabled=" + snap.configuredEnabled());
    sender.sendMessage("runtimeEnabled=" + snap.runtimeEnabled());
    sender.sendMessage("available=" + snap.available());
    sender.sendMessage("redisUri=" + snap.redisUri());
    sender.sendMessage("keyPrefix=" + snap.keyPrefix());
    sender.sendMessage("defaultTtlSeconds=" + snap.defaultTtlSeconds());
    sender.sendMessage("maximumLocalEntries=" + snap.maximumLocalEntries());
    sender.sendMessage("gets=" + snap.gets());
    sender.sendMessage("puts=" + snap.puts());
    sender.sendMessage("increments=" + snap.increments());
    sender.sendMessage("removes=" + snap.removes());
    sender.sendMessage("redisHits=" + snap.redisHits());
    sender.sendMessage("localHits=" + snap.localHits());
    sender.sendMessage("redisFailures=" + snap.redisFailures());
    sender.sendMessage("lastFailureAt=" + formatEpochMillis(snap.lastFailureAtMs()));
    String err = snap.lastError();
    sender.sendMessage("lastError=" + (err == null || err.isBlank() ? "none" : err));
  }

  public List<String> moduleValidatorLines(boolean forceValidate) {
    ModuleStartupValidator validator = moduleStartupValidator;
    if (validator == null) {
      return List.of("Module startup validator is offline.");
    }
    ModuleStartupValidator.Snapshot snap = forceValidate ? validator.validateNow() : validator.snapshot();
    List<String> lines = new ArrayList<>();
    lines.add("Zakum Module Startup Validator");
    lines.add("configuredEnabled=" + snap.configuredEnabled());
    lines.add("strictMode=" + snap.strictMode());
    lines.add("healthy=" + snap.healthy());
    lines.add("runs=" + snap.runs());
    lines.add("validatedAt=" + formatEpochMillis(snap.validatedAtMs()));
    lines.add("modulesScanned=" + snap.modulesScanned());
    lines.add("zakumFamilyModules=" + snap.zakumFamilyModules());
    lines.add("requiredPlugins=" + snap.requiredPluginCount());
    lines.add("requiredCapabilities=" + snap.requiredCapabilityCount());
    lines.add("startupTaskId=" + snap.startupTaskId());
    lines.add("warnings=" + snap.warnings());
    lines.add("criticals=" + snap.criticals());
    if (snap.issues() == null || snap.issues().isEmpty()) {
      lines.add("issues=none");
      return lines;
    }
    for (ModuleStartupValidator.Issue issue : snap.issues()) {
      if (issue == null) continue;
      lines.add(
        issue.severity().name().toLowerCase(java.util.Locale.ROOT)
          + "."
          + issue.code()
          + "="
          + issue.message()
      );
    }
    return lines;
  }

  private void sendModuleValidatorStatus(CommandSender sender, boolean forceValidate) {
    for (String line : moduleValidatorLines(forceValidate)) {
      sender.sendMessage(line);
    }
  }

  private void appendTaskLines(List<String> lines, String key, int taskId) {
    if (lines == null || key == null || key.isBlank()) return;
    lines.add(key + ".taskId=" + taskId);
    lines.add(key + ".active=" + (scheduler != null && scheduler.isTaskActive(taskId)));
  }

  private static String formatEpochMillis(long epochMillis) {
    if (epochMillis <= 0L) return "never";
    return Instant.ofEpochMilli(epochMillis).toString();
  }

  public record DataHealthSnapshot(
    String sqlState,
    int poolActive,
    int poolIdle,
    boolean cloudConfigured,
    boolean cloudOnline,
    boolean datastoreOnline,
    boolean socialOnline,
    boolean economyRegistered,
    boolean economyAvailable,
    String economyError,
    boolean packetsOnline,
    String packetBackend,
    int packetHooks,
    boolean threadGuardOnline,
    long threadGuardViolations,
    long asyncRejected,
    boolean moduleValidatorEnabled,
    boolean moduleValidatorHealthy,
    int moduleValidatorWarnings,
    int moduleValidatorCriticals,
    String error
  ) {
    public static DataHealthSnapshot empty(String error) {
      return new DataHealthSnapshot(
        "OFFLINE",
        0,
        0,
        false,
        false,
        false,
        false,
        false,
        false,
        "",
        false,
        "offline",
        0,
        false,
        0L,
        0L,
        false,
        false,
        0,
        0,
        error == null ? "" : error
      );
    }
  }

  private static UUID resolveUuid(CommandSender sender, String token) {
    if (token.contains("-")) {
        try { return UUID.fromString(token); } catch (Exception ignored) {}
    }
    OfflinePlayer op = Bukkit.getOfflinePlayer(token);
    UUID id = op.getUniqueId();
    if (id == null) sender.sendMessage("Unknown player: " + token);
    return id;
  }

  private static int parseInt(String raw, int fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  private static double parseDouble(String raw, double fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    try {
      return Double.parseDouble(raw.trim());
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  private static String formatMoney(double value) {
    if (!Double.isFinite(value)) return "0.00";
    return String.format(java.util.Locale.ROOT, "%.2f", value);
  }

  private void registerCoreActionEmitters(Clock clock) {
    var pm = getServer().getPluginManager();
    var a = settings.actions();

    if (a.deferredReplay().enabled()) {
      int lim = a.deferredReplay().claimLimit();
      pm.registerEvents(new DeferredActionReplayListener(this, api.server(), deferred, actionBus, lim), this);
    }

    if (!a.enabled()) return;
    var e = a.emitters();
    
    if (e.joinQuit()) pm.registerEvents(new PlayerLifecycleEmitter(actionBus), this);
    if (e.onlineTime()) pm.registerEvents(new OnlineTimeEmitter(actionBus, clock), this);
    if (e.blockBreak()) pm.registerEvents(new BlockBreakEmitter(actionBus), this);
    if (e.blockPlace()) pm.registerEvents(new BlockPlaceEmitter(actionBus), this);
    if (e.mobKill()) pm.registerEvents(new MobKillEmitter(actionBus), this);
    if (e.playerDeath() || e.playerKill()) pm.registerEvents(new PlayerDeathEmitter(actionBus), this);
    if (e.xpGain()) pm.registerEvents(new XpGainEmitter(actionBus), this);
    if (e.levelChange()) pm.registerEvents(new LevelChangeEmitter(actionBus), this);
    if (e.itemCraft()) pm.registerEvents(new CraftEmitter(actionBus), this);
    if (e.smeltExtract()) pm.registerEvents(new SmeltExtractEmitter(actionBus), this);
    if (e.fishCatch()) pm.registerEvents(new FishCatchEmitter(actionBus), this);
    if (e.itemEnchant()) pm.registerEvents(new EnchantEmitter(actionBus), this);
    if (e.itemConsume()) pm.registerEvents(new ConsumeEmitter(actionBus), this);
    if (e.advancement()) pm.registerEvents(new AdvancementEmitter(actionBus), this);

    if (e.commandUse().enabled()) {
      pm.registerEvents(new CommandUseEmitter(actionBus, e.commandUse().allowlist()), this);
    }

    if (a.movement().enabled()) {
      int ticks = a.movement().sampleTicks();
      long maxCm = a.movement().maxCmPerSample();
      this.movementSampler = new MovementSampler(this, actionBus, ticks, maxCm);
      pm.registerEvents(movementSampler, this);
      movementSampler.start();
    }
  }
}

