package net.orbis.zakum.core.action;

import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.api.action.AceEngine;
import net.orbis.zakum.api.capability.ZakumCapabilities;
import net.orbis.zakum.api.social.SocialService;
import net.orbis.zakum.core.metrics.MetricsMonitor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.UUID;

public final class ZakumAceEngine implements AceEngine {

  // Syntax: [EFFECT] value @TARGETER {param=value ...}
  private static final Pattern ACE_PATTERN = Pattern.compile(
    "^\\[(?<effect>\\w+)]\\s*(?<value>[^@\\{]+)?(?:@(?<targeter>\\w+))?\\s*(?:\\{(?<params>.*)})?$"
  );
  private static final Set<String> KNOWN_TARGETERS = Set.of(
    "SELF",
    "VICTIM",
    "ALL",
    "RADIUS",
    "NEARBY",
    "ALLIES",
    "FRIENDS",
    "RIVALS"
  );
  private static final int DEFAULT_BOUNDED_TARGETS = 24;
  private static final int MAX_BOUNDED_TARGETS = 128;
  private static final double DEFAULT_RADIUS = 6.0d;
  private static final double MIN_RADIUS = 0.5d;
  private static final double MAX_RADIUS = 64.0d;

  private final Map<String, EffectAction> effects;
  private final MetricsMonitor metrics;
  private final AceDiagnosticsTracker diagnostics;

  public ZakumAceEngine() {
    this(null, AceDiagnosticsTracker.disabled());
  }

  public ZakumAceEngine(MetricsMonitor metrics) {
    this(metrics, AceDiagnosticsTracker.disabled());
  }

  public ZakumAceEngine(MetricsMonitor metrics, AceDiagnosticsTracker diagnostics) {
    this.effects = new ConcurrentHashMap<>();
    this.metrics = metrics;
    this.diagnostics = diagnostics == null ? AceDiagnosticsTracker.disabled() : diagnostics;
    registerDefaults();
  }

  public AceDiagnosticsTracker diagnostics() {
    return diagnostics;
  }

  @Override
  public void executeScript(List<String> script, ActionContext context) {
    if (script == null || script.isEmpty() || context == null || context.actor() == null) return;
    long startedAtNanos = System.nanoTime();
    int resolvedEffects = 0;
    diagnostics.recordScript();

    try {
      for (String raw : script) {
        if (raw == null) continue;
        String line = raw.trim();
        if (line.isEmpty() || line.startsWith("#")) continue;
        diagnostics.recordLine();

        Matcher m = ACE_PATTERN.matcher(line);
        if (!m.matches()) {
          diagnostics.recordParseFailure(line, "Line does not match ACE syntax pattern.");
          if (metrics != null) metrics.recordAction("ace_diag_parse_syntax");
          continue;
        }

        String effectKey = normalize(m.group("effect"));
        String value = trim(m.group("value"));
        String targetKey = trim(m.group("targeter"));
        String inlineParams = trim(m.group("params"));

        EffectAction action = effects.get(effectKey);
        if (action == null) {
          diagnostics.recordUnknownEffect(effectKey, line);
          if (metrics != null) metrics.recordAction("ace_diag_unknown_effect");
          continue;
        }

        if (targetKey != null && !targetKey.isBlank() && !KNOWN_TARGETERS.contains(normalize(targetKey))) {
          diagnostics.recordUnknownTargeter(targetKey, line);
          if (metrics != null) metrics.recordAction("ace_diag_unknown_targeter");
        }

        Map<String, String> params = parseParams(value, inlineParams);
        List<Entity> targets = resolveTargets(context, targetKey, params);
        if (targets.isEmpty()) {
          targets = List.of(context.actor());
        }

        resolvedEffects++;
        diagnostics.recordResolvedEffect();
        try {
          action.apply(context, targets, params);
        } catch (Throwable error) {
          diagnostics.recordExecutionFailure(effectKey, targetKey, line, error);
          if (metrics != null) metrics.recordAction("ace_diag_execution_error");
          // Script execution should be fault-tolerant across individual effect lines.
        }
      }
    } finally {
      if (metrics != null) {
        metrics.recordAceExecution(System.nanoTime() - startedAtNanos, resolvedEffects);
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
      case "RADIUS", "NEARBY" -> {
        int limit = boundedTargetLimit(params);
        yield nearby(actor, params, limit);
      }
      case "ALLIES" -> allies(actor, boundedTargetLimit(params));
      case "FRIENDS" -> friends(actor);
      case "RIVALS" -> rivals(actor);
      default -> List.of(actor);
    };
  }

  private static int boundedTargetLimit(Map<String, String> params) {
    if (params == null || params.isEmpty()) return DEFAULT_BOUNDED_TARGETS;
    String raw = params.get("target_limit");
    if (raw == null || raw.isBlank()) raw = params.get("targetlimit");
    if (raw == null || raw.isBlank()) raw = params.get("max_targets");
    if (raw == null || raw.isBlank()) raw = params.get("maxtargets");
    if (raw == null || raw.isBlank()) raw = params.get("limit");
    int requested = parseInt(raw, DEFAULT_BOUNDED_TARGETS);
    return Math.max(1, Math.min(MAX_BOUNDED_TARGETS, requested));
  }

  private static List<Entity> applyTargetLimit(List<Entity> targets, int limit) {
    if (targets == null || targets.isEmpty()) return List.of();
    int bounded = Math.max(1, Math.min(MAX_BOUNDED_TARGETS, limit));
    if (targets.size() <= bounded) return targets;
    return List.copyOf(targets.subList(0, bounded));
  }

  private static List<Entity> nearby(Player actor, Map<String, String> params, int limit) {
    int boundedLimit = Math.max(1, Math.min(MAX_BOUNDED_TARGETS, limit));
    String value = params.get("radius");
    if (value == null || value.isBlank()) value = params.get("value");
    double radius = parseDouble(value, DEFAULT_RADIUS);
    double r = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));

