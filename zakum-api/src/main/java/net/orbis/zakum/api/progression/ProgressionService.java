package net.orbis.zakum.api.progression;

public interface ProgressionService {

  long xpForLevel(int level, long xpBase, double xpGrowth);

  int levelForXp(long totalXp, int maxLevel, long xpBase, double xpGrowth);

  long pointsForTier(int tier, long base, long step);
}
