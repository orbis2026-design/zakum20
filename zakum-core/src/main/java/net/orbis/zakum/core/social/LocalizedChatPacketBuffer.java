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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
  private final Logger logger;
  private final String defaultLocale;
  private final Cache<String, PreparedMessage> preparedCache;
  private final Map<String, Map<String, String>> templates;

  public LocalizedChatPacketBuffer(
    AssetManager assets,
    ChatBufferCache parseCache,
    ZakumSettings.Chat.Localization localization,
    Logger logger
  ) {
    this.assets = assets;
    this.parseCache = parseCache;
    this.logger = logger;
    this.defaultLocale = normalizeLocale(localization == null ? "en_us" : localization.defaultLocale());
    long preparedMax = localization == null ? 100_000L : localization.preparedMaximumSize();
    this.preparedCache = Caffeine.newBuilder()
      .maximumSize(Math.max(1_000L, preparedMax))
      .build();
    this.templates = new ConcurrentHashMap<>();

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
    for (Map.Entry<String, Map<String, String>> entry : templates.entrySet()) {
      String key = entry.getKey();
      for (String locale : entry.getValue().keySet()) {
        PreparedMessage message = resolve(key, locale, Map.of());
        if (message != PreparedMessage.EMPTY) prepared++;
      }
    }
    logger.fine("Localized chat buffer warmed entries=" + prepared);
  }

  @Override
  public PreparedMessage resolve(String key, String locale, Map<String, String> placeholders) {
    String template = findTemplate(key, locale);
    if (template == null || template.isBlank()) return PreparedMessage.EMPTY;

    String rendered = applyPlaceholders(template, placeholders);
    String resolved = themed(assets.resolve(rendered));
    if (resolved == null || resolved.isBlank()) return PreparedMessage.EMPTY;

    String cacheKey = resolveCacheKey(locale, resolved);
    return preparedCache.get(cacheKey, ignored -> prepare(resolved));
  }

  @Override
  public void send(Player player, String key, Map<String, String> placeholders) {
    if (player == null || !player.isOnline()) return;
    String locale = normalizeLocale(player.getLocale());
    PreparedMessage message = resolve(key, locale, placeholders);
    if (message == PreparedMessage.EMPTY) return;
    player.sendMessage(message.component());
  }

  private PreparedMessage prepare(String line) {
    if (line == null || line.isBlank()) return PreparedMessage.EMPTY;
    Component component = parseCache.parse(line);
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
    String normalizedLocale = normalizeLocale(locale);
    return normalizedLocale + '\u0000' + content;
  }
}
