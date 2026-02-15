package net.orbis.orbishud.service;

import net.orbis.orbishud.config.HudConfig;
import net.orbis.orbishud.config.HudProfile;
import net.orbis.orbishud.render.ScoreboardHudRenderer;
import net.orbis.orbishud.state.HudPlayerState;
import net.orbis.orbishud.state.HudStateCache;
import net.orbis.zakum.api.ZakumApi;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DefaultHudService implements HudService {

  private final Plugin plugin;
  private final ZakumApi api;
  private final Logger logger;
  private final HudStateCache stateCache;
  private final ScoreboardHudRenderer renderer;

  private volatile HudConfig config;
  private volatile int taskId = -1;
  private volatile boolean running;

  public DefaultHudService(Plugin plugin, ZakumApi api, HudConfig initialConfig, Logger logger) {
    this.plugin = plugin;
    this.api = api;
    this.config = initialConfig;
    this.logger = logger;
    this.stateCache = new HudStateCache();
    this.renderer = new ScoreboardHudRenderer();
  }

  @Override
  public void start() {
    if (running) return;
    running = true;
    if (config.enabled()) {
      startTask();
    } else {
      stopTask();
    }
    for (Player player : Bukkit.getOnlinePlayers()) {
      renderPlayer(player);
    }
  }

  @Override
  public void stop() {
    if (!running) return;
    running = false;
    stopTask();
    for (Player player : Bukkit.getOnlinePlayers()) {
      HudPlayerState state = stateCache.remove(player.getUniqueId());
      if (state != null) {
        renderer.clear(player, state);
      }
    }
    stateCache.clear();
  }

  @Override
  public HudStatus snapshot() {
    HudConfig cfg = config;
    return new HudStatus(
      running,
      cfg.enabled(),
      cfg.updateIntervalTicks(),
      cfg.profileIds().size(),
      cfg.defaultProfile(),
      stateCache.size(),
      Bukkit.getOnlinePlayers().size(),
      taskId
    );
  }

  @Override
  public void reload(HudConfig newConfig) {
    this.config = newConfig;
    if (!running) return;
    if (newConfig.enabled()) {
      startTask();
    } else {
      stopTask();
    }
    for (Player player : Bukkit.getOnlinePlayers()) {
      renderPlayer(player);
    }
    logger.info(
      "HUD config reloaded. enabled=" + newConfig.enabled() +
        ", intervalTicks=" + newConfig.updateIntervalTicks() +
        ", profiles=" + newConfig.profileIds().size() +
        ", defaultProfile=" + newConfig.defaultProfile()
    );
  }

  @Override
  public boolean setProfile(UUID playerId, String profileId) {
    if (playerId == null || profileId == null || profileId.isBlank()) return false;
    String normalized = profileId.trim().toLowerCase(Locale.ROOT);
    HudConfig cfg = config;
    if (!cfg.profileIds().contains(normalized)) return false;

    HudPlayerState state = stateCache.getOrCreate(playerId);
    state.forcedProfileId(normalized);

    Player player = Bukkit.getPlayer(playerId);
    if (player != null && player.isOnline()) {
      renderPlayer(player);
    }
    return true;
  }

  @Override
  public boolean clearProfile(UUID playerId) {
    if (playerId == null) return false;
    HudPlayerState state = stateCache.getOrCreate(playerId);
    state.forcedProfileId(null);
    Player player = Bukkit.getPlayer(playerId);
    if (player != null && player.isOnline()) {
      renderPlayer(player);
    }
    return true;
  }

  @Override
  public Set<String> availableProfiles() {
    return config.profileIds();
  }

  @Override
  public HudConfig config() {
    return config;
  }

  @Override
  public void onPlayerJoin(Player player) {
    if (player == null) return;
    renderPlayer(player);
  }

  @Override
  public void onPlayerQuit(Player player) {
    if (player == null) return;
    HudPlayerState state = stateCache.remove(player.getUniqueId());
    if (state != null) {
      renderer.clear(player, state);
    }
  }

  @Override
  public void refreshPlayer(Player player) {
    if (player == null) return;
    renderPlayer(player);
  }

  private void tick() {
    if (!running) return;
    for (Player player : Bukkit.getOnlinePlayers()) {
      renderPlayer(player);
    }
  }

  private void renderPlayer(Player player) {
    if (player == null || !player.isOnline()) return;

    HudConfig cfg = config;
    HudPlayerState state = stateCache.getOrCreate(player.getUniqueId());

    if (!cfg.enabled()) {
      renderer.clear(player, state);
      return;
    }

    String profileId = state.forcedProfileId();
    HudProfile profile = cfg.profile(profileId);
    if (profile == null) {
      logger.warning("No HUD profile resolved for player " + player.getName() + ". Skipping render.");
      return;
    }

    try {
      renderer.render(player, profile, state, api.server().serverId(), cfg.hideWhenInSpectator());
    } catch (Throwable t) {
      logger.log(Level.WARNING, "Failed rendering HUD for " + player.getName() + ": " + t.getMessage(), t);
    }
  }

  private void startTask() {
    stopTask();
    HudConfig cfg = config;
    long period = Math.max(5L, cfg.updateIntervalTicks());
    taskId = api.getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, 0L, period);
  }

  private void stopTask() {
    if (taskId >= 0) {
      api.getScheduler().cancelTask(taskId);
      taskId = -1;
    }
  }
}
