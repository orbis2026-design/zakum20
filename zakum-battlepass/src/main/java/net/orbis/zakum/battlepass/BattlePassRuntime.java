package net.orbis.zakum.battlepass;

import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.api.actions.ActionEvent;
import net.orbis.zakum.api.actions.ActionSubscription;
import net.orbis.zakum.api.boosters.BoosterKind;
import net.orbis.zakum.api.db.DatabaseState;
import net.orbis.zakum.api.entitlements.EntitlementScope;
import net.orbis.zakum.battlepass.index.QuestIndex;
import net.orbis.zakum.battlepass.model.QuestCadence;
import net.orbis.zakum.battlepass.model.QuestDef;
import net.orbis.zakum.battlepass.model.QuestStep;
import net.orbis.zakum.battlepass.premium.PremiumResolver;
import net.orbis.zakum.battlepass.rewards.RewardExecutor;
import net.orbis.zakum.battlepass.rewards.RewardLoader;
import net.orbis.zakum.battlepass.rewards.RewardTrack;
import net.orbis.zakum.battlepass.rewards.RewardsTable;
import net.orbis.zakum.battlepass.state.PlayerBpState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BattlePass runtime.
 *
 * Threading rules:
 * - ActionBus callback is typically main thread. Keep it lean.
 * - DB load/flush happens on Zakum async executor.
 */
public final class BattlePassRuntime {

  private final Plugin plugin;
  private final ZakumApi zakum;

  private final String progressServerId;
  private final int season;
  private final ZoneId zoneId;
  private final int currentWeek;

  private final Map<String, QuestDef> quests;
  private final Set<String> dailyQuestIds;
  private final Set<String> weeklyQuestIds;

  private final QuestIndex index;

  private final PremiumResolver premium;
  private final RewardsTable rewards;
  private final RewardExecutor rewardExec;

  private final ConcurrentHashMap<UUID, PlayerBpState> states = new ConcurrentHashMap<>();
  private volatile ActionSubscription sub;

  public BattlePassRuntime(Plugin plugin, ZakumApi zakum) {
    this.plugin = plugin;
    this.zakum = zakum;

    this.progressServerId = resolveProgressServerId(plugin, zakum);
    this.season = Math.max(1, plugin.getConfig().getInt("battlepass.seasons.current", 1));

    this.zoneId = parseZone(plugin.getConfig().getString("battlepass.timezone", "UTC"));
    this.currentWeek = Math.max(1, plugin.getConfig().getInt("battlepass.weeks.current", 1));

    this.quests = QuestLoader.load(plugin);
    this.index = new QuestIndex(quests.values());

    Set<String> d = new HashSet<>();
    Set<String> w = new HashSet<>();
    for (QuestDef q : quests.values()) {
      if (q.cadence() == QuestCadence.DAILY) d.add(q.id());
      else if (q.cadence() == QuestCadence.WEEKLY) w.add(q.id());
    }
    this.dailyQuestIds = Set.copyOf(d);
    this.weeklyQuestIds = Set.copyOf(w);

    String premiumScope = plugin.getConfig().getString("battlepass.premiumScope", "SERVER");
    String entKey = plugin.getConfig().getString("battlepass.premiumEntitlementKey", "battlepass_premium");
    this.premium = new PremiumResolver(zakum, premiumScope, entKey, progressServerId);

    this.rewards = RewardLoader.load(plugin);
    this.rewardExec = new RewardExecutor(plugin);
  }

  public void start() {
    this.sub = zakum.actions().subscribe(this::onAction);

    for (Player p : Bukkit.getOnlinePlayers()) {
      loadPlayerAsync(p.getUniqueId());
    }
  }

  public void stop() {
    stop(true);
  }

  /**
   * Stops the runtime.
   *
   * @param flush whether to flush deltas to DB as part of stop.
   */
  public void stop(boolean flush) {
    pauseActions();
    if (flush) flushAllAsync();
  }

  /**
   * Stops ActionBus processing immediately (maintenance mode).
   * Does not clear in-memory state.
   */
  public void pauseActions() {
    if (sub != null) {
      try { sub.close(); } catch (Throwable ignored) {}
    }
    sub = null;
  }

  public String progressServerId() { return progressServerId; }
  public int season() { return season; }
  public int week() { return currentWeek; }
  public String timezone() { return zoneId.getId(); }
  public RewardsTable rewards() { return rewards; }

  public java.util.Collection<QuestDef> allQuests() {
    return quests.values();
  }

  public java.util.Optional<QuestDef> quest(String id) {
    if (id == null) return java.util.Optional.empty();
    return java.util.Optional.ofNullable(quests.get(id));
  }


