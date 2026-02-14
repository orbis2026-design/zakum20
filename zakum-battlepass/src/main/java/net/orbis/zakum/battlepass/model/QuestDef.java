package net.orbis.zakum.battlepass.model;

import java.util.List;

/**
 * Quest definition.
 *
 * Notes:
 * - cadence controls when the quest resets / is available.
 * - availableWeeks applies to WEEKLY quests (planned week schedules).
 */
public record QuestDef(
  String id,
  String name,
  long points,
  boolean premiumOnly,
  long premiumBonusPoints,
  QuestCadence cadence,
  List<Integer> availableWeeks,
  List<QuestStep> steps
) {}
