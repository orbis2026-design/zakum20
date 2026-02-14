package net.orbis.zakum.api.chat;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Localized, pre-serialized chat payload buffer.
 *
 * Implementations should cache parsed Adventure components and packet-ready
 * serialized payload bytes to minimize chat-path CPU overhead.
 */
public interface ChatPacketBuffer {

  void registerTemplate(String key, String locale, String miniMessageTemplate);

  default void registerTemplate(String key, Map<String, String> localizedTemplates) {
    if (localizedTemplates == null) return;
    for (Map.Entry<String, String> entry : localizedTemplates.entrySet()) {
      registerTemplate(key, entry.getKey(), entry.getValue());
    }
  }

  void warmup();

  PreparedMessage resolve(String key, String locale, Map<String, String> placeholders);

  default PreparedMessage resolve(String key, String locale) {
    return resolve(key, locale, Map.of());
  }

  default void send(Player player, String key) {
    send(player, key, Map.of());
  }

  void send(Player player, String key, Map<String, String> placeholders);

  /**
   * Sends a localized message using an explicit locale override.
   * Default implementation falls back to Adventure delivery.
   */
  default void send(Player player, String key, String locale, Map<String, String> placeholders) {
    if (player == null || !player.isOnline()) return;
    if (key == null || key.isBlank()) return;
    String resolvedLocale = (locale == null || locale.isBlank()) ? localeOf(player) : locale;
    PreparedMessage message = resolve(key, resolvedLocale, placeholders == null ? Map.of() : placeholders);
    if (message == null || message == PreparedMessage.EMPTY) return;
    player.sendMessage(message.component());
  }

  default void send(Player player, String key, String locale) {
    send(player, key, locale, Map.of());
  }

  /**
   * Sends a localized ActionBar message using cached buffers when possible.
   * Default implementation falls back to Adventure delivery.
   */
  default void sendActionBar(Player player, String key, Map<String, String> placeholders) {
    if (player == null || !player.isOnline()) return;
    if (key == null || key.isBlank()) return;
    PreparedMessage message = resolve(key, localeOf(player), placeholders == null ? Map.of() : placeholders);
    if (message == null || message == PreparedMessage.EMPTY) return;
    player.sendActionBar(message.component());
  }

  default void sendActionBar(Player player, String key) {
    sendActionBar(player, key, Map.of());
  }

  default void sendActionBar(Player player, String key, String locale, Map<String, String> placeholders) {
    if (player == null || !player.isOnline()) return;
    if (key == null || key.isBlank()) return;
    String resolvedLocale = (locale == null || locale.isBlank()) ? localeOf(player) : locale;
    PreparedMessage message = resolve(key, resolvedLocale, placeholders == null ? Map.of() : placeholders);
    if (message == null || message == PreparedMessage.EMPTY) return;
    player.sendActionBar(message.component());
  }

  default void sendActionBar(Player player, String key, String locale) {
    sendActionBar(player, key, locale, Map.of());
  }

  record PreparedMessage(Component component, byte[] serializedJson) {
    public static final PreparedMessage EMPTY = new PreparedMessage(Component.empty(), new byte[0]);
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
}
