package net.orbis.zakum.core.action;

import net.orbis.zakum.api.config.ZakumSettings;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * Tracks ACE parser/execution diagnostics using a bounded recent-issues buffer.
 */
public final class AceDiagnosticsTracker {

  private static final AceDiagnosticsTracker DISABLED = new AceDiagnosticsTracker(
    new ZakumSettings.Operations.AceDiagnostics(false, 1, 80)
  );

  private final boolean configuredEnabled;
  private final int maxRecentEntries;
  private final int maxLineLength;
  private final AtomicBoolean runtimeEnabled = new AtomicBoolean(true);

  private final LongAdder scripts = new LongAdder();
  private final LongAdder lines = new LongAdder();
  private final LongAdder resolvedEffects = new LongAdder();
  private final LongAdder parseFailures = new LongAdder();
  private final LongAdder unknownEffects = new LongAdder();
  private final LongAdder unknownTargeters = new LongAdder();
  private final LongAdder executionFailures = new LongAdder();
  private final EnumMap<Code, LongAdder> byCode = new EnumMap<>(Code.class);
  private final ArrayDeque<Entry> recent = new ArrayDeque<>();
  private final Object lock = new Object();

  public AceDiagnosticsTracker(ZakumSettings.Operations.AceDiagnostics cfg) {
    this.configuredEnabled = cfg != null && cfg.enabled();
    this.maxRecentEntries = cfg == null ? 200 : Math.max(1, cfg.maxRecentEntries());
    this.maxLineLength = cfg == null ? 200 : Math.max(40, cfg.maxLineLength());
    for (Code code : Code.values()) {
      byCode.put(code, new LongAdder());
    }
  }

  public static AceDiagnosticsTracker disabled() {
    return DISABLED;
  }

  public boolean configuredEnabled() {
    return configuredEnabled;
  }

  public boolean runtimeEnabled() {
    return runtimeEnabled.get();
  }

  public boolean enabled() {
    return configuredEnabled && runtimeEnabled.get();
  }

  public void setRuntimeEnabled(boolean enabled) {
    runtimeEnabled.set(enabled);
  }

  public void recordScript() {
    if (!enabled()) return;
    scripts.increment();
  }

  public void recordLine() {
    if (!enabled()) return;
    lines.increment();
  }

  public void recordResolvedEffect() {
    if (!enabled()) return;
    resolvedEffects.increment();
  }

  public void recordParseFailure(String rawLine, String detail) {
    if (!enabled()) return;
    parseFailures.increment();
    increment(Code.PARSE_SYNTAX);
    appendRecent(Severity.WARNING, Code.PARSE_SYNTAX, "", "", detail, rawLine, "");
  }

  public void recordUnknownEffect(String effect, String rawLine) {
    if (!enabled()) return;
    unknownEffects.increment();
    increment(Code.UNKNOWN_EFFECT);
    appendRecent(
      Severity.WARNING,
      Code.UNKNOWN_EFFECT,
      normalize(effect),
      "",
      "Effect is not registered.",
      rawLine,
      ""
    );
  }

  public void recordUnknownTargeter(String targeter, String rawLine) {
    if (!enabled()) return;
    unknownTargeters.increment();
    increment(Code.UNKNOWN_TARGETER);
    appendRecent(
      Severity.WARNING,
      Code.UNKNOWN_TARGETER,
      "",
      normalize(targeter),
      "Targeter is unknown; fallback target resolution used.",
      rawLine,
      ""
    );
  }

  public void recordExecutionFailure(String effect, String targeter, String rawLine, Throwable error) {
    if (!enabled()) return;
    executionFailures.increment();
    increment(Code.EFFECT_EXECUTION);
    String message = error == null ? "unknown" : Objects.toString(error.getMessage(), error.getClass().getSimpleName());
    appendRecent(
      Severity.CRITICAL,
      Code.EFFECT_EXECUTION,
      normalize(effect),
      normalize(targeter),
      "Effect execution failed: " + sanitizeMessage(message),
      rawLine,
      error == null ? "" : error.getClass().getSimpleName()
    );
  }

