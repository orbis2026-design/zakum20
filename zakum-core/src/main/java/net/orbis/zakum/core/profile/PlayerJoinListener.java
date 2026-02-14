package net.orbis.zakum.core.profile;

import net.orbis.zakum.api.concurrent.ZakumScheduler;
import net.orbis.zakum.api.storage.DataStore;
import net.orbis.zakum.core.social.CloudTabRenderer;
import net.orbis.zakum.core.util.PdcKeys;
import net.orbis.zakum.core.util.PdcStats;
import org.bson.Document;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerJoinListener implements Listener, AutoCloseable {

  private static final long ONE_MINUTE_MS = 60_000L;

  private final ZakumScheduler scheduler;
  private final DataStore dataStore;
  private final CloudTabRenderer tabRenderer;
  private final ProfileProvider profileProvider;
  private final Map<UUID, Thread> playtimeWorkers;
  private final Map<UUID, ProfileSnapshot> snapshots;

  public PlayerJoinListener(ZakumScheduler scheduler, DataStore dataStore) {
    this(scheduler, dataStore, null, null);
  }

  public PlayerJoinListener(ZakumScheduler scheduler, DataStore dataStore, CloudTabRenderer tabRenderer) {
    this(scheduler, dataStore, tabRenderer, null);
  }

  public PlayerJoinListener(ZakumScheduler scheduler, DataStore dataStore, CloudTabRenderer tabRenderer, ProfileProvider profileProvider) {
    this.scheduler = scheduler;
    this.dataStore = dataStore;
    this.tabRenderer = tabRenderer;
    this.profileProvider = profileProvider;
    this.playtimeWorkers = new ConcurrentHashMap<>();
    this.snapshots = new ConcurrentHashMap<>();
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    UUID uuid = player.getUniqueId();
    stopWorker(uuid);

    var profileLoad = profileProvider != null
      ? profileProvider.takeOrLoad(uuid)
      : dataStore.loadProfile(uuid);

    profileLoad.exceptionally(ex -> "{}").thenAccept(json -> {
      LoadedProfile loaded = parseProfile(json);
      long now = System.currentTimeMillis();
      long firstJoin = loaded.firstJoinEpochMs() > 0L ? loaded.firstJoinEpochMs() : now;
      ProfileSnapshot snapshot = new ProfileSnapshot(
        firstJoin,
        Math.max(0L, loaded.playtimeMinutes()),
        Math.max(0, loaded.deaths()),
        Math.max(0, loaded.kills()),
        loaded.discordId()
      );

      snapshots.put(uuid, snapshot);
      scheduler.runAtEntity(player, () -> {
        if (!player.isOnline()) return;
        var pdc = player.getPersistentDataContainer();
        if (loaded.isNew() || !pdc.has(PdcKeys.FIRST_JOIN, PersistentDataType.LONG)) {
          pdc.set(PdcKeys.FIRST_JOIN, PersistentDataType.LONG, firstJoin);
        }
        pdc.set(PdcKeys.PLAYTIME, PersistentDataType.LONG, snapshot.playtimeMinutes());
        pdc.set(PdcKeys.DEATHS, PersistentDataType.INTEGER, snapshot.deaths());
        pdc.set(PdcKeys.KILLS, PersistentDataType.INTEGER, snapshot.kills());
        if (snapshot.discordId() != null && !snapshot.discordId().isBlank()) {
          pdc.set(PdcKeys.CLOUD_DISCORD_ID, PersistentDataType.STRING, snapshot.discordId());
        }
        if (tabRenderer != null) {
          tabRenderer.render(player);
        }
      });

      if (loaded.isNew()) {
        scheduler.runAsync(() -> saveSnapshot(uuid, snapshot));
      }
      startWorker(player);
    });
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    UUID uuid = player.getUniqueId();
    stopWorker(uuid);
    if (profileProvider != null) {
      profileProvider.invalidate(uuid);
    }

    var pdc = player.getPersistentDataContainer();
    long firstJoin = getLong(pdc.get(PdcKeys.FIRST_JOIN, PersistentDataType.LONG), System.currentTimeMillis());
    long playtime = getLong(pdc.get(PdcKeys.PLAYTIME, PersistentDataType.LONG), 0L);
    int deaths = getInt(pdc.get(PdcKeys.DEATHS, PersistentDataType.INTEGER), 0);
    int kills = getInt(pdc.get(PdcKeys.KILLS, PersistentDataType.INTEGER), 0);
    String discordId = pdc.get(PdcKeys.CLOUD_DISCORD_ID, PersistentDataType.STRING);
    ProfileSnapshot snapshot = new ProfileSnapshot(firstJoin, playtime, deaths, kills, discordId);
    snapshots.put(uuid, snapshot);

    scheduler.runAsync(() -> saveSnapshot(uuid, snapshot));
    snapshots.remove(uuid);
  }

  @EventHandler(ignoreCancelled = true)
  public void onDeath(PlayerDeathEvent event) {
    Player player = event.getEntity();
    PdcStats.increment(player, PdcStats.DEATHS);

    UUID uuid = player.getUniqueId();
    ProfileSnapshot next = snapshots.compute(uuid, (id, current) -> {
      if (current == null) {
        String discord = player.getPersistentDataContainer().get(PdcKeys.CLOUD_DISCORD_ID, PersistentDataType.STRING);
        return new ProfileSnapshot(System.currentTimeMillis(), 0L, 1, 0, discord);
      }
      return current.withDeaths(current.deaths() + 1);
    });
    if (next == null) return;

    Player killer = player.getKiller();
    if (killer != null) {
      PdcStats.increment(killer, PdcStats.KILLS);
      snapshots.compute(killer.getUniqueId(), (id, current) -> {
        if (current == null) {
          long firstJoin = getLong(killer.getPersistentDataContainer().get(PdcKeys.FIRST_JOIN, PersistentDataType.LONG), System.currentTimeMillis());
          long playtime = getLong(killer.getPersistentDataContainer().get(PdcKeys.PLAYTIME, PersistentDataType.LONG), 0L);
          int deaths = killer.getPersistentDataContainer().getOrDefault(PdcKeys.DEATHS, PersistentDataType.INTEGER, 0);
          int kills = killer.getPersistentDataContainer().getOrDefault(PdcKeys.KILLS, PersistentDataType.INTEGER, 0);
          String discord = killer.getPersistentDataContainer().get(PdcKeys.CLOUD_DISCORD_ID, PersistentDataType.STRING);
          return new ProfileSnapshot(firstJoin, playtime, deaths, kills, discord);
        }
        return current.withKills(current.kills() + 1);
      });
    }
  }

  @Override
  public void close() {
    playtimeWorkers.values().forEach(Thread::interrupt);
    playtimeWorkers.clear();
    snapshots.clear();
    if (profileProvider != null) {
      profileProvider.clear();
    }
  }

  private void startWorker(Player player) {
    UUID uuid = player.getUniqueId();
    Thread worker = Thread.ofVirtual().name("zakum-profile-" + uuid).start(() -> {
      int unsavedMinutes = 0;
      while (!Thread.currentThread().isInterrupted()) {
        try {
          Thread.sleep(ONE_MINUTE_MS);
        } catch (InterruptedException ignored) {
          Thread.currentThread().interrupt();
          break;
        }
        if (!player.isOnline()) break;

        ProfileSnapshot next = snapshots.compute(uuid, (id, current) -> {
          if (current == null) return null;
          return current.withPlaytimeMinutes(current.playtimeMinutes() + 1L);
        });
        if (next == null) break;

        long playtime = next.playtimeMinutes();
        scheduler.runAtEntity(player, () -> {
          if (player.isOnline()) {
            player.getPersistentDataContainer().set(PdcKeys.PLAYTIME, PersistentDataType.LONG, playtime);
          }
        });

        unsavedMinutes++;
        if (unsavedMinutes >= 5) {
          unsavedMinutes = 0;
          ProfileSnapshot finalNext = next;
          scheduler.runAsync(() -> saveSnapshot(uuid, finalNext));
        }
      }
    });

    Thread previous = playtimeWorkers.put(uuid, worker);
    if (previous != null) previous.interrupt();
  }

  private void stopWorker(UUID uuid) {
    Thread worker = playtimeWorkers.remove(uuid);
    if (worker != null) worker.interrupt();
  }

  private void saveSnapshot(UUID uuid, ProfileSnapshot snapshot) {
    if (snapshot == null) return;
    Document doc = new Document()
      .append("first_join", snapshot.firstJoinEpochMs())
      .append("playtime", snapshot.playtimeMinutes())
      .append("deaths", snapshot.deaths())
      .append("kills", snapshot.kills())
      .append("updated_at", System.currentTimeMillis());
    if (snapshot.discordId() != null && !snapshot.discordId().isBlank()) {
      doc.append("discord_id", snapshot.discordId());
    }
    dataStore.saveProfile(uuid, doc.toJson()).exceptionally(ex -> null);
  }

  private static LoadedProfile parseProfile(String json) {
    if (json == null || json.isBlank() || "{}".equals(json.trim())) {
      return LoadedProfile.fresh();
    }
    try {
      Document doc = Document.parse(json);
      if (doc.isEmpty()) return LoadedProfile.fresh();
      long firstJoin = longValue(doc, "first_join", 0L);
      long playtime = longValue(doc, "playtime", 0L);
      int deaths = intValue(doc, "deaths", 0);
      int kills = intValue(doc, "kills", 0);
      String discordId = stringValue(doc, "discord_id");
      boolean isNew = firstJoin <= 0L;
      return new LoadedProfile(isNew, firstJoin, playtime, deaths, kills, discordId);
    } catch (Throwable ignored) {
      return LoadedProfile.fresh();
    }
  }

  private static long longValue(Document doc, String key, long fallback) {
    Object value = doc.get(key);
    if (value instanceof Number number) return number.longValue();
    if (value instanceof String text) {
      try {
        return Long.parseLong(text);
      } catch (NumberFormatException ignored) {
        return fallback;
      }
    }
    return fallback;
  }

  private static int intValue(Document doc, String key, int fallback) {
    Object value = doc.get(key);
    if (value instanceof Number number) return number.intValue();
    if (value instanceof String text) {
      try {
        return Integer.parseInt(text);
      } catch (NumberFormatException ignored) {
        return fallback;
      }
    }
    return fallback;
  }

  private static String stringValue(Document doc, String key) {
    Object value = doc.get(key);
    if (value == null) return null;
    String text = String.valueOf(value).trim();
    return text.isBlank() ? null : text;
  }

  private static long getLong(Long value, long fallback) {
    return value == null ? fallback : value;
  }

  private static int getInt(Integer value, int fallback) {
    return value == null ? fallback : value;
  }

  private record LoadedProfile(boolean isNew, long firstJoinEpochMs, long playtimeMinutes, int deaths, int kills, String discordId) {
    private static LoadedProfile fresh() {
      return new LoadedProfile(true, System.currentTimeMillis(), 0L, 0, 0, null);
    }
  }

  private record ProfileSnapshot(long firstJoinEpochMs, long playtimeMinutes, int deaths, int kills, String discordId) {
    private ProfileSnapshot withPlaytimeMinutes(long next) {
      return new ProfileSnapshot(firstJoinEpochMs, next, deaths, kills, discordId);
    }

    private ProfileSnapshot withDeaths(int next) {
      return new ProfileSnapshot(firstJoinEpochMs, playtimeMinutes, next, kills, discordId);
    }

    private ProfileSnapshot withKills(int next) {
      return new ProfileSnapshot(firstJoinEpochMs, playtimeMinutes, deaths, next, discordId);
    }
  }
}
