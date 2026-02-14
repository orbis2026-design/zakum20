package net.orbis.zakum.core.perf;

import net.orbis.zakum.api.concurrent.ZakumScheduler;
import net.orbis.zakum.api.storage.DataStore;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Hugster Performance Blueprint:
 * per-player runtime visual mode toggle for animation fidelity.
 */
public final class PlayerVisualModeService {

  private static final String SESSION_KEY = "visual_mode";

  private final ZakumScheduler scheduler;
  private final Logger logger;
  private final Map<UUID, Mode> modes;
  private volatile DataStore dataStore;

  public PlayerVisualModeService(ZakumScheduler scheduler, Logger logger) {
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    this.logger = logger;
    this.modes = new ConcurrentHashMap<>();
  }

  public void bindDataStore(DataStore dataStore) {
    this.dataStore = dataStore;
  }

  public Mode mode(UUID playerId) {
    if (playerId == null) return Mode.AUTO;
    return modes.getOrDefault(playerId, Mode.AUTO);
  }

  public Mode mode(Player player) {
    if (player == null) return Mode.AUTO;
    return mode(player.getUniqueId());
  }

  public void setMode(UUID playerId, Mode mode) {
    if (playerId == null || mode == null) return;

    if (mode == Mode.AUTO) {
      modes.remove(playerId);
    } else {
      modes.put(playerId, mode);
    }
    persist(playerId, mode);
  }

  public void load(Player player) {
    if (player == null) return;
    DataStore current = this.dataStore;
    if (current == null) return;

    UUID playerId = player.getUniqueId();
    scheduler.runAsync(() -> {
      try {
        Mode mode = Mode.fromStorage(current.getSessionData(playerId, SESSION_KEY));
        if (mode == Mode.AUTO) {
          modes.remove(playerId);
        } else {
          modes.put(playerId, mode);
        }
      } catch (Throwable ex) {
        if (logger != null) {
          logger.warning("Failed to load visual mode for " + playerId + ": " + ex.getMessage());
        }
      }
    });
  }

  public void clear(UUID playerId) {
    if (playerId == null) return;
    modes.remove(playerId);
  }

  private void persist(UUID playerId, Mode mode) {
    DataStore current = this.dataStore;
    if (current == null) return;
    scheduler.runAsync(() -> {
      try {
        current.setSessionData(playerId, SESSION_KEY, mode.toStorage());
      } catch (Throwable ex) {
        if (logger != null) {
          logger.warning("Failed to persist visual mode for " + playerId + ": " + ex.getMessage());
        }
      }
    });
  }

  public enum Mode {
    AUTO,
    PERFORMANCE,
    QUALITY;

    public static Mode fromInput(String raw) {
      if (raw == null || raw.isBlank()) return AUTO;
      String value = raw.trim().toLowerCase(Locale.ROOT);
      return switch (value) {
        case "on", "performance", "perf", "low" -> PERFORMANCE;
        case "off", "quality", "high", "full" -> QUALITY;
        case "auto", "default" -> AUTO;
        default -> AUTO;
      };
    }

    public static Mode fromStorage(String raw) {
      if (raw == null || raw.isBlank()) return AUTO;
      try {
        return Mode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ignored) {
        return AUTO;
      }
    }

    public String toStorage() {
      if (this == AUTO) return null;
      return name().toLowerCase(Locale.ROOT);
    }

    public String displayName() {
      return name().toLowerCase(Locale.ROOT);
    }
  }
}

