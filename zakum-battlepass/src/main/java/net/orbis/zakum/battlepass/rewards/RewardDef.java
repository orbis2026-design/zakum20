package net.orbis.zakum.battlepass.rewards;

import java.util.List;

/**
 * One reward definition.
 *
 * Supports command, direct message, and raw ACE script rewards.
 */
public record RewardDef(
  RewardType type,
  List<String> commands,
  List<String> messages,
  List<String> aceScript
) {
  public RewardDef {
    type = type == null ? RewardType.COMMAND : type;
    commands = commands == null ? List.of() : List.copyOf(commands);
    messages = messages == null ? List.of() : List.copyOf(messages);
    aceScript = aceScript == null ? List.of() : List.copyOf(aceScript);
  }
}
