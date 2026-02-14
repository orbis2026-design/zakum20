package net.orbis.zakum.battlepass.editor;

import java.util.UUID;

/**
 * One pending editor prompt that expects a chat response.
 *
 * Fields:
 * - a/b: lightweight context (questId/tier/rewardId/track)
 * - index: step index or command index (0-based). -1 if unused.
 */
public record EditSession(
  UUID playerId,
  EditKind kind,
  String a,
  String b,
  int index,
  long expiresAtMs,
  int timeoutTaskId
) {}
