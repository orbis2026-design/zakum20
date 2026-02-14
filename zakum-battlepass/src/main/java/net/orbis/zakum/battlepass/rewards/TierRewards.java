package net.orbis.zakum.battlepass.rewards;

import java.util.List;

/**
 * Rewards for one tier.
 *
 * pointsRequired: points needed to reach this tier.
 */
public record TierRewards(
  int tier,
  long pointsRequired,
  List<RewardDef> freeRewards,
  List<RewardDef> premiumRewards
) {}
