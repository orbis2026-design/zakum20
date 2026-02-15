package net.orbis.zakum.core.ops;

import net.orbis.zakum.api.capability.Capability;
import net.orbis.zakum.api.capability.CapabilityRegistry;
import net.orbis.zakum.api.capability.ZakumCapabilities;
import net.orbis.zakum.api.concurrent.ZakumScheduler;
import net.orbis.zakum.api.config.ZakumSettings;
import net.orbis.zakum.api.gui.GuiService;
import net.orbis.zakum.core.metrics.MetricsMonitor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Validates module startup/load-order posture for Zakum family plugins.
 */
public final class ModuleStartupValidator {

  private static final Set<String> CORE_PLUGIN_NAMES = Set.of(
    "zakum",
    "zakumpackets"
  );

  private static final Map<String, Capability<?>> CAPABILITY_TOKENS = capabilityTokens();

  private final Plugin owner;
  private final ZakumSettings settings;
  private final ZakumSettings.Operations.StartupValidator config;
  private final CapabilityRegistry capabilityRegistry;
  private final ZakumScheduler scheduler;
  private final MetricsMonitor metrics;
  private final Logger logger;
  private final Set<String> requiredPlugins;
  private final Set<String> requiredCapabilities;
  private final AtomicLong runs = new AtomicLong();
  private volatile Snapshot snapshot;
  private volatile int startupTaskId = -1;

  public ModuleStartupValidator(
    Plugin owner,
    ZakumSettings settings,
    CapabilityRegistry capabilityRegistry,
    ZakumScheduler scheduler,
    MetricsMonitor metrics,
    Logger logger
  ) {
    this.owner = Objects.requireNonNull(owner, "owner");
    this.settings = Objects.requireNonNull(settings, "settings");
    this.config = Objects.requireNonNull(settings.operations(), "operations").startupValidator();
    this.capabilityRegistry = Objects.requireNonNull(capabilityRegistry, "capabilityRegistry");
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    this.metrics = metrics;
    this.logger = Objects.requireNonNull(logger, "logger");
    this.requiredPlugins = normalizeValues(config.requiredPlugins());
    this.requiredCapabilities = normalizeValues(config.requiredCapabilities());
    this.snapshot = Snapshot.disabled(config.strictMode(), this.requiredPlugins.size(), this.requiredCapabilities.size());
  }

  public void start() {
    if (!config.enabled()) return;
    int delay = Math.max(1, config.initialDelayTicks());
    startupTaskId = scheduler.runTaskLater(owner, this::runStartupValidation, delay);
    logger.info("Module startup validator scheduled (delayTicks=" + delay + ").");
  }

  public void stop() {
    int taskId = startupTaskId;
    if (taskId > 0) {
      scheduler.cancelTask(taskId);
    }
    startupTaskId = -1;
  }

  public Snapshot snapshot() {
    return snapshot;
  }