  public void onJoin(UUID uuid) {
    loadPlayerAsync(uuid);
  }

  public void onQuit(UUID uuid) {
    flushPlayerAsync(uuid);
    states.remove(uuid);
  }

  public void refreshPremiumAllAsync() {
    for (UUID uuid : states.keySet()) refreshPremiumAsync(uuid);
  }

  public PlayerBpState state(UUID uuid) {
    return states.get(uuid);
  }

  public record ClaimResult(boolean ok, String message) {}

  public ClaimResult claim(Player player, int tier, RewardTrack track) {
    if (player == null) return new ClaimResult(false, "player missing");
    if (tier <= 0) return new ClaimResult(false, "invalid tier");

    PlayerBpState st = states.get(player.getUniqueId());
    if (st == null) return new ClaimResult(false, "state not loaded yet");

    int curTier = st.tier();
    if (curTier < tier) return new ClaimResult(false, "tier not reached (" + curTier + ")");

    var t = rewards.tier(tier);
    if (t.isEmpty()) return new ClaimResult(false, "no rewards for tier " + tier);

    boolean did = false;

    if (track == RewardTrack.FREE) {
      did = claimFree(player, st, t.get()) || did;
    } else if (track == RewardTrack.PREMIUM) {
      did = claimPremium(player, st, t.get()) || did;
    } else {
      did = claimFree(player, st, t.get()) || did;
      did = claimPremium(player, st, t.get()) || did;
    }

    if (!did) return new ClaimResult(false, "already claimed");
    return new ClaimResult(true, "claimed");
  }

  public int claimAll(Player player) {
    if (player == null) return 0;
    PlayerBpState st = states.get(player.getUniqueId());
    if (st == null) return 0;

    int curTier = st.tier();
    int claimed = 0;

    for (int tier = 1; tier <= curTier; tier++) {
      var t = rewards.tier(tier);
      if (t.isEmpty()) continue;

      boolean did = false;
      did = claimFree(player, st, t.get()) || did;
      did = claimPremium(player, st, t.get()) || did;

      if (did) claimed++;
    }

    return claimed;
  }

  private boolean claimFree(Player player, PlayerBpState st, net.orbis.zakum.battlepass.rewards.TierRewards t) {
    if (st.hasClaim(false, t.tier())) return false;
    st.markClaim(false, t.tier());
    rewardExec.execute(player, t.freeRewards());
    return true;
  }

  private boolean claimPremium(Player player, PlayerBpState st, net.orbis.zakum.battlepass.rewards.TierRewards t) {
    if (!st.premium) return false;
    if (st.hasClaim(true, t.tier())) return false;
    st.markClaim(true, t.tier());
    rewardExec.execute(player, t.premiumRewards());
    return true;
  }

  private void refreshPremiumAsync(UUID uuid) {
    PlayerBpState st = states.get(uuid);
    if (st == null) return;

    premium.isPremium(uuid).whenComplete((ok, err) -> {
      if (err != null) return;
      st.premium = Boolean.TRUE.equals(ok);
    });
  }

  private void onAction(ActionEvent e) {
    PlayerBpState st = states.get(e.playerId());
    if (st == null) return;

    // Apply progress booster.
    long amt = e.amount();
    double progMult = zakum.boosters().multiplier(e.playerId(), EntitlementScope.SERVER, progressServerId, BoosterKind.BATTLEPASS_PROGRESS);
    long boosted = (long) Math.max(1, Math.floor(amt * progMult));
    ActionEvent boostedEvent = (boosted == amt) ? e : new ActionEvent(e.type(), e.playerId(), boosted, e.key(), e.value());

    for (QuestDef q : index.candidates(boostedEvent)) {
      if (!isQuestActive(q)) continue;
      if (q.premiumOnly() && !st.premium) continue;
      applyQuest(boostedEvent, q, st);
    }
  }

  private boolean isQuestActive(QuestDef q) {
    if (q == null) return false;
    QuestCadence c = q.cadence();
    if (c == null) return true;

    if (c == QuestCadence.WEEKLY) {
      List<Integer> weeks = q.availableWeeks();
      if (weeks != null && !weeks.isEmpty()) {
        return weeks.contains(currentWeek);
      }
    }
    return true;
  }

