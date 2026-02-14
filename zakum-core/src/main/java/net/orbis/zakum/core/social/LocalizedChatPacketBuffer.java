package net.orbis.zakum.core.social;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.orbis.zakum.api.asset.AssetManager;
import net.orbis.zakum.api.chat.ChatPacketBuffer;
import net.orbis.zakum.api.config.ZakumSettings;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

/**
 * Hugster-style localized, pre-serialized message buffer.
 */
public final class LocalizedChatPacketBuffer implements ChatPacketBuffer {

  private static final String PRIMARY = "#44FFCC";
  private static final String SECONDARY = "#33AAFF";
  private static final GsonComponentSerializer GSON = GsonComponentSerializer.gson();

  private final AssetManager assets;
  private final ChatBufferCache parseCache;
  private final BedrockClientDetector bedrockDetector;
  private final BedrockGlyphRemapper bedrockRemapper;
  private final Logger logger;
  private final String defaultLocale;
  private final Set<String> supportedLocales;
  private final PacketChatTransport packetTransport;
  private final Cache<String, PreparedMessage> preparedCache;
  private final Map<String, Map<String, String>> templates;
  private final LongAdder resolveRequests;
  private final LongAdder resolveHits;
  private final LongAdder resolveMisses;
  private final LongAdder sendCount;

  public LocalizedChatPacketBuffer(
    AssetManager assets,
    ChatBufferCache parseCache,
    ZakumSettings.Chat.Localization localization,
    BedrockClientDetector bedrockDetector,
    BedrockGlyphRemapper bedrockRemapper,
    Logger logger
  ) {
    this.assets = assets;
    this.parseCache = parseCache;
    this.bedrockDetector = bedrockDetector;
    this.bedrockRemapper = bedrockRemapper;
    this.logger = logger;
    this.defaultLocale = normalizeLocale(localization == null ? "en_us" : localization.defaultLocale());
    Set<String> configuredLocales = localization == null ? Set.of() : localization.supportedLocales();
    if (configuredLocales == null || configuredLocales.isEmpty()) {
      this.supportedLocales = Set.of(defaultLocale);
    } else {
      java.util.LinkedHashSet<String> normalized = new java.util.LinkedHashSet<>();
      for (String locale : configuredLocales) {
        String value = normalizeLocale(locale);
        if (!value.isBlank()) normalized.add(value);
      }
      if (normalized.isEmpty()) normalized.add(defaultLocale);
      this.supportedLocales = Set.copyOf(normalized);
    }
    boolean packetDispatchEnabled = localization != null && localization.packetDispatchEnabled();
    this.packetTransport = new PacketChatTransport(packetDispatchEnabled, logger);
    long preparedMax = localization == null ? 100_000L : localization.preparedMaximumSize();
    this.preparedCache = Caffeine.newBuilder()
      .maximumSize(Math.max(1_000L, preparedMax))
      .build();
    this.templates = new ConcurrentHashMap<>();
    this.resolveRequests = new LongAdder();
    this.resolveHits = new LongAdder();
    this.resolveMisses = new LongAdder();
    this.sendCount = new LongAdder();

    if (localization != null && localization.templates() != null) {
      for (Map.Entry<String, Map<String, String>> entry : localization.templates().entrySet()) {
        registerTemplate(entry.getKey(), entry.getValue());
      }
    }
  }

  @Override
  public void registerTemplate(String key, String locale, String miniMessageTemplate) {
    if (key == null || key.isBlank()) return;
    if (locale == null || locale.isBlank()) return;
    if (miniMessageTemplate == null || miniMessageTemplate.isBlank()) return;

    String normalizedKey = key.trim();
    String normalizedLocale = normalizeLocale(locale);
    templates.compute(normalizedKey, (k, existing) -> {
      Map<String, String> next = existing == null ? new LinkedHashMap<>() : new LinkedHashMap<>(existing);
      next.put(normalizedLocale, miniMessageTemplate);
      return Map.copyOf(next);
    });
    preparedCache.invalidateAll();
  }

  @Override
  public void registerTemplate(String key, Map<String, String> localizedTemplates) {
    if (localizedTemplates == null || localizedTemplates.isEmpty()) return;
    for (Map.Entry<String, String> entry : localizedTemplates.entrySet()) {
      registerTemplate(key, entry.getKey(), entry.getValue());
    }
  }

  @Override
  public void warmup() {
    int prepared = 0;
    boolean warmBedrock = bedrockRemapper != null;
    for (String key : templates.keySet()) {
      for (String locale : supportedLocales) {
        PreparedMessage message = resolve(key, normalizeLocale(locale), Map.of(), false);
        if (message != PreparedMessage.EMPTY) prepared++;
        if (warmBedrock) {
          PreparedMessage bedrock = resolve(key, normalizeLocale(locale), Map.of(), true);
          if (bedrock != PreparedMessage.EMPTY) prepared++;
        }
      }
    }
    logger.fine("Localized chat buffer warmed entries=" + prepared);
  }

  @Override
  public PreparedMessage resolve(String key, String locale, Map<String, String> placeholders) {
    return resolve(key, locale, placeholders, false);
  }

