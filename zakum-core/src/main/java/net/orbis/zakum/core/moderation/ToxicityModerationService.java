package net.orbis.zakum.core.moderation;

import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.api.action.AceEngine;
import net.orbis.zakum.api.config.ZakumSettings;
import net.orbis.zakum.core.metrics.MetricsMonitor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Lightweight async toxicity scoring + response hooks.
 */
public final class ToxicityModerationService {

  private final boolean enabled;
  private final double threshold;
  private final boolean cancelMessage;
  private final String notifyPermission;
  private final Set<String> lexicon;
  private final java.util.List<String> aceScript;
  private final Logger logger;
  private final MetricsMonitor metrics;

  public ToxicityModerationService(
    ZakumSettings.Moderation.Toxicity cfg,
    Logger logger,
    MetricsMonitor metrics
  ) {
    this.enabled = cfg != null && cfg.enabled();
    this.threshold = cfg == null ? 0.8d : cfg.threshold();
    this.cancelMessage = cfg != null && cfg.cancelMessage();
    this.notifyPermission = cfg == null ? "zakum.moderation.alerts" : cfg.notifyPermission();
    this.lexicon = cfg == null ? Set.of() : cfg.lexicon();
    this.aceScript = cfg == null ? java.util.List.of() : cfg.aceScript();
    this.logger = logger;
    this.metrics = metrics;
  }

  public Decision evaluate(String message) {
    if (!enabled) return Decision.DISABLED;
    if (message == null || message.isBlank()) return Decision.SAFE;

    String normalized = normalize(message);
    if (normalized.isBlank()) return Decision.SAFE;

    String[] tokens = normalized.split("\\s+");
    int toxicCount = 0;
    for (String token : tokens) {
      if (lexicon.contains(token)) toxicCount++;
    }
    if (toxicCount <= 0) return Decision.SAFE;

    double ratio = (double) toxicCount / Math.max(1, tokens.length);
    double weighted = Math.min(1.0d, ratio * 2.0d + (toxicCount >= 3 ? 0.15d : 0.0d));
    boolean flagged = weighted >= threshold;
    return new Decision(weighted, flagged, flagged && cancelMessage);
  }

  public void onFlag(ZakumApi api, Player actor, String plainMessage, Decision decision) {
    if (!enabled || api == null || actor == null || decision == null || !decision.flagged()) return;

    Map<String, Object> metadata = new HashMap<>();
    metadata.put("toxicity_score", String.format(Locale.ROOT, "%.3f", decision.score()));
    metadata.put("toxicity_threshold", String.format(Locale.ROOT, "%.3f", threshold));
    metadata.put("toxicity_message", safeExcerpt(plainMessage));

    if (!aceScript.isEmpty()) {
      api.getScheduler().runAtEntity(actor, () -> {
        if (!actor.isOnline()) return;
        api.getAceEngine().executeScript(aceScript, new AceEngine.ActionContext(actor, Optional.empty(), metadata));
      });
    }

    String alert = "<red>[Moderation]</red> <gray>" + actor.getName() + "</gray> "
      + "<yellow>score=" + String.format(Locale.ROOT, "%.2f", decision.score()) + "</yellow> "
      + "<gray>msg:</gray> <white>" + safeExcerpt(plainMessage) + "</white>";

    api.getScheduler().runGlobal(() -> {
      for (Player online : Bukkit.getOnlinePlayers()) {
        if (!online.hasPermission(notifyPermission)) continue;
        online.sendRichMessage(alert);
      }
    });

    if (metrics != null) metrics.recordAction("toxicity_flag");
    if (logger != null) {
      logger.fine("Moderation flagged " + actor.getUniqueId() + " score=" + String.format(Locale.ROOT, "%.3f", decision.score()));
    }
  }

  private static String normalize(String input) {
    return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]+", " ").replaceAll("\\s+", " ").trim();
  }

  private static String safeExcerpt(String input) {
    if (input == null) return "";
    String clean = input.replace('\n', ' ').replace('\r', ' ').trim();
    if (clean.length() <= 160) return clean;
    return clean.substring(0, 157) + "...";
  }

  public record Decision(double score, boolean flagged, boolean cancel) {
    public static final Decision DISABLED = new Decision(0.0d, false, false);
    public static final Decision SAFE = new Decision(0.0d, false, false);
  }
}
