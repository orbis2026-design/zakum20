package net.orbis.zakum.battlepass.rewards;

import java.util.List;

/**
 * One reward definition.
 *
 * v1 supports COMMAND rewards (console command list).
 */
public record RewardDef(
  RewardType type,
  List<String> commands
) {}
