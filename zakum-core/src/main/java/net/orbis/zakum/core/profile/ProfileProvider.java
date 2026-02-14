package net.orbis.zakum.core.profile;

import net.orbis.zakum.api.storage.DataStore;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prefetches profile documents during async pre-login.
 */
public final class ProfileProvider implements Listener {

  private final DataStore dataStore;
  private final Map<UUID, CompletableFuture<String>> prefetchedProfiles;

  public ProfileProvider(DataStore dataStore) {
    this.dataStore = dataStore;
    this.prefetchedProfiles = new ConcurrentHashMap<>();
  }

  @EventHandler
  public void onPreLogin(AsyncPlayerPreLoginEvent event) {
    UUID uuid = event.getUniqueId();
    prefetchedProfiles.put(uuid, dataStore.loadProfile(uuid).exceptionally(ex -> "{}"));
  }

  public CompletableFuture<String> takeOrLoad(UUID uuid) {
    CompletableFuture<String> prefetched = prefetchedProfiles.remove(uuid);
    if (prefetched != null) return prefetched;
    return dataStore.loadProfile(uuid);
  }

  public void invalidate(UUID uuid) {
    if (uuid == null) return;
    prefetchedProfiles.remove(uuid);
  }

  public void clear() {
    prefetchedProfiles.clear();
  }
}
