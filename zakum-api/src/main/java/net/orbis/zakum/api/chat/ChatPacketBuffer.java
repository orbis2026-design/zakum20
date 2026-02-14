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

  record PreparedMessage(Component component, byte[] serializedJson) {
    public static final PreparedMessage EMPTY = new PreparedMessage(Component.empty(), new byte[0]);
  }
}
