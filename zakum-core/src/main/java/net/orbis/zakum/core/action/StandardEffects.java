package net.orbis.zakum.core.action;

import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.api.action.AceEngine;
import net.orbis.zakum.api.capability.ZakumCapabilities;
import net.orbis.zakum.api.chat.ChatPacketBuffer;
import net.orbis.zakum.core.bridge.DecentHologramsBridge;
import net.orbis.zakum.core.perf.VisualCircuitState;
import net.orbis.zakum.core.util.PdcKeys;
import net.orbis.zakum.core.world.ZakumRtpService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Baseline ACE effect library used by battlepass/crates/pets modules.
 */
public final class StandardEffects {

  private static final Map<UUID, BossBar> ACTIVE_BOSS_BARS = new ConcurrentHashMap<>();

  private StandardEffects() {}

  public static void registerDefaults(AceEngine engine) {
    Objects.requireNonNull(engine, "engine");

    registerConsoleCommand(engine);
    registerPlayerCommand(engine);
    registerMessage(engine);
    registerMessageKey(engine);
    registerActionBar(engine);
    registerActionBarKey(engine);
    registerTitle(engine);
    registerSound(engine);
    registerParticle(engine);
    registerGiveMoney(engine);
    registerGiveSouls(engine);
    registerGiveXp(engine);
    registerSetXpLevel(engine);
    registerHeal(engine);
    registerDamage(engine);
    registerFeed(engine);
    registerFireTicks(engine);
    registerPotion(engine);
    registerClearEffects(engine);
    registerTeleport(engine);
    registerVelocity(engine);
    registerGiveItem(engine);
    registerBroadcast(engine);
    registerLightning(engine);
    registerGamemode(engine);
    registerSetModel(engine);
    registerBossBar(engine);
    registerGuiEffects(engine);
    registerRtp(engine);
    registerHologram(engine);
  }

  private static void registerConsoleCommand(AceEngine engine) {
    AceEngine.EffectAction command = (ctx, targets, params) -> {
      String raw = raw(params);
      if (raw == null || raw.isBlank()) return;

      for (Entity target : targets) {
        String prepared = placeholders(raw, ctx, target);
        if (prepared.startsWith("/")) prepared = prepared.substring(1);
        String finalCommand = prepared;
        runGlobal(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand));
      }
    };