    List<Entity> out = new ArrayList<>();
    out.add(actor);
    if (out.size() >= boundedLimit) return out;
    for (Entity e : actor.getNearbyEntities(r, r, r)) {
      out.add(e);
      if (out.size() >= boundedLimit) break;
    }
    return out;
  }

  private static List<Entity> allies(Player actor, int limit) {
    int boundedLimit = Math.max(1, Math.min(MAX_BOUNDED_TARGETS, limit));
    List<Entity> fromSocial = selectBySocial(actor, SocialRelation.ALLY, boundedLimit);
    if (!fromSocial.isEmpty()) return fromSocial;

    var board = actor.getScoreboard();
    if (board == null) return List.of(actor);
    var team = board.getEntryTeam(actor.getName());
    if (team == null) return List.of(actor);

    List<Entity> out = new ArrayList<>();
    out.add(actor);
    if (out.size() >= boundedLimit) return out;
    for (Player p : actor.getWorld().getPlayers()) {
      if (p.equals(actor)) continue;
      var other = p.getScoreboard().getEntryTeam(p.getName());
      if (other != null && other.getName().equals(team.getName())) {
        out.add(p);
        if (out.size() >= boundedLimit) break;
      }
    }
    return out;
  }

  private static List<Entity> friends(Player actor) {
    List<Entity> fromSocial = selectBySocial(actor, SocialRelation.FRIEND, MAX_BOUNDED_TARGETS);
    return fromSocial.isEmpty() ? List.of(actor) : fromSocial;
  }

  private static List<Entity> rivals(Player actor) {
    List<Entity> fromSocial = selectBySocial(actor, SocialRelation.RIVAL, MAX_BOUNDED_TARGETS);
    return fromSocial.isEmpty() ? List.of(actor) : fromSocial;
  }

  private static List<Entity> selectBySocial(Player actor, SocialRelation relation, int limit) {
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
    if (out.size() >= limit) return out;
    for (Player player : actor.getWorld().getPlayers()) {
      UUID id = player.getUniqueId();
      if (!id.equals(actor.getUniqueId()) && ids.contains(id)) {
        out.add(player);
        if (out.size() >= limit) break;
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

  private static int parseInt(String value, int fallback) {
    if (value == null || value.isBlank()) return fallback;
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }
}
