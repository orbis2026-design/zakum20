package net.orbis.zakum.pets.model;

import org.bukkit.entity.EntityType;

import java.util.List;

public record PetDef(
  String id,
  String name,
  EntityType entityType,
  FollowMode followMode,
  int xpPerMobKill,
  List<String> summonScript,
  List<String> dismissScript,
  List<String> levelUpScript
) {
  public PetDef {
    summonScript = summonScript == null ? List.of() : List.copyOf(summonScript);
    dismissScript = dismissScript == null ? List.of() : List.copyOf(dismissScript);
    levelUpScript = levelUpScript == null ? List.of() : List.copyOf(levelUpScript);
  }
}
