package net.orbis.zakum.battlepass.editor;

/**
 * Separate menu types for the BattlePass in-game YAML editors.
 *
 * Kept separate from player menus to avoid mixing click logic.
 */
public enum EditorMenuType {
  ADMIN_HOME,
  QUEST_LIST,
  QUEST_DETAIL,
  QUEST_STEPS,
  REWARD_TIER_LIST,
  REWARD_TIER_DETAIL,
  REWARD_LIST,      // free/premium list for a tier
  REWARD_COMMANDS   // commands list for a reward id
}
