package net.orbis.zakum.api.storage;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * No-SQL profile/session contract for rapid-schema evolution modules.
 */
public interface DataStore {

  CompletableFuture<Void> saveProfile(UUID uuid, String jsonData);

  CompletableFuture<String> loadProfile(UUID uuid);

  void setSessionData(UUID uuid, String key, String value);

  String getSessionData(UUID uuid, String key);
}
