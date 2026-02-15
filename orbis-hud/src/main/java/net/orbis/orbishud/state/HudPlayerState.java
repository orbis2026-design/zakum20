package net.orbis.orbishud.state;

import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class HudPlayerState {

  private final UUID playerId;
  private String forcedProfileId;
  private String lastSignature;
  private final Set<String> lastEntries;

  private Scoreboard scoreboard;
  private Objective objective;

  public HudPlayerState(UUID playerId) {
    this.playerId = playerId;
    this.lastEntries = new LinkedHashSet<>();
  }

  public UUID playerId() {
    return playerId;
  }

  public String forcedProfileId() {
    return forcedProfileId;
  }

  public void forcedProfileId(String forcedProfileId) {
    this.forcedProfileId = forcedProfileId;
  }

  public String lastSignature() {
    return lastSignature;
  }

  public Set<String> lastEntries() {
    return Set.copyOf(lastEntries);
  }

  public Scoreboard scoreboard() {
    return scoreboard;
  }

  public Objective objective() {
    return objective;
  }

  public void updateRenderState(Scoreboard scoreboard, Objective objective, String signature, Set<String> entries) {
    this.scoreboard = scoreboard;
    this.objective = objective;
    this.lastSignature = signature;
    this.lastEntries.clear();
    this.lastEntries.addAll(entries);
  }

  public void clearRenderState() {
    this.lastSignature = null;
    this.lastEntries.clear();
    this.scoreboard = null;
    this.objective = null;
  }
}