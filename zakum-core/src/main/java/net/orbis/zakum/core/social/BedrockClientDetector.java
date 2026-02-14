package net.orbis.zakum.core.social;

import net.orbis.zakum.api.config.ZakumSettings;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Reflection bridge for Floodgate player detection.
 */
public final class BedrockClientDetector {

  private final boolean enabled;
  private final Object apiInstance;
  private final Method isFloodgatePlayer;

  public BedrockClientDetector(ZakumSettings.Chat.Bedrock config, Logger logger) {
    this.enabled = config != null && config.enabled();

    Object api = null;
    Method detector = null;
    if (enabled) {
      try {
        Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
        Method getInstance = apiClass.getMethod("getInstance");
        api = getInstance.invoke(null);
        detector = apiClass.getMethod("isFloodgatePlayer", UUID.class);
      } catch (Throwable ignored) {
        if (logger != null) {
          logger.fine("Floodgate not detected; bedrock remapping disabled at runtime.");
        }
      }
    }
    this.apiInstance = api;
    this.isFloodgatePlayer = detector;
  }

  public boolean isBedrock(Player player) {
    if (!enabled || player == null || apiInstance == null || isFloodgatePlayer == null) return false;
    try {
      Object result = isFloodgatePlayer.invoke(apiInstance, player.getUniqueId());
      return result instanceof Boolean b && b;
    } catch (Throwable ignored) {
      return false;
    }
  }
}
