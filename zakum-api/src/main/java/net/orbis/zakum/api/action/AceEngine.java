package net.orbis.zakum.api.action;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface AceEngine {

  void executeScript(List<String> script, ActionContext context);

  void registerEffect(String key, EffectAction action);

  record ActionContext(Player actor, Optional<Entity> victim, Map<String, Object> metadata) {
    public static ActionContext of(Player p) {
      return new ActionContext(p, Optional.empty(), new HashMap<>());
    }
  }

  @FunctionalInterface
  interface EffectAction {
    void apply(ActionContext context, List<Entity> targets, Map<String, String> params);
  }
}
