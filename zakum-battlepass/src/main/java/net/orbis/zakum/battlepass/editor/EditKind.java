package net.orbis.zakum.battlepass.editor;

/** Kinds of pending chat-input edits. */
public enum EditKind {
  QUEST_NAME,
  QUEST_POINTS,
  QUEST_PREMIUM_BONUS,
  QUEST_WEEKS,
  STEP_ADD,
  STEP_REQUIRED,
  STEP_VALUE,

  TIER_ADD,
  TIER_POINTS_REQUIRED,

  REWARD_ADD,
  REWARD_ADD_COMMAND
}