  public Snapshot validateNow() {
    if (!config.enabled()) {
      Snapshot disabled = Snapshot.disabled(config.strictMode(), requiredPlugins.size(), requiredCapabilities.size());
      snapshot = disabled;
      return disabled;
    }

    long runNumber = runs.incrementAndGet();
    long now = System.currentTimeMillis();
    List<Issue> issues = new ArrayList<>();
    try {
      PluginManager pm = Bukkit.getPluginManager();
      Plugin[] plugins = pm.getPlugins();
      int modulesScanned = plugins.length;
      int zakumFamilyModules = 0;

      for (Plugin plugin : plugins) {
        if (plugin == null) continue;
        String pluginName = plugin.getName();
        if (!isZakumFamily(pluginName)) continue;
        zakumFamilyModules++;
        if (isCorePlugin(pluginName)) continue;
        if (!declaresDependOnZakum(plugin.getDescription())) {
          issues.add(Issue.critical(
            "MISSING_DEPEND",
            "Plugin " + pluginName + " does not declare depend: [Zakum] (startup order risk)."
          ));
        }
      }

      for (String required : requiredPlugins) {
        Plugin plugin = pm.getPlugin(required);
        if (plugin == null) {
          issues.add(Issue.critical("REQUIRED_PLUGIN_MISSING", "Required plugin missing: " + required));
          continue;
        }
        if (!plugin.isEnabled()) {
          issues.add(Issue.critical("REQUIRED_PLUGIN_DISABLED", "Required plugin is present but disabled: " + required));
        }
      }

      validateConfiguredCapabilities(issues);
      validateFeatureToggles(pm, issues);

      int warnings = 0;
      int criticals = 0;
      for (Issue issue : issues) {
        if (issue.severity() == Severity.CRITICAL) {
          criticals++;
          recordAction("module_validator_critical");
        } else {
          warnings++;
          recordAction("module_validator_warning");
        }
      }

      boolean healthy = criticals == 0;
      Snapshot next = new Snapshot(
        true,
        config.strictMode(),
        healthy,
        now,
        runNumber,
        modulesScanned,
        zakumFamilyModules,
        warnings,
        criticals,
        requiredPlugins.size(),
        requiredCapabilities.size(),
        startupTaskId,
        List.copyOf(issues)
      );
      snapshot = next;
      recordAction("module_validator_run");
      return next;
    } catch (Throwable ex) {
      String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
      Issue issue = Issue.critical("VALIDATOR_EXCEPTION", "Module validator failed: " + message);
      Snapshot failed = new Snapshot(
        true,
        config.strictMode(),
        false,
        now,
        runNumber,
        0,
        0,
        0,
        1,
        requiredPlugins.size(),
        requiredCapabilities.size(),
        startupTaskId,
        List.of(issue)
      );
      snapshot = failed;
      recordAction("module_validator_run");
      recordAction("module_validator_critical");
      return failed;
    }
  }

  private void runStartupValidation() {
    startupTaskId = -1;
    Snapshot result = validateNow();
    logSummary(result);

    if (result.criticals() > 0 && config.strictMode()) {
      logger.severe("Module validator strictMode=true and critical issues found. Disabling Zakum.");
      Bukkit.getPluginManager().disablePlugin(owner);
    }
  }

  private void logSummary(Snapshot snap) {
    if (snap == null || !snap.configuredEnabled()) return;
    logger.info(
      "Module validator run="
        + snap.runs()
        + " healthy="
        + snap.healthy()
        + " warnings="
        + snap.warnings()
        + " criticals="
        + snap.criticals()
        + " at="
        + Instant.ofEpochMilli(snap.validatedAtMs())
    );
    for (Issue issue : snap.issues()) {
      if (issue.severity() == Severity.CRITICAL) {
        logger.severe("[ModuleValidator][" + issue.code() + "] " + issue.message());
      } else {
        logger.warning("[ModuleValidator][" + issue.code() + "] " + issue.message());
      }
    }
  }

  private void validateConfiguredCapabilities(List<Issue> issues) {
    for (String token : requiredCapabilities) {
      Capability<?> capability = resolveCapability(token);
      if (capability == null) {
        issues.add(Issue.warning("UNKNOWN_CAPABILITY", "Unknown required capability token: " + token));
        continue;
      }
      if (!hasCapability(capability)) {
        issues.add(Issue.critical("MISSING_CAPABILITY", "Required capability missing: " + capability.id()));
      }
    }
  }