  private void applyQuest(ActionEvent e, QuestDef q, PlayerBpState st) {
    var cur = st.getQuest(q.id());
    if (cur.stepIdx() >= q.steps().size()) return;

    QuestStep step = q.steps().get(cur.stepIdx());

    if (!step.type().equalsIgnoreCase(e.type())) return;
    if (!step.key().isBlank() && !step.key().equalsIgnoreCase(e.key())) return;
    if (!step.value().isBlank() && !step.value().equalsIgnoreCase(e.value())) return;

    long next = cur.progress() + e.amount();
    if (next < step.required()) {
      st.setQuest(q.id(), cur.stepIdx(), next);
      return;
    }

    int nextIdx = cur.stepIdx() + 1;
    st.setQuest(q.id(), nextIdx, 0);

    if (nextIdx >= q.steps().size()) {
      awardPoints(e.playerId(), q, st);
    }
  }

  private void awardPoints(UUID playerId, QuestDef q, PlayerBpState st) {
    long base = q.points();
    if (base <= 0) return;

    if (st.premium && q.premiumBonusPoints() > 0) {
      base += q.premiumBonusPoints();
    }

    double pointsMult = zakum.boosters().multiplier(playerId, EntitlementScope.SERVER, progressServerId, BoosterKind.BATTLEPASS_POINTS);
    long finalPoints = (long) Math.max(1, Math.floor(base * pointsMult));

    st.addPoints(finalPoints);

    // Tier calculation (binary search).
    int newTier = rewards.tierForPoints(st.points());
    int oldTier = st.tier();
    if (newTier > oldTier) {
      st.setTier(newTier);
    }
  }

  public void flushAllAsync() {
    if (zakum.database().state() != DatabaseState.ONLINE) return;

    // Avoid spawning N async tasks for N players (task spam under load).
    // Take a stable snapshot and flush in bounded batches.
    java.util.ArrayList<UUID> uuids = new java.util.ArrayList<>(states.keySet());
    if (uuids.isEmpty()) return;

    int maxPerBatch = Math.max(10, plugin.getConfig().getInt("battlepass.flush.maxPlayersPerBatch", 200));
    scheduleFlushBatch(uuids, 0, maxPerBatch);
  }