    engine.registerEffect("COMMAND", command);
    engine.registerEffect("CONSOLE_COMMAND", command);
  }

  private static void registerPlayerCommand(AceEngine engine) {
    engine.registerEffect("PLAYER_COMMAND", (ctx, targets, params) -> {
      String raw = raw(params);
      if (raw == null || raw.isBlank()) return;

      for (Entity target : targets) {
        if (!(target instanceof Player player)) continue;
        String prepared = placeholders(raw, ctx, target);
        if (prepared.startsWith("/")) prepared = prepared.substring(1);
        String finalCommand = prepared;
        runAtEntity(target, () -> player.performCommand(finalCommand));
      }
    });
  }

  private static void registerMessage(AceEngine engine) {
    engine.registerEffect("MESSAGE", (ctx, targets, params) -> {
      String message = raw(params);
      if (message == null || message.isBlank()) return;

      for (Entity target : targets) {
        if (!(target instanceof CommandSender sender)) continue;
        String finalMessage = placeholders(message, ctx, target);
        runAtEntity(target, () -> sender.sendRichMessage(finalMessage));
      }
    });
  }

  private static void registerMessageKey(AceEngine engine) {
    AceEngine.EffectAction effect = (ctx, targets, params) -> {
      String key = firstNonBlank(params.get("key"), raw(params));
      if (key == null || key.isBlank()) return;

      ZakumApi api = ZakumApi.get();
      ChatPacketBuffer buffer = api.capability(ZakumCapabilities.CHAT_BUFFER).orElse(null);
      if (buffer == null) return;

      String forcedLocale = firstNonBlank(params.get("locale"), params.get("lang"));
      for (Entity target : targets) {
        if (!(target instanceof Player player)) continue;
        Map<String, String> placeholders = templatePlaceholders(ctx, target, params);
        String locale = forcedLocale == null || forcedLocale.isBlank()
          ? player.getLocale()
          : forcedLocale;
        var message = buffer.resolve(key, locale, placeholders);
        if (message == null || message == ChatPacketBuffer.PreparedMessage.EMPTY) continue;
        runAtEntity(target, () -> player.sendMessage(message.component()));
      }
    };

    // Hugster directive: execute pre-serialized localized templates by key.
    engine.registerEffect("MESSAGE_KEY", effect);
    engine.registerEffect("LOCALIZED_MESSAGE", effect);
  }

  private static void registerActionBar(AceEngine engine) {
    engine.registerEffect("ACTION_BAR", (ctx, targets, params) -> {
      String message = raw(params);
      if (message == null || message.isBlank()) return;

      for (Entity target : targets) {
        if (!(target instanceof Player player)) continue;
        String finalMessage = placeholders(message, ctx, target);
        runAtEntity(target, () -> player.sendActionBar(finalMessage));
      }
    });
  }

  private static void registerActionBarKey(AceEngine engine) {
    AceEngine.EffectAction effect = (ctx, targets, params) -> {
      String key = firstNonBlank(params.get("key"), raw(params));
      if (key == null || key.isBlank()) return;

      ZakumApi api = ZakumApi.get();
      ChatPacketBuffer buffer = api.capability(ZakumCapabilities.CHAT_BUFFER).orElse(null);
      if (buffer == null) return;

      String forcedLocale = firstNonBlank(params.get("locale"), params.get("lang"));
      for (Entity target : targets) {
        if (!(target instanceof Player player)) continue;
        Map<String, String> placeholders = templatePlaceholders(ctx, target, params);
        String locale = forcedLocale == null || forcedLocale.isBlank()
          ? player.getLocale()
          : forcedLocale;
        var message = buffer.resolve(key, locale, placeholders);
        if (message == null || message == ChatPacketBuffer.PreparedMessage.EMPTY) continue;
        runAtEntity(target, () -> player.sendActionBar(message.component()));
      }
    };

    engine.registerEffect("ACTION_BAR_KEY", effect);
    engine.registerEffect("LOCALIZED_ACTION_BAR", effect);
  }

  private static void registerTitle(AceEngine engine) {
    engine.registerEffect("TITLE", (ctx, targets, params) -> {
      String raw = raw(params);
      if (raw == null || raw.isBlank()) return;

      String[] parts = raw.split("\\|", 2);
      String title = parts[0].trim();
      String subtitle = parts.length > 1 ? parts[1].trim() : "";

      int fadeIn = intParam(params, "fadein", 10);
      int stay = intParam(params, "stay", 40);
      int fadeOut = intParam(params, "fadeout", 10);

      for (Entity target : targets) {
        if (!(target instanceof Player player)) continue;
        String t = placeholders(title, ctx, target);
        String s = placeholders(subtitle, ctx, target);
        runAtEntity(target, () -> player.sendTitle(t, s, fadeIn, stay, fadeOut));
      }
    });
  }

  private static void registerSound(AceEngine engine) {
    engine.registerEffect("SOUND", (ctx, targets, params) -> {
      String id = firstNonBlank(params.get("sound"), firstToken(raw(params)));
      if (id == null || id.isBlank()) return;

      Sound sound;
      try {
        sound = Sound.valueOf(id.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ex) {
        return;
      }

      float volume = (float) doubleParam(params, "volume", 1.0);
      float pitch = (float) doubleParam(params, "pitch", 1.0);

      for (Entity target : targets) {
        runAtEntity(target, () -> target.getWorld().playSound(target.getLocation(), sound, volume, pitch));
      }
    });
  }

  private static void registerParticle(AceEngine engine) {
    engine.registerEffect("PARTICLE", (ctx, targets, params) -> {
      if (isVisualSuppressed(params)) return;
      String id = firstNonBlank(params.get("particle"), firstToken(raw(params)));
      if (id == null || id.isBlank()) return;

      Particle particle;
      try {
        particle = Particle.valueOf(id.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ex) {
        return;
      }

      int count = intParam(params, "count", 8);
      double dx = doubleParam(params, "dx", 0.15d);
      double dy = doubleParam(params, "dy", 0.15d);
      double dz = doubleParam(params, "dz", 0.15d);
      double speed = doubleParam(params, "speed", 0.01d);

      for (Entity target : targets) {
        runAtEntity(target, () -> target.getWorld().spawnParticle(particle, target.getLocation(), count, dx, dy, dz, speed));
      }
    });
  }

  private static void registerGiveMoney(AceEngine engine) {
    engine.registerEffect("GIVE_MONEY", (ctx, targets, params) -> {
      double amount = doubleParam(params, "amount", parseNumber(raw(params), 0.0d));
      if (amount <= 0.0d) return;

      ZakumApi api = ZakumApi.get();
      api.capability(ZakumCapabilities.ECONOMY).ifPresent(econ -> {
        if (!econ.available()) return;
        for (Entity target : targets) {
          if (target instanceof Player player) {
            econ.deposit(player.getUniqueId(), amount);
          }
        }
      });
    });
  }

  private static void registerGiveSouls(AceEngine engine) {
    engine.registerEffect("GIVE_SOULS", (ctx, targets, params) -> {
      long amount = Math.round(doubleParam(params, "amount", parseNumber(raw(params), 0.0d)));
      if (amount == 0L) return;

      for (Entity target : targets) {
        if (!(target instanceof Player player)) continue;
        runAtEntity(player, () -> {
          var pdc = player.getPersistentDataContainer();
          long current = readLong(pdc.get(PdcKeys.SOULS, PersistentDataType.LONG), 0L);
          long next = Math.max(0L, current + amount);
          pdc.set(PdcKeys.SOULS, PersistentDataType.LONG, next);
        });
      }
    });
  }

  private static void registerGiveXp(AceEngine engine) {
    engine.registerEffect("GIVE_XP", (ctx, targets, params) -> {
      int amount = (int) Math.round(doubleParam(params, "amount", parseNumber(raw(params), 0.0d)));
      if (amount == 0) return;
      for (Entity target : targets) {
        if (target instanceof Player player) {
          runAtEntity(target, () -> player.giveExp(amount));
        }
      }
    });
  }

  private static void registerSetXpLevel(AceEngine engine) {
    engine.registerEffect("SET_XP_LEVEL", (ctx, targets, params) -> {
      int level = intParam(params, "level", (int) Math.round(parseNumber(raw(params), 0.0d)));
      if (level < 0) level = 0;
      int finalLevel = level;
      for (Entity target : targets) {
        if (target instanceof Player player) {
          runAtEntity(target, () -> player.setLevel(finalLevel));
        }
      }
    });
  }

  private static void registerHeal(AceEngine engine) {
    engine.registerEffect("HEAL", (ctx, targets, params) -> {
      double amount = doubleParam(params, "amount", parseNumber(raw(params), 0.0d));
      if (amount <= 0.0d) return;

      for (Entity target : targets) {
        if (!(target instanceof LivingEntity living)) continue;
        runAtEntity(target, () -> {
          double next = Math.min(living.getMaxHealth(), living.getHealth() + amount);
          living.setHealth(next);
        });
      }
    });
  }

  private static void registerDamage(AceEngine engine) {
    engine.registerEffect("DAMAGE", (ctx, targets, params) -> {
      double amount = doubleParam(params, "amount", parseNumber(raw(params), 0.0d));
      if (amount <= 0.0d) return;

      for (Entity target : targets) {
        if (!(target instanceof LivingEntity living)) continue;
        runAtEntity(target, () -> living.damage(amount, ctx.actor()));
      }
    });
  }

  private static void registerFeed(AceEngine engine) {
    engine.registerEffect("FEED", (ctx, targets, params) -> {
      int food = intParam(params, "food", (int) Math.round(parseNumber(raw(params), 20.0d)));
      float saturation = (float) doubleParam(params, "saturation", 5.0d);
      for (Entity target : targets) {
        if (!(target instanceof Player player)) continue;
        runAtEntity(target, () -> {
          player.setFoodLevel(Math.max(0, Math.min(20, food)));
          player.setSaturation(Math.max(0.0f, saturation));
        });
      }
    });
  }

  private static void registerFireTicks(AceEngine engine) {
    engine.registerEffect("FIRE_TICKS", (ctx, targets, params) -> {
      int ticks = intParam(params, "ticks", (int) Math.round(parseNumber(raw(params), 0.0d)));
      if (ticks < 0) ticks = 0;
      int finalTicks = ticks;
      for (Entity target : targets) {
        runAtEntity(target, () -> target.setFireTicks(finalTicks));
      }
    });
  }

  private static void registerPotion(AceEngine engine) {
    engine.registerEffect("POTION", (ctx, targets, params) -> {
      String raw = raw(params);
      if (raw == null || raw.isBlank()) return;

      String[] parts = raw.split(":");
      String typeRaw = parts[0].trim();
      PotionEffectType type = PotionEffectType.getByName(typeRaw.toUpperCase(Locale.ROOT));
      if (type == null) return;

      int seconds = parts.length > 1 ? parseInt(parts[1], 5) : intParam(params, "duration", 5);
      int amplifier = parts.length > 2 ? parseInt(parts[2], 0) : intParam(params, "amplifier", 0);
      boolean ambient = boolParam(params, "ambient", false);
      boolean particles = boolParam(params, "particles", true);

      PotionEffect effect = new PotionEffect(type, Math.max(1, seconds * 20), Math.max(0, amplifier), ambient, particles);
      for (Entity target : targets) {
        if (!(target instanceof LivingEntity living)) continue;
        runAtEntity(target, () -> living.addPotionEffect(effect));
      }
    });
  }

  private static void registerClearEffects(AceEngine engine) {
    engine.registerEffect("CLEAR_EFFECTS", (ctx, targets, params) -> {
      for (Entity target : targets) {
        if (!(target instanceof LivingEntity living)) continue;
        runAtEntity(target, () -> {
          for (PotionEffect effect : living.getActivePotionEffects()) {
            living.removePotionEffect(effect.getType());
          }
        });
      }
    });
  }

  private static void registerTeleport(AceEngine engine) {
    engine.registerEffect("TELEPORT", (ctx, targets, params) -> {
      String raw = raw(params);
      if (raw == null || raw.isBlank()) return;

      Location base = ctx.actor().getLocation();
      Location parsed = parseLocation(raw, params, base);
      if (parsed == null) return;

      for (Entity target : targets) {
        runAtEntity(target, () -> target.teleport(parsed));
      }
    });
  }

  private static void registerVelocity(AceEngine engine) {
    engine.registerEffect("VELOCITY", (ctx, targets, params) -> {
      Vector vector = parseVector(raw(params), params);
      if (vector == null) return;
      for (Entity target : targets) {
        runAtEntity(target, () -> target.setVelocity(vector));
      }
    });
  }

  private static void registerGiveItem(AceEngine engine) {
    engine.registerEffect("GIVE_ITEM", (ctx, targets, params) -> {
      String materialRaw = firstNonBlank(params.get("material"), firstToken(raw(params)));
      if (materialRaw == null || materialRaw.isBlank()) return;

      Material material;
      try {
        material = Material.valueOf(materialRaw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ex) {
        return;
      }

      int amount = intParam(params, "amount", 1);
      ItemStack stack = new ItemStack(material, Math.max(1, Math.min(64, amount)));

      for (Entity target : targets) {
        if (!(target instanceof Player player)) continue;
        runAtEntity(target, () -> {
          var leftover = player.getInventory().addItem(stack.clone());
          for (ItemStack item : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
          }
        });
      }
    });
  }

  private static void registerBroadcast(AceEngine engine) {
    engine.registerEffect("BROADCAST", (ctx, targets, params) -> {
      String raw = raw(params);
      if (raw == null || raw.isBlank()) return;
      String msg = placeholders(raw, ctx, ctx.actor());
      runGlobal(() -> Bukkit.broadcastMessage(msg));
    });
  }

  private static void registerLightning(AceEngine engine) {
    AceEngine.EffectAction lightning = (ctx, targets, params) -> {
      for (Entity target : targets) {
        runAtEntity(target, () -> target.getWorld().strikeLightning(target.getLocation()));
      }
    };
    engine.registerEffect("STRIKE_LIGHTNING", lightning);
    engine.registerEffect("LIGHTNING", lightning);
    engine.registerEffect("LIGHTNING_EFFECT", (ctx, targets, params) -> {
      for (Entity target : targets) {
        runAtEntity(target, () -> target.getWorld().strikeLightningEffect(target.getLocation()));
      }
    });
  }

  private static void registerGamemode(AceEngine engine) {
    engine.registerEffect("SET_GAMEMODE", (ctx, targets, params) -> {
      String value = firstNonBlank(params.get("mode"), raw(params));
      if (value == null || value.isBlank()) return;

      GameMode mode;
      try {
        mode = GameMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ex) {
        return;
      }

      for (Entity target : targets) {
        if (!(target instanceof Player player)) continue;
        runAtEntity(target, () -> player.setGameMode(mode));
      }
    });
  }

  private static void registerSetModel(AceEngine engine) {
    engine.registerEffect("SET_MODEL", (ctx, targets, params) -> {
      String modelId = firstNonBlank(params.get("id"), params.get("model"), raw(params));
      if (modelId == null || modelId.isBlank()) return;
      NamespacedKey key = new NamespacedKey("zakum", "model");

      for (Entity target : targets) {
        runAtEntity(target, () -> target.getPersistentDataContainer().set(key, PersistentDataType.STRING, modelId));
      }
    });
  }

  private static void registerBossBar(AceEngine engine) {
    engine.registerEffect("BOSS_BAR", (ctx, targets, params) -> {
      String raw = raw(params);
      if (raw == null || raw.isBlank()) return;

      String colorRaw = firstNonBlank(params.get("color"), "BLUE");
      String styleRaw = firstNonBlank(params.get("style"), "SOLID");
      BarColor color = parseBarColor(colorRaw);
      BarStyle style = parseBarStyle(styleRaw);
      double progress = Math.max(0.0d, Math.min(1.0d, doubleParam(params, "progress", 1.0d)));
      int durationSeconds = Math.max(0, intParam(params, "duration", intParam(params, "seconds", 6)));
      long durationTicks = Math.max(1L, durationSeconds * 20L);

      for (Entity target : targets) {
        if (!(target instanceof Player player)) continue;
        String text = placeholders(raw, ctx, target);
        runAtEntity(player, () -> {
          BossBar existing = ACTIVE_BOSS_BARS.remove(player.getUniqueId());
          if (existing != null) existing.removeAll();

          BossBar bar = Bukkit.createBossBar(text, color, style);
          bar.setProgress(progress);
          bar.addPlayer(player);
          ACTIVE_BOSS_BARS.put(player.getUniqueId(), bar);

          var pdc = player.getPersistentDataContainer();
          pdc.set(PdcKeys.BOSS_BAR_TEXT, PersistentDataType.STRING, text);
          pdc.set(PdcKeys.BOSS_BAR_PROGRESS, PersistentDataType.DOUBLE, progress);

          if (durationSeconds > 0) {
            Plugin owner = ZakumApi.get().plugin();
            ZakumApi.get().getScheduler().runTaskLater(owner, () -> {
              BossBar active = ACTIVE_BOSS_BARS.get(player.getUniqueId());
              if (active != bar) return;
              active.removePlayer(player);
              active.removeAll();
              ACTIVE_BOSS_BARS.remove(player.getUniqueId());
            }, durationTicks);
          }
        });
      }
    });

    engine.registerEffect("REMOVE_BOSS_BAR", (ctx, targets, params) -> {
      for (Entity target : targets) {
        if (!(target instanceof Player player)) continue;
        runAtEntity(player, () -> {
          BossBar bar = ACTIVE_BOSS_BARS.remove(player.getUniqueId());
          if (bar != null) {
            bar.removePlayer(player);
            bar.removeAll();
          }
          player.getPersistentDataContainer().remove(PdcKeys.BOSS_BAR_TEXT);
          player.getPersistentDataContainer().remove(PdcKeys.BOSS_BAR_PROGRESS);
        });
      }
    });
  }

  private static void registerGuiEffects(AceEngine engine) {
    engine.registerEffect("OPEN_GUI", (ctx, targets, params) -> {
      String id = firstNonBlank(params.get("id"), raw(params));
      if (id == null || id.isBlank()) return;
      for (Entity target : targets) {
        if (target instanceof Player player) {
          runAtEntity(target, () -> ZakumApi.get().getGui().openLayout(player, id, Map.of()));
        }
      }
    });

    engine.registerEffect("CLOSE_INVENTORY", (ctx, targets, params) -> {
      for (Entity target : targets) {
        if (target instanceof Player player) {
          runAtEntity(target, player::closeInventory);
        }
      }
    });
  }

  private static void registerRtp(AceEngine engine) {
    var rtp = new ZakumRtpService();
    engine.registerEffect("RTP", (ctx, targets, params) -> {
      int min = intParam(params, "min", intParam(params, "minrange", 256));
      int max = intParam(params, "max", intParam(params, "maxrange", 2048));
      if (max < min) {
        int tmp = min;
        min = max;
        max = tmp;
      }
      int finalMin = Math.max(1, min);
      int finalMax = Math.max(finalMin, max);
      for (Entity target : targets) {
        if (target instanceof Player player) {
          rtp.searchAndTeleport(player, finalMin, finalMax);
        }
      }
    });
  }

  private static void registerHologram(AceEngine engine) {
    engine.registerEffect("HOLOGRAM", (ctx, targets, params) -> {
      if (isVisualSuppressed(params)) return;
      String text = firstNonBlank(params.get("text"), raw(params));
      if (text == null || text.isBlank()) return;

      String baseId = firstNonBlank(params.get("id"), "zakum-" + UUID.randomUUID());
      for (Entity target : targets) {
        Location location = resolveHologramLocation(params, target);
        if (location == null || location.getWorld() == null) continue;

        String line = placeholders(text, ctx, target);
        String id = sanitizeHologramId(placeholders(baseId, ctx, target));
        runAtLocation(location, () -> DecentHologramsBridge.createHologram(id, location, List.of(line)));
      }
    });
  }

  private static void runGlobal(Runnable task) {
    ZakumApi.get().getScheduler().runGlobal(task);
  }

  private static void runAtEntity(Entity entity, Runnable task) {
    if (entity == null) {
      runGlobal(task);
      return;
    }
    ZakumApi.get().getScheduler().runAtEntity(entity, task);
  }

  private static void runAtLocation(Location location, Runnable task) {
    if (location == null) {
      runGlobal(task);
      return;
    }
    ZakumApi.get().getScheduler().runAtLocation(location, task);
  }

  private static String raw(Map<String, String> params) {
    return firstNonBlank(params.get("raw_value"), params.get("value"));
  }

  private static String firstToken(String value) {
    if (value == null || value.isBlank()) return null;
    String[] parts = value.trim().split("\\s+", 2);
    return parts.length == 0 ? null : parts[0];
  }

  private static double parseNumber(String value, double fallback) {
    if (value == null || value.isBlank()) return fallback;
    try {
      return Double.parseDouble(value.trim().split("\\s+")[0]);
    } catch (Exception ex) {
      return fallback;
    }
  }

  private static int parseInt(String value, int fallback) {
    try {
      return Integer.parseInt(value.trim());
    } catch (Exception ex) {
      return fallback;
    }
  }

  private static String firstNonBlank(String... values) {
    if (values == null) return null;
    for (String value : values) {
      if (value != null && !value.isBlank()) return value;
    }
    return null;
  }

  private static double doubleParam(Map<String, String> params, String key, double fallback) {
    String value = params.get(key.toLowerCase(Locale.ROOT));
    if (value == null || value.isBlank()) return fallback;
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  private static int intParam(Map<String, String> params, String key, int fallback) {
    String value = params.get(key.toLowerCase(Locale.ROOT));
    if (value == null || value.isBlank()) return fallback;
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  private static boolean boolParam(Map<String, String> params, String key, boolean fallback) {
    String value = params.get(key.toLowerCase(Locale.ROOT));
    if (value == null || value.isBlank()) return fallback;
    return value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("1");
  }

  private static BarColor parseBarColor(String value) {
    if (value == null || value.isBlank()) return BarColor.BLUE;
    try {
      return BarColor.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ignored) {
      return BarColor.BLUE;
    }
  }

  private static BarStyle parseBarStyle(String value) {
    if (value == null || value.isBlank()) return BarStyle.SOLID;
    try {
      return BarStyle.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ignored) {
      return BarStyle.SOLID;
    }
  }

  private static boolean isVisualSuppressed(Map<String, String> params) {
    if (boolParam(params, "force", false)) return false;
    if (VisualCircuitState.isOpen()) return true;
    double tps = currentTps();
    double minTps = 18.0d;
    try {
      var cfg = ZakumApi.get().settings().visuals().lod();
      if (cfg != null) {
        minTps = Math.max(1.0d, cfg.minTps());
      }
    } catch (Throwable ignored) {
      // Fallback default threshold.
    }
    return tps < minTps;
  }

  private static double currentTps() {
    try {
      double[] tps = Bukkit.getTPS();
      if (tps == null || tps.length == 0) return 20.0d;
      double oneMinute = tps[0];
      if (Double.isFinite(oneMinute) && oneMinute > 0.0d) {
        return oneMinute;
      }
    } catch (Throwable ignored) {
      // Fall through.
    }
    return 20.0d;
  }

  private static long readLong(Long value, long fallback) {
    return value == null ? fallback : value;
  }

  private static String placeholders(String input, AceEngine.ActionContext ctx, Entity target) {
    if (input == null) return "";
    String out = input;
    out = out.replace("%player%", ctx.actor().getName());
    out = out.replace("%uuid%", ctx.actor().getUniqueId().toString());
    if (target != null) {
      out = out.replace("%target%", target.getName());
      out = out.replace("%target_uuid%", target.getUniqueId().toString());
    }
    for (Map.Entry<String, Object> entry : ctx.metadata().entrySet()) {
      if (entry.getKey() == null || entry.getValue() == null) continue;
      out = out.replace("%" + entry.getKey() + "%", String.valueOf(entry.getValue()));
    }
    return out;
  }

  private static Map<String, String> templatePlaceholders(
    AceEngine.ActionContext ctx,
    Entity target,
    Map<String, String> params
  ) {
    Map<String, String> placeholders = new HashMap<>();
    if (ctx != null && ctx.actor() != null) {
      placeholders.put("player", ctx.actor().getName());
      placeholders.put("uuid", ctx.actor().getUniqueId().toString());
    }
    if (target != null) {
      placeholders.put("target", target.getName());
      placeholders.put("target_uuid", target.getUniqueId().toString());
    }
    if (ctx != null && ctx.metadata() != null) {
      for (Map.Entry<String, Object> entry : ctx.metadata().entrySet()) {
        if (entry.getKey() == null || entry.getValue() == null) continue;
        placeholders.put(entry.getKey(), String.valueOf(entry.getValue()));
      }
    }
    if (params != null) {
      for (Map.Entry<String, String> entry : params.entrySet()) {
        String key = entry.getKey();
        String value = entry.getValue();
        if (key == null || value == null) continue;
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.equals("key") || lower.equals("locale") || lower.equals("lang")) continue;
        if (lower.equals("value") || lower.equals("raw_value")) continue;
        placeholders.put(lower, value);
      }
    }
    return placeholders;
  }

  private static Location parseLocation(String raw, Map<String, String> params, Location base) {
    String worldName = params.get("world");
    double x = doubleParam(params, "x", Double.NaN);
    double y = doubleParam(params, "y", Double.NaN);
    double z = doubleParam(params, "z", Double.NaN);

    if (!Double.isNaN(x) && !Double.isNaN(y) && !Double.isNaN(z)) {
      var world = worldName == null ? base.getWorld() : Bukkit.getWorld(worldName);
      if (world == null) return null;
      return new Location(world, x, y, z);
    }

    String[] parts = raw.replace(',', ' ').trim().split("\\s+");
    if (parts.length < 3) return null;

    int offset = 0;
    var world = base.getWorld();
    if (parts.length >= 4) {
      var parsedWorld = Bukkit.getWorld(parts[0]);
      if (parsedWorld != null) {
        world = parsedWorld;
        offset = 1;
      }
    }

    if (world == null) return null;

    try {
      double px = Double.parseDouble(parts[offset]);
      double py = Double.parseDouble(parts[offset + 1]);
      double pz = Double.parseDouble(parts[offset + 2]);
      return new Location(world, px, py, pz);
    } catch (Exception ex) {
      return null;
    }
  }

  private static Vector parseVector(String raw, Map<String, String> params) {
    double x = doubleParam(params, "x", Double.NaN);
    double y = doubleParam(params, "y", Double.NaN);
    double z = doubleParam(params, "z", Double.NaN);
    if (!Double.isNaN(x) && !Double.isNaN(y) && !Double.isNaN(z)) {
      return new Vector(x, y, z);
    }

    if (raw == null || raw.isBlank()) return null;
    String[] parts = raw.replace(',', ' ').trim().split("\\s+");
    if (parts.length < 3) return null;
    try {
      return new Vector(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
    } catch (Exception ex) {
      return null;
    }
  }

  private static Location resolveHologramLocation(Map<String, String> params, Entity target) {
    if (target == null || target.getWorld() == null) return null;

    String worldName = params.get("world");
    double x = doubleParam(params, "x", Double.NaN);
    double y = doubleParam(params, "y", Double.NaN);
    double z = doubleParam(params, "z", Double.NaN);
    if (!Double.isNaN(x) && !Double.isNaN(y) && !Double.isNaN(z)) {
      var world = worldName == null ? target.getWorld() : Bukkit.getWorld(worldName);
      if (world == null) return null;
      return new Location(world, x, y, z);
    }

    double yOffset = doubleParam(params, "y_offset", 2.2d);
    return target.getLocation().clone().add(0.0d, yOffset, 0.0d);
  }

  private static String sanitizeHologramId(String value) {
    if (value == null || value.isBlank()) return "zakum-" + UUID.randomUUID();
    return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
  }
}
