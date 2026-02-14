package net.orbis.zakum.core.action;

import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.api.action.AceEngine;
import net.orbis.zakum.api.capability.ZakumCapabilities;
import net.orbis.zakum.api.social.SocialService;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.UUID;

public final class ZakumAceEngine implements AceEngine {

  // Syntax: [EFFECT] value @TARGETER {param=value ...}
  private static final Pattern ACE_PATTERN = Pattern.compile(
    "^\\[(?<effect>\\w+)]\\s*(?<value>[^@\\{]+)?(?:@(?<targeter>\\w+))?\\s*(?:\\{(?<params>.*)})?$"
  );

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

      String effectKey = normalize(m.group("effect"));
      String value = trim(m.group("value"));
      String targetKey = trim(m.group("targeter"));
      String inlineParams = trim(m.group("params"));

      EffectAction action = effects.get(effectKey);
      if (action == null) continue;

      Map<String, String> params = parseParams(value, inlineParams);
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
    StandardEffects.registerDefaults(this);
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
      case "FRIENDS" -> friends(actor);
      case "RIVALS" -> rivals(actor);
      default -> List.of(actor);
    };
  }

  private static List<Entity> nearby(Player actor, Map<String, String> params) {
    String value = params.get("radius");
    if (value == null || value.isBlank()) value = params.get("value");
    double radius = parseDouble(value, 6.0);
    double r = Math.max(0.5, radius);

    List<Entity> out = new ArrayList<>();
    out.add(actor);
    for (Entity e : actor.getNearbyEntities(r, r, r)) {
      out.add(e);
    }
    return out;
  }

  private static List<Entity> allies(Player actor) {
    List<Entity> fromSocial = selectBySocial(actor, SocialRelation.ALLY);
    if (!fromSocial.isEmpty()) return fromSocial;

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

  private static List<Entity> friends(Player actor) {
    List<Entity> fromSocial = selectBySocial(actor, SocialRelation.FRIEND);
    return fromSocial.isEmpty() ? List.of(actor) : fromSocial;
  }

  private static List<Entity> rivals(Player actor) {
    List<Entity> fromSocial = selectBySocial(actor, SocialRelation.RIVAL);
    return fromSocial.isEmpty() ? List.of(actor) : fromSocial;
  }

  private static List<Entity> selectBySocial(Player actor, SocialRelation relation) {
    ZakumApi api = ZakumApi.get();
    if (api == null) return List.of();
    SocialService social = api.capability(ZakumCapabilities.SOCIAL).orElse(null);
    if (social == null) return List.of();

    SocialService.SocialSnapshot snapshot = social.snapshot(actor.getUniqueId());
    if (snapshot == null) return List.of();
    var ids = switch (relation) {
      case FRIEND -> snapshot.friends();
      case ALLY -> snapshot.allies();
      case RIVAL -> snapshot.rivals();
    };
    if (ids.isEmpty()) return List.of();

    List<Entity> out = new ArrayList<>();
    out.add(actor);
    for (Player player : actor.getWorld().getPlayers()) {
      UUID id = player.getUniqueId();
      if (!id.equals(actor.getUniqueId()) && ids.contains(id)) {
        out.add(player);
      }
    }
    return out;
  }

  private enum SocialRelation {
    FRIEND,
    ALLY,
    RIVAL
  }

  private static Map<String, String> parseParams(String value, String tail) {
    Map<String, String> params = new LinkedHashMap<>();
    if (value != null && !value.isBlank()) {
      String normalized = value.trim();
      params.put("value", normalized);
      params.put("raw_value", normalized);
      parseKeyValues(normalized, params);
    }
    if (tail != null && !tail.isBlank()) {
      parseKeyValues(tail.trim(), params);
    }
    return params;
  }

  private static void parseKeyValues(String text, Map<String, String> params) {
    for (String token : text.split("\\s+")) {
      if (token.isBlank()) continue;
      int idx = token.indexOf('=');
      if (idx < 1 || idx == token.length() - 1) continue;
      String key = token.substring(0, idx).trim().toLowerCase(Locale.ROOT);
      String val = token.substring(idx + 1).trim();
      if (key.isEmpty() || val.isEmpty()) continue;
      params.put(key, val);
    }
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
}