  /**
   * Flushes all currently loaded player deltas and completes when the flush is finished.
   *
   * Intended for admin maintenance flows (backup/rollover/purge) where you need a consistent
   * snapshot before destructive DB operations.
   */
  public java.util.concurrent.CompletableFuture<Void> flushAllAndWaitAsync() {
    if (zakum.database().state() != DatabaseState.ONLINE) {
      return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    java.util.ArrayList<UUID> uuids = new java.util.ArrayList<>(states.keySet());
    if (uuids.isEmpty()) return java.util.concurrent.CompletableFuture.completedFuture(null);

    int maxPerBatch = Math.max(10, plugin.getConfig().getInt("battlepass.flush.maxPlayersPerBatch", 200));
    java.util.concurrent.CompletableFuture<Void> done = new java.util.concurrent.CompletableFuture<>();
    scheduleFlushBatchDone(uuids, 0, maxPerBatch, done);
    return done;
  }

  private void scheduleFlushBatch(java.util.List<UUID> uuids, int start, int maxPerBatch) {
    if (uuids == null) return;
    if (start >= uuids.size()) return;
    int end = Math.min(uuids.size(), start + Math.max(1, maxPerBatch));

    zakum.async().execute(() -> {
      for (int i = start; i < end; i++) {
        try {
          flushPlayerDeltaNow(uuids.get(i));
        } catch (Throwable ignored) {
          // Best-effort: don't let one bad row kill the whole batch.
        }
      }

      if (end < uuids.size()) {
        scheduleFlushBatch(uuids, end, maxPerBatch);
      }
    });
  }

  private void scheduleFlushBatchDone(java.util.List<UUID> uuids, int start, int maxPerBatch, java.util.concurrent.CompletableFuture<Void> done) {
    if (done == null) return;
    if (uuids == null) {
      done.complete(null);
      return;
    }
    if (start >= uuids.size()) {
      done.complete(null);
      return;
    }
    int end = Math.min(uuids.size(), start + Math.max(1, maxPerBatch));

    zakum.async().execute(() -> {
      try {
        for (int i = start; i < end; i++) {
          try {
            flushPlayerDeltaNow(uuids.get(i));
          } catch (Throwable ignored) {}
        }

        if (end < uuids.size()) {
          scheduleFlushBatchDone(uuids, end, maxPerBatch, done);
        } else {
          done.complete(null);
        }
      } catch (Throwable t) {
        done.completeExceptionally(t);
      }
    });
  }

  private void flushPlayerDeltaNow(UUID uuid) {
    if (uuid == null) return;
    PlayerBpState st = states.get(uuid);
    if (st == null) return;

    PlayerBpState.DeltaSnapshot delta = st.snapshotDeltaAndClear();
    boolean hasClaims = delta.dirtyClaims() != null && !delta.dirtyClaims().isEmpty();
    if (!delta.writeProgress() && delta.dirtySteps().isEmpty() && !hasClaims) return;

    BattlePassStorage.flushPlayerDelta(zakum, progressServerId, season, uuid, delta);
  }

  public void flushPlayerAsync(UUID uuid) {
    if (zakum.database().state() != DatabaseState.ONLINE) return;

    PlayerBpState st = states.get(uuid);
    if (st == null) return;

    PlayerBpState.DeltaSnapshot delta = st.snapshotDeltaAndClear();
    boolean hasClaims = delta.dirtyClaims() != null && !delta.dirtyClaims().isEmpty();
    if (!delta.writeProgress() && delta.dirtySteps().isEmpty() && !hasClaims) return;

    zakum.async().execute(() -> BattlePassStorage.flushPlayerDelta(zakum, progressServerId, season, uuid, delta));
  }

  private void loadPlayerAsync(UUID uuid) {
    if (zakum.database().state() != DatabaseState.ONLINE) {
      PlayerBpState st = new PlayerBpState();
      // Ensure quests exist to avoid missing state in memory.
      for (String qid : quests.keySet()) st.ensureQuest(qid);
      states.putIfAbsent(uuid, st);
      refreshPremiumAsync(uuid);
      return;
    }

    zakum.async().execute(() -> {
      PlayerBpState st = BattlePassStorage.loadPlayer(zakum, progressServerId, season, quests, uuid);

      // Rollover based on persisted period markers.
      BattlePassStorage.PeriodState period = BattlePassStorage.loadPeriod(zakum, progressServerId, season, uuid);

      long nowDay = epochDayNow();
      long nowWeek = weeklyTokenNow();

      boolean changed = false;
      if (period.dailyDay() != nowDay) {
        resetQuests(st, dailyQuestIds);
        changed = true;
      }
      if (period.weeklyWeek() != nowWeek) {
        resetQuests(st, weeklyQuestIds);
        changed = true;
      }

      BattlePassStorage.upsertPeriod(zakum, progressServerId, season, uuid, nowDay, nowWeek);

      if (changed) {
        PlayerBpState.DeltaSnapshot delta = st.snapshotDeltaAndClear();
        BattlePassStorage.flushPlayerDelta(zakum, progressServerId, season, uuid, delta);
      }

      // tier resync from points (safety if rewards table changed)
      int expectedTier = rewards.tierForPoints(st.points());
      if (expectedTier != st.tier()) {
        st.setTier(expectedTier);
        PlayerBpState.DeltaSnapshot delta = st.snapshotDeltaAndClear();
        BattlePassStorage.flushPlayerDelta(zakum, progressServerId, season, uuid, delta);
      }

      states.put(uuid, st);
      refreshPremiumAsync(uuid);
    });
  }

  private void resetQuests(PlayerBpState st, Set<String> questIds) {
    if (st == null || questIds == null || questIds.isEmpty()) return;
    for (String q : questIds) st.resetQuest(q);
  }

  private long epochDayNow() {
    return LocalDate.now(zoneId).toEpochDay();
  }

  /** ISO week id as YYYYWW (e.g. 202601). */
  private long isoWeekIdNow() {
    LocalDate d = LocalDate.now(zoneId);
    WeekFields wf = WeekFields.ISO;
    int week = d.get(wf.weekOfWeekBasedYear());
    int year = d.get(wf.weekBasedYear());
    return (year * 100L) + week;
  }

  /**
   * Weekly reset token used for "weekly" quest resets.
   *
   * AdvancedPlugins-style expectation: weekly quests typically reset on "battlepass week" changes
   * (admin controlled), not strictly on ISO calendar weeks.
   */
  private long weeklyTokenNow() {
    String mode = plugin.getConfig().getString("battlepass.weeks.resetMode", "BATTLEPASS_WEEK");
    if (mode != null && mode.equalsIgnoreCase("ISO")) {
      return isoWeekIdNow();
    }
    // Default: battlepass week number.
    return (long) currentWeek;
  }

  private static ZoneId parseZone(String zone) {
    if (zone == null || zone.isBlank()) return ZoneId.of("UTC");
    try { return ZoneId.of(zone.trim()); }
    catch (Exception ignored) { return ZoneId.of("UTC"); }
  }

  private static String resolveProgressServerId(Plugin plugin, ZakumApi zakum) {
    String override = plugin.getConfig().getString("battlepass.progressServerIdOverride", "").trim();
    return override.isBlank() ? zakum.server().serverId() : override;
  }
}
