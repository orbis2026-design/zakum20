package net.orbis.orbishud.state;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HudStateCache {

  private final ConcurrentHashMap<UUID, HudPlayerState> states = new ConcurrentHashMap<>();

  public HudPlayerState getOrCreate(UUID playerId) {
    return states.computeIfAbsent(playerId, HudPlayerState::new);
  }

  public HudPlayerState remove(UUID playerId) {
    return states.remove(playerId);
  }

  public int size() {
    return states.size();
  }

  public Collection<HudPlayerState> values() {
    return states.values();
  }

  public void clear() {
    states.clear();
  }
}