  private void validateFeatureToggles(PluginManager pm, List<Issue> issues) {
    if (settings.packets().enabled()) {
      Plugin packetLayer = pm.getPlugin("ZakumPackets");
      if (packetLayer == null || !packetLayer.isEnabled()) {
        issues.add(Issue.critical("PACKETS_MODULE_OFFLINE", "packets.enabled=true but ZakumPackets is missing or disabled."));
      }
      Plugin packetEvents = pm.getPlugin("PacketEvents");
      if (packetEvents == null || !packetEvents.isEnabled()) {
        issues.add(Issue.critical("PACKETEVENTS_OFFLINE", "packets.enabled=true but PacketEvents is missing or disabled."));
      }
      if (!hasCapability(ZakumCapabilities.PACKETS)) {
        issues.add(Issue.critical("PACKET_CAPABILITY_OFFLINE", "packets.enabled=true but PacketService capability is unavailable."));
      }
    }

    if (settings.chat().localization().packetDispatchEnabled() && !hasCapability(ZakumCapabilities.PACKETS)) {
      issues.add(Issue.warning(
        "CHAT_PACKET_FALLBACK",
        "chat.localization.packetDispatchEnabled=true but PacketService is unavailable (falls back to Adventure send)."
      ));
    }

    if (settings.dataStore().enabled() && !hasCapability(ZakumCapabilities.DATA_STORE)) {
      issues.add(Issue.critical("DATASTORE_CAPABILITY_OFFLINE", "datastore.enabled=true but DataStore capability is unavailable."));
    }

    if (settings.economy().global().enabled() && !hasCapability(ZakumCapabilities.ECONOMY)) {
      issues.add(Issue.critical("ECONOMY_CAPABILITY_OFFLINE", "economy.global.enabled=true but Economy capability is unavailable."));
    }

    if (settings.cache().burst().enabled() && !hasCapability(ZakumCapabilities.BURST_CACHE)) {
      issues.add(Issue.critical("BURST_CACHE_CAPABILITY_OFFLINE", "cache.burst.enabled=true but BurstCache capability is unavailable."));
    }

    Plugin guiPlugin = pm.getPlugin("OrbisGUI");
    if (guiPlugin != null && guiPlugin.isEnabled()) {
      GuiService gui = Bukkit.getServicesManager().load(GuiService.class);
      if (gui == null) {
        issues.add(Issue.warning("GUI_SERVICE_MISSING", "OrbisGUI is enabled but GuiService is not registered."));
      } else if (!gui.available()) {
        issues.add(Issue.warning("GUI_SERVICE_UNAVAILABLE", "OrbisGUI is enabled but GuiService reports unavailable."));
      }
    }
  }

  private boolean hasCapability(Capability<?> capability) {
    if (capability == null) return false;
    try {
      return capabilityRegistry.has(capability);
    } catch (Throwable ignored) {
      return false;
    }
  }

  private Capability<?> resolveCapability(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) return null;
    String token = normalizeToken(rawToken);
    Capability<?> fromNamedToken = CAPABILITY_TOKENS.get(token);
    if (fromNamedToken != null) return fromNamedToken;

