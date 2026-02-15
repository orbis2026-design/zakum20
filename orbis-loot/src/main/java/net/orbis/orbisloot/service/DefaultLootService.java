package net.orbis.orbisloot.service;

import net.orbis.orbisloot.config.LootConfig;
import net.orbis.orbisloot.config.LootCrateConfig;
import net.orbis.orbisloot.config.LootRewardConfig;
import net.orbis.zakum.api.ZakumApi;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

public final class DefaultLootService implements LootService {

  private final Plugin plugin;
  private final ZakumApi zakum;
  private final Logger logger;
  private final ConcurrentHashMap<UUID, Instant> cooldowns = new ConcurrentHashMap<>();

  private volatile LootConfig config;
  private volatile boolean running;
  private volatile int taskId = -1;

  public DefaultLootService(Plugin plugin, ZakumApi zakum, LootConfig config, Logger logger) {
    this.plugin = plugin;
    this.zakum = zakum;
    this.config = config;
    this.logger = logger;
  }

  @Override
  public void start() {
    if (running) return;
    running = true;
    if (config.enabled()) {
      startTask();
    }
  }

  @Override
  public void stop() {
    if (!running) return;
    running = false;
    stopTask();
    cooldowns.clear();
  }

  @Override
  public void reload(LootConfig config) {
    this.config = config;
    if (!running) return;
    if (config.enabled()) {
      startTask();
    } else {
      stopTask();
    }
    logger.info("OrbisLoot config reloaded. crates=" + config.crates().size());
  }

  @Override
  public LootStatus snapshot() {
    LootConfig cfg = config;
    return new LootStatus(
      running,
      cfg.enabled(),
      cfg.cleanupIntervalTicks(),
      cfg.openCooldownSeconds(),
      cfg.maxRewardsPerOpen(),
      cfg.crates().size(),
      cooldowns.size(),
      taskId
    );
  }

  @Override
  public Map<String, Integer> simulate(String crateId, int rolls) {
    LootCrateConfig crate = config.crate(crateId);
    if (crate == null || rolls <= 0) return Map.of();

    Map<String, Integer> counts = new LinkedHashMap<>();
    for (LootRewardConfig reward : crate.rewards()) {
      counts.put(reward.id(), 0);
    }

    for (int i = 0; i < rolls; i++) {
      LootRewardConfig reward = pick(crate);
      if (reward == null) continue;
      counts.computeIfPresent(reward.id(), (k, v) -> v + 1);
    }
    return Map.copyOf(counts);
  }

  @Override
  public Set<String> crateIds() {
    return config.crates().keySet();
  }

  private LootRewardConfig pick(LootCrateConfig crate) {
    double total = crate.totalWeight();
    if (total <= 0.0D) return null;

    double roll = ThreadLocalRandom.current().nextDouble(total);
    double cursor = 0.0D;
    for (LootRewardConfig reward : crate.rewards()) {
      cursor += reward.weight();
      if (roll <= cursor) return reward;
    }
    return crate.rewards().isEmpty() ? null : crate.rewards().get(crate.rewards().size() - 1);
  }

  private void cleanupCooldowns() {
    if (cooldowns.isEmpty()) return;
    Instant now = zakum.clock().instant();
    Duration maxCooldown = Duration.ofSeconds(Math.max(1, config.openCooldownSeconds()));
    cooldowns.entrySet().removeIf(entry -> Duration.between(entry.getValue(), now).compareTo(maxCooldown) > 0);
  }

  private void startTask() {
    stopTask();
    LootConfig cfg = config;
    long period = LootConfig.clamp(cfg.cleanupIntervalTicks(), 20, 1200);
    taskId = zakum.getScheduler().scheduleSyncRepeatingTask(plugin, this::cleanupCooldowns, period, period);
  }

  private void stopTask() {
    if (taskId >= 0) {
      zakum.getScheduler().cancelTask(taskId);
      taskId = -1;
    }
  }
}
