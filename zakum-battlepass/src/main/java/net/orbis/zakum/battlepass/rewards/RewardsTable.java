package net.orbis.zakum.battlepass.rewards;

import java.util.*;

/**
 * Immutable rewards table for the season.
 *
 * Tier numbers are 1..maxTier.
 */
public final class RewardsTable {

  private final List<TierRewards> tiers; // sorted
  private final long[] requiredByTier;   // index=1..N

  public RewardsTable(List<TierRewards> tiers) {
    if (tiers == null) tiers = List.of();
    ArrayList<TierRewards> copy = new ArrayList<>(tiers);
    copy.sort(Comparator.comparingInt(TierRewards::tier));
    this.tiers = List.copyOf(copy);

    int max = this.tiers.isEmpty() ? 0 : this.tiers.get(this.tiers.size() - 1).tier();
    this.requiredByTier = new long[max + 1];

    for (TierRewards t : this.tiers) {
      int idx = t.tier();
      if (idx <= 0 || idx >= requiredByTier.length) continue;
      requiredByTier[idx] = Math.max(0, t.pointsRequired());
    }

    // Ensure monotonic non-decreasing requirements (failsafe).
    long last = 0;
    for (int i = 1; i < requiredByTier.length; i++) {
      long v = requiredByTier[i];
      if (v < last) v = last;
      requiredByTier[i] = v;
      last = v;
    }
  }

  public int maxTier() {
    return requiredByTier.length == 0 ? 0 : requiredByTier.length - 1;
  }

  public long pointsRequired(int tier) {
    if (tier <= 0 || tier >= requiredByTier.length) return Long.MAX_VALUE;
    return requiredByTier[tier];
  }

  /** Compute tier for points using binary search over required thresholds. */
  public int tierForPoints(long points) {
    if (requiredByTier.length <= 1) return 0;
    long p = Math.max(0, points);

    int lo = 1;
    int hi = requiredByTier.length - 1;
    int ans = 0;

    while (lo <= hi) {
      int mid = (lo + hi) >>> 1;
      if (p >= requiredByTier[mid]) {
        ans = mid;
        lo = mid + 1;
      } else {
        hi = mid - 1;
      }
    }
    return ans;
  }

  public Optional<TierRewards> tier(int tier) {
    // tiers list is small; linear scan ok, but we can binary search if needed later.
    for (TierRewards t : tiers) if (t.tier() == tier) return Optional.of(t);
    return Optional.empty();
  }

  public List<TierRewards> all() {
    return tiers;
  }
}