  private PreparedMessage resolve(String key, String locale, Map<String, String> placeholders, boolean bedrock) {
    resolveRequests.increment();
    String template = findTemplate(key, locale);
    if (template == null || template.isBlank()) return PreparedMessage.EMPTY;

    String rendered = applyPlaceholders(template, placeholders);
    String resolved = themed(assets.resolve(rendered));
    if (resolved == null || resolved.isBlank()) return PreparedMessage.EMPTY;

    String cacheKey = resolveCacheKey(locale, resolved, bedrock);
    PreparedMessage cached = preparedCache.getIfPresent(cacheKey);
    if (cached != null) {
      resolveHits.increment();
      return cached;
    }

    resolveMisses.increment();
    PreparedMessage prepared = prepare(resolved, bedrock);
    preparedCache.put(cacheKey, prepared);
    return prepared;
  }

  @Override
  public void send(Player player, String key, Map<String, String> placeholders) {
    sendInternal(player, key, null, placeholders, false);
  }

  @Override
  public void send(Player player, String key, String locale, Map<String, String> placeholders) {
    sendInternal(player, key, locale, placeholders, false);
  }

  @Override
  public void sendActionBar(Player player, String key, Map<String, String> placeholders) {
    sendInternal(player, key, null, placeholders, true);
  }

  @Override
  public void sendActionBar(Player player, String key, String locale, Map<String, String> placeholders) {
    sendInternal(player, key, locale, placeholders, true);
  }

  public Stats stats() {
    return new Stats(
      templates.size(),
      packetTransport.enabled(),
      packetTransport.available(),
      preparedCache.estimatedSize(),
      resolveRequests.sum(),
      resolveHits.sum(),
      resolveMisses.sum(),
      sendCount.sum(),
      packetTransport.packetSends(),
      packetTransport.fallbackSends(),
      packetTransport.sendFailures()
    );
  }

  private void sendInternal(
    Player player,
    String key,
    String locale,
    Map<String, String> placeholders,
    boolean overlay
  ) {
    if (player == null || !player.isOnline()) return;
    String resolvedLocale = (locale == null || locale.isBlank()) ? localeOf(player) : locale;
    boolean bedrock = isBedrock(player);
    PreparedMessage message = resolve(key, resolvedLocale, placeholders, bedrock);
    if (message == PreparedMessage.EMPTY) return;

    sendCount.increment();
    if (overlay) {
      if (packetTransport.sendSystem(player, message.serializedJson(), true)) return;
    } else {
      if (packetTransport.sendSystem(player, message.serializedJson())) return;
    }
    packetTransport.recordFallback();
    if (overlay) {
      player.sendActionBar(message.component());
    } else {
      player.sendMessage(message.component());
    }
  }

  private PreparedMessage prepare(String line, boolean bedrock) {
    if (line == null || line.isBlank()) return PreparedMessage.EMPTY;
    Component component = parseCache.parse(line);
    if (bedrock && bedrockRemapper != null) {
      component = bedrockRemapper.remap(component);
    }
    String json = GSON.serialize(component);
    byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
    return new PreparedMessage(component, bytes);
  }

  private String findTemplate(String key, String locale) {
    if (key == null || key.isBlank()) return null;
    Map<String, String> localized = templates.get(key.trim());
    if (localized == null || localized.isEmpty()) return null;

    String normalizedLocale = normalizeLocale(locale);
    String direct = localized.get(normalizedLocale);
    if (direct != null && !direct.isBlank()) return direct;

    String fallback = localized.get(defaultLocale);
    if (fallback != null && !fallback.isBlank()) return fallback;

    for (String value : localized.values()) {
      if (value != null && !value.isBlank()) return value;
    }
    return null;
  }

  private static String applyPlaceholders(String input, Map<String, String> placeholders) {
    if (input == null || input.isBlank()) return input;
    if (placeholders == null || placeholders.isEmpty()) return input;

    String out = input;
    for (Map.Entry<String, String> entry : placeholders.entrySet()) {
      String key = entry.getKey();
      if (key == null || key.isBlank()) continue;
      String value = entry.getValue() == null ? "" : entry.getValue();
      out = out.replace("%" + key + "%", value);
    }
    return out;
  }

  private static String themed(String input) {
    if (input == null) return null;
    return input
      .replace("<primary>", PRIMARY)
      .replace("<secondary>", SECONDARY);
  }

  private static String normalizeLocale(String locale) {
    if (locale == null || locale.isBlank()) return "en_us";
    return locale.trim().toLowerCase(Locale.ROOT).replace('-', '_');
  }

  private static String resolveCacheKey(String locale, String content) {
    return resolveCacheKey(locale, content, false);
  }

  private static String resolveCacheKey(String locale, String content, boolean bedrock) {
    String normalizedLocale = normalizeLocale(locale);
    String prefix = bedrock ? "bedrock" : "java";
    return prefix + '\u0000' + normalizedLocale + '\u0000' + content;
  }

  private boolean isBedrock(Player player) {
    return bedrockDetector != null && bedrockDetector.isBedrock(player);
  }

  private static String localeOf(Player player) {
    if (player == null) return "en_us";
    try {
      java.util.Locale locale = player.locale();
      if (locale != null) return locale.toString();
    } catch (Throwable ignored) {
      // Fall through to default.
    }
    return "en_us";
  }

  public record Stats(
    int templateKeys,
    boolean packetDispatchEnabled,
    boolean packetDispatchAvailable,
    long preparedCacheSize,
    long resolveRequests,
    long resolveHits,
    long resolveMisses,
    long sends,
    long packetSends,
    long fallbackSends,
    long packetFailures
  ) {}
}
