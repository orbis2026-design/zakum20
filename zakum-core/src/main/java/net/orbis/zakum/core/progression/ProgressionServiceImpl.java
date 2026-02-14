package net.orbis.zakum.core.progression;

import net.orbis.zakum.api.progression.ProgressionService;

public final class ProgressionServiceImpl implements ProgressionService {

  @Override
  public long xpForLevel(int level, long xpBase, double xpGrowth) {
    int lvl = Math.max(1, level);
    long base = Math.max(1L, xpBase);
    double growth = Math.max(1.0, xpGrowth);
    double raw = base * Math.pow(growth, lvl - 1);
    return clampToLong(raw);
  }

  @Override
  public int levelForXp(long totalXp, int maxLevel, long xpBase, double xpGrowth) {
    long xp = Math.max(0L, totalXp);
    int cap = Math.max(1, maxLevel);

    long cumulative = 0L;
    for (int level = 1; level <= cap; level++) {
      long req = xpForLevel(level, xpBase, xpGrowth);
      if (Long.MAX_VALUE - cumulative < req) return cap;
      cumulative += req;
      if (xp < cumulative) {
        return level;
      }
    }
    return cap;
  }

  @Override
  public long pointsForTier(int tier, long base, long step) {
    int t = Math.max(1, tier);
    long b = Math.max(0L, base);
    long s = Math.max(0L, step);
    long add = (long) (t - 1) * s;
    if (Long.MAX_VALUE - b < add) return Long.MAX_VALUE;
    return b + add;
  }

  private static long clampToLong(double value) {
    if (Double.isNaN(value) || value <= 0.0d) return 0L;
    if (value >= Long.MAX_VALUE) return Long.MAX_VALUE;
    return (long) Math.ceil(value);
  }
}