    for (Capability<?> known : CAPABILITY_TOKENS.values()) {
      if (known.id().equalsIgnoreCase(rawToken.trim())) return known;
    }
    return null;
  }

  private static boolean isZakumFamily(String pluginName) {
    if (pluginName == null || pluginName.isBlank()) return false;
    String normalized = normalizeToken(pluginName);
    return normalized.startsWith("ORBIS") || normalized.startsWith("ZAKUM");
  }

  private static boolean isCorePlugin(String pluginName) {
    if (pluginName == null || pluginName.isBlank()) return false;
    return CORE_PLUGIN_NAMES.contains(pluginName.trim().toLowerCase(Locale.ROOT));
  }

  private static boolean declaresDependOnZakum(PluginDescriptionFile description) {
    if (description == null) return false;
    List<String> depends = description.getDepend();
    if (depends == null || depends.isEmpty()) return false;
    for (String dep : depends) {
      if (dep != null && dep.equalsIgnoreCase("Zakum")) return true;
    }
    return false;
  }

  private static Set<String> normalizeValues(Set<String> values) {
    LinkedHashSet<String> out = new LinkedHashSet<>();
    if (values == null) return out;
    for (String value : values) {
      if (value == null || value.isBlank()) continue;
      out.add(value.trim());
    }
    return Set.copyOf(out);
  }

  private static String normalizeToken(String raw) {
    if (raw == null) return "";
    String upper = raw.trim().toUpperCase(Locale.ROOT);
    if (upper.isBlank()) return "";
    StringBuilder normalized = new StringBuilder(upper.length());
    for (int i = 0; i < upper.length(); i++) {
      char ch = upper.charAt(i);
      if ((ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9')) {
        normalized.append(ch);
      }
    }
    return normalized.toString();
  }

  private static Map<String, Capability<?>> capabilityTokens() {
    LinkedHashMap<String, Capability<?>> map = new LinkedHashMap<>();
    register(map, "ACTION_BUS", ZakumCapabilities.ACTION_BUS);
    register(map, "DEFERRED_ACTIONS", ZakumCapabilities.DEFERRED_ACTIONS);
    register(map, "ACE_ENGINE", ZakumCapabilities.ACE_ENGINE);
    register(map, "ENTITLEMENTS", ZakumCapabilities.ENTITLEMENTS);
    register(map, "BOOSTERS", ZakumCapabilities.BOOSTERS);
    register(map, "PROGRESSION", ZakumCapabilities.PROGRESSION);
    register(map, "SCHEDULER", ZakumCapabilities.SCHEDULER);
    register(map, "STORAGE", ZakumCapabilities.STORAGE);
    register(map, "ANIMATIONS", ZakumCapabilities.ANIMATIONS);
    register(map, "CONTROL_PLANE", ZakumCapabilities.CONTROL_PLANE);
    register(map, "DATA_STORE", ZakumCapabilities.DATA_STORE);
    register(map, "SOCIAL", ZakumCapabilities.SOCIAL);
    register(map, "CHAT_BUFFER", ZakumCapabilities.CHAT_BUFFER);
    register(map, "BRIDGE_MANAGER", ZakumCapabilities.BRIDGE_MANAGER);
    register(map, "GUI", ZakumCapabilities.GUI);
    register(map, "PACKETS", ZakumCapabilities.PACKETS);
    register(map, "PLACEHOLDERS", ZakumCapabilities.PLACEHOLDERS);
    register(map, "ECONOMY", ZakumCapabilities.ECONOMY);
    register(map, "LUCKPERMS", ZakumCapabilities.LUCKPERMS);
    register(map, "BURST_CACHE", ZakumCapabilities.BURST_CACHE);
    return Map.copyOf(map);
  }

  private static void register(Map<String, Capability<?>> map, String token, Capability<?> capability) {
    map.put(normalizeToken(token), capability);
  }

  private void recordAction(String action) {
    if (metrics == null) return;
    metrics.recordAction(action);
  }

  public enum Severity {
    WARNING,
    CRITICAL
  }

  public record Issue(
    Severity severity,
    String code,
    String message
  ) {
    public Issue {
      severity = Objects.requireNonNull(severity, "severity");
      code = code == null ? "UNKNOWN" : code.trim().toUpperCase(Locale.ROOT);
      if (code.isBlank()) code = "UNKNOWN";
      message = message == null ? "" : message.trim();
    }

    public static Issue warning(String code, String message) {
      return new Issue(Severity.WARNING, code, message);
    }

    public static Issue critical(String code, String message) {
      return new Issue(Severity.CRITICAL, code, message);
    }
  }

  public record Snapshot(
    boolean configuredEnabled,
    boolean strictMode,
    boolean healthy,
    long validatedAtMs,
    long runs,
    int modulesScanned,
    int zakumFamilyModules,
    int warnings,
    int criticals,
    int requiredPluginCount,
    int requiredCapabilityCount,
    int startupTaskId,
    List<Issue> issues
  ) {
    public Snapshot {
      issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public static Snapshot disabled(boolean strictMode, int requiredPluginCount, int requiredCapabilityCount) {
      return new Snapshot(
        false,
        strictMode,
        true,
        0L,
        0L,
        0,
        0,
        0,
        0,
        Math.max(0, requiredPluginCount),
        Math.max(0, requiredCapabilityCount),
        -1,
        List.of()
      );
    }
  }
}
