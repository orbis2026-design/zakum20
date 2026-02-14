package net.orbis.zakum.battlepass.state;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Per-player BattlePass state.
 *
 * Threading:
 * - Action processing runs on the ActionBus callback thread (typically main thread).
 * - Load/flush run async.
 *
 * We use a small per-state lock and snapshot deltas for DB writes.
 */
public final class PlayerBpState {

  private final Object lock = new Object();

  private long points;
  private int tier;

  public volatile boolean premium = false;

  // questId -> step state (internal, protected by lock)
  private final Map<String, StepState> quests = new HashMap<>();

  // claimed tiers (internal, protected by lock)
  private final Set<Integer> claimedFree = new HashSet<>();
  private final Set<Integer> claimedPremium = new HashSet<>();

  // dirty flags (minimize DB writes)
  private boolean dirtyProgress = false;

  // dirty claim inserts
  private final Set<ClaimSnap> dirtyClaims = new HashSet<>();

  public long points() { synchronized (lock) { return points; } }
  public int tier() { synchronized (lock) { return tier; } }

  public void seedProgress(int tier, long points) {
    synchronized (lock) {
      this.tier = tier;
      this.points = points;
      this.dirtyProgress = false;
    }
  }

  public void setTier(int tier) {
    synchronized (lock) {
      this.tier = tier;
      this.dirtyProgress = true;
    }
  }

  public void addPoints(long delta) {
    if (delta == 0) return;
    synchronized (lock) {
      this.points += delta;
      this.dirtyProgress = true;
    }
  }

  public void seedClaim(boolean premium, int tier) {
    synchronized (lock) {
      if (premium) claimedPremium.add(tier);
      else claimedFree.add(tier);
    }
  }

  public boolean hasClaim(boolean premium, int tier) {
    synchronized (lock) {
      return premium ? claimedPremium.contains(tier) : claimedFree.contains(tier);
    }
  }

  /** Mark a claim as completed and dirty for DB insert. */
  public void markClaim(boolean premium, int tier) {
    synchronized (lock) {
      boolean added = premium ? claimedPremium.add(tier) : claimedFree.add(tier);
      if (added) dirtyClaims.add(new ClaimSnap(premium, tier));
    }
  }

  /** Ensure quest exists (no dirty). */
  public void ensureQuest(String questId) {
    synchronized (lock) {
      quests.computeIfAbsent(questId, k -> new StepState());
    }
  }

  /** Seed quest state without marking dirty (load path). */
  public void seedQuest(String questId, int stepIdx, long progress) {
    synchronized (lock) {
      StepState ss = quests.computeIfAbsent(questId, k -> new StepState());
      ss.stepIdx = stepIdx;
      ss.progress = progress;
      ss.dirty = false;
    }
  }

  /** Read current quest step/progress (creates if missing). */
  public StepStateSnap getQuest(String questId) {
    synchronized (lock) {
      StepState ss = quests.computeIfAbsent(questId, k -> new StepState());
      return new StepStateSnap(ss.stepIdx, ss.progress);
    }
  }

  /** Write quest step/progress (marks dirty). */
  public void setQuest(String questId, int stepIdx, long progress) {
    synchronized (lock) {
      StepState ss = quests.computeIfAbsent(questId, k -> new StepState());
      ss.stepIdx = stepIdx;
      ss.progress = progress;
      ss.dirty = true;
    }
  }

  /** Reset a quest's progress (used for daily/weekly rollover). */
  public void resetQuest(String questId) {
    setQuest(questId, 0, 0);
  }

  public Set<String> questIds() {
    synchronized (lock) {
      return Set.copyOf(quests.keySet());
    }
  }

  /**
   * Snapshot only dirty deltas and clear dirty flags.
   * Used by async flush to avoid races and reduce DB load.
   */
  public DeltaSnapshot snapshotDeltaAndClear() {
    synchronized (lock) {
      long pointsSnap = this.points;
      int tierSnap = this.tier;

      boolean writeProgress = this.dirtyProgress;
      this.dirtyProgress = false;

      Map<String, StepStateSnap> dirtySteps = new HashMap<>();
      for (var e : quests.entrySet()) {
        StepState ss = e.getValue();
        if (!ss.dirty) continue;
        ss.dirty = false;
        dirtySteps.put(e.getKey(), new StepStateSnap(ss.stepIdx, ss.progress));
      }

      Set<ClaimSnap> claimsSnap = Set.copyOf(dirtyClaims);
      dirtyClaims.clear();

      return new DeltaSnapshot(writeProgress, tierSnap, pointsSnap, dirtySteps, claimsSnap);
    }
  }

  public static final class StepState {
    public int stepIdx = 0;
    public long progress = 0;
    public boolean dirty = false;
  }

  public record StepStateSnap(int stepIdx, long progress) {}

  public record ClaimSnap(boolean premium, int tier) {}

  public record DeltaSnapshot(boolean writeProgress, int tier, long points, Map<String, StepStateSnap> dirtySteps, Set<ClaimSnap> dirtyClaims) {}
}
