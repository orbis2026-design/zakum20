package net.orbis.zakum.core.action;

import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.api.action.AceEngine;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ZakumAceEngine implements AceEngine {

  private static final Pattern ACE_PATTERN = Pattern.compile("\\[(\\w+)]\\s*([^@]+)?(?:@(\\w+))?\\s*(.*)");

  private final Map<String, EffectAction> effects;

  public ZakumAceEngine() {
    this.effects = new ConcurrentHashMap<>();
    registerDefaults();
  }

  @Override
  public void executeScript(List<String> script, ActionContext context) {
    if (script == null || script.isEmpty() || context == null || context.actor() == null) return;

    for (String raw : script) {
      if (raw == null) continue;
      String line = raw.trim();
      if (line.isEmpty() || line.startsWith("#")) continue;

      Matcher m = ACE_PATTERN.matcher(line);
      if (!m.matches()) continue;

      String effectKey = normalize(m.group(1));
      String value = trim(m.group(2));
      String targetKey = trim(m.group(3));
      String tail = trim(m.group(4));

      EffectAction action = effects.get(effectKey);
      if (action == null) continue;

      Map<String, String> params = parseParams(value, tail);
      List<Entity> targets = resolveTargets(context, targetKey, params);
      if (targets.isEmpty()) {
        targets = List.of(context.actor());
      }

      try {
        action.apply(context, targets, params);
      } catch (Throwable ignored) {
        // Script execution should be fault-tolerant across individual effect lines.
      }
    }
  }

  @Override
  public void registerEffect(String key, EffectAction action) {
    if (key == null || key.isBlank() || action == null) return;
    effects.put(normalize(key), action);
  }

  private void registerDefaults() {
    registerEffect("COMMAND", this::applyCommand);
  }

  private void applyCommand(ActionContext context, List<Entity> targets, Map<String, String> params) {
    String command = firstNonBlank(params.get("cmd"), params.get("command"), params.get("value"));
    if (command == null || command.isBlank()) return;

    Player actor = context.actor();
    String actorName = actor.getName();
    String actorUuid = actor.getUniqueId().toString();

    for (Entity target : targets) {
      String targetName = (target == null) ? actorName : target.getName();
      String targetUuid = (target == null) ? actorUuid : target.getUniqueId().toString();
      String prepared = command
        .replace("%player%", actorName)
        .replace("%uuid%", actorUuid)
        .replace("%target%", targetName)
        .replace("%target_uuid%", targetUuid);

      if (prepared.startsWith("/")) prepared = prepared.substring(1);

      String finalCommand = prepared;
      ZakumApi.get().getScheduler().runGlobal(() ->
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand)
      );
    }
  }

  private static List<Entity> resolveTargets(ActionContext context, String targetKey, Map<String, String> params) {
    Player actor = context.actor();
    if (actor == null) return Collections.emptyList();

    String target = normalize(targetKey == null || targetKey.isBlank() ? "SELF" : targetKey);

    return switch (target) {
      case "SELF" -> List.of(actor);
      case "VICTIM" -> context.victim().<List<Entity>>map(List::of).orElse(List.of(actor));
      case "ALL" -> new ArrayList<>(actor.getWorld().getPlayers());
      case "RADIUS", "NEARBY" -> nearby(actor, params);
      case "ALLIES" -> allies(actor);
      default -> List.of(actor);
    };
  }

  private static List<Entity> nearby(Player actor, Map<String, String> params) {
    double radius = parseDouble(firstNonBlank(params.get("radius"), params.get("value")), 6.0);
    double r = Math.max(0.5, radius);

    List<Entity> out = new ArrayList<>();
    out.add(actor);
    for (Entity e : actor.getNearbyEntities(r, r, r)) {
      out.add(e);
    }
    return out;
  }

  private static List<Entity> allies(Player actor) {
    var board = actor.getScoreboard();
    if (board == null) return List.of(actor);
    var team = board.getEntryTeam(actor.getName());
    if (team == null) return List.of(actor);

    List<Entity> out = new ArrayList<>();
    out.add(actor);
    for (Player p : actor.getWorld().getPlayers()) {
      if (p.equals(actor)) continue;
      var other = p.getScoreboard().getEntryTeam(p.getName());
      if (other != null && other.getName().equals(team.getName())) {
        out.add(p);
      }
    }
    return out;
  }

  private static Map<String, String> parseParams(String value, String tail) {
    Map<String, String> params = new LinkedHashMap<>();
    if (value != null && !value.isBlank()) {
      params.put("value", value.trim());
    }
    if (tail == null || tail.isBlank()) return params;

    for (String token : tail.trim().split("\\s+")) {
      if (token.isBlank()) continue;
      int idx = token.indexOf('=');
      if (idx < 1 || idx == token.length() - 1) continue;
      String key = token.substring(0, idx).trim().toLowerCase(Locale.ROOT);
      String val = token.substring(idx + 1).trim();
      if (key.isEmpty() || val.isEmpty()) continue;
      params.put(key, val);
    }

    return params;
  }

  private static String normalize(String value) {
    if (value == null) return "";
    return value.trim().toUpperCase(Locale.ROOT);
  }

  private static String trim(String value) {
    return value == null ? null : value.trim();
  }

  private static double parseDouble(String value, double fallback) {
    if (value == null || value.isBlank()) return fallback;
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  private static String firstNonBlank(String... values) {
    if (values == null) return null;
    for (String value : values) {
      if (value != null && !value.isBlank()) return value;
    }
    return null;
  }
}