  public void clear() {
    scripts.reset();
    lines.reset();
    resolvedEffects.reset();
    parseFailures.reset();
    unknownEffects.reset();
    unknownTargeters.reset();
    executionFailures.reset();
    for (LongAdder adder : byCode.values()) {
      adder.reset();
    }
    synchronized (lock) {
      recent.clear();
    }
  }

  public Snapshot snapshot() {
    LinkedHashMap<String, Long> byCodeSnapshot = new LinkedHashMap<>();
    for (Code code : Code.values()) {
      byCodeSnapshot.put(code.name(), byCode.get(code).sum());
    }
    List<Entry> recentSnapshot;
    synchronized (lock) {
      recentSnapshot = List.copyOf(new ArrayList<>(recent));
    }
    return new Snapshot(
      configuredEnabled,
      runtimeEnabled(),
      enabled(),
      maxRecentEntries,
      maxLineLength,
      scripts.sum(),
      lines.sum(),
      resolvedEffects.sum(),
      parseFailures.sum(),
      unknownEffects.sum(),
      unknownTargeters.sum(),
      executionFailures.sum(),
      Map.copyOf(byCodeSnapshot),
      recentSnapshot
    );
  }

  private void increment(Code code) {
    LongAdder adder = byCode.get(code);
    if (adder != null) adder.increment();
  }

  private void appendRecent(
    Severity severity,
    Code code,
    String effect,
    String targeter,
    String message,
    String rawLine,
    String errorClass
  ) {
    Entry entry = new Entry(
      System.currentTimeMillis(),
      severity == null ? Severity.WARNING : severity,
      code == null ? Code.PARSE_SYNTAX : code,
      normalize(effect),
      normalize(targeter),
      sanitizeMessage(message),
      sanitizeLine(rawLine),
      sanitizeMessage(errorClass)
    );
    synchronized (lock) {
      recent.addFirst(entry);
      while (recent.size() > maxRecentEntries) {
        recent.removeLast();
      }
    }
  }

  private String sanitizeLine(String raw) {
    if (raw == null || raw.isBlank()) return "";
    String clean = raw.trim();
    if (clean.length() <= maxLineLength) return clean;
    return clean.substring(0, maxLineLength) + "...";
  }

  private static String sanitizeMessage(String raw) {
    if (raw == null || raw.isBlank()) return "";
    String clean = raw.trim().replace('\n', ' ').replace('\r', ' ');
    if (clean.length() <= 300) return clean;
    return clean.substring(0, 300) + "...";
  }

  private static String normalize(String raw) {
    if (raw == null || raw.isBlank()) return "";
    return raw.trim().toUpperCase(Locale.ROOT);
  }

  public enum Severity {
    WARNING,
    CRITICAL
  }

  public enum Code {
    PARSE_SYNTAX,
    UNKNOWN_EFFECT,
    UNKNOWN_TARGETER,
    EFFECT_EXECUTION
  }

  public record Entry(
    long timestampMs,
    Severity severity,
    Code code,
    String effect,
    String targeter,
    String message,
    String line,
    String errorClass
  ) {}

  public record Snapshot(
    boolean configuredEnabled,
    boolean runtimeEnabled,
    boolean enabled,
    int maxRecentEntries,
    int maxLineLength,
    long scripts,
    long lines,
    long resolvedEffects,
    long parseFailures,
    long unknownEffects,
    long unknownTargeters,
    long executionFailures,
    Map<String, Long> byCode,
    List<Entry> recent
  ) {
    public Snapshot {
      byCode = byCode == null ? Map.of() : Map.copyOf(byCode);
      recent = recent == null ? List.of() : List.copyOf(recent);
    }
  }
}
