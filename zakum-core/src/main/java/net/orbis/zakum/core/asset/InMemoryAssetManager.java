package net.orbis.zakum.core.asset;

import net.orbis.zakum.api.asset.AssetManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryAssetManager implements AssetManager {

  private final Map<String, String> assets;

  public InMemoryAssetManager() {
    this.assets = new ConcurrentHashMap<>();
  }

  @Override
  public void init() {
    register(":logo:", "\uE001");
    register(":coin:", "\uE002");
    register(":check:", "<green>\u2714</green>");
  }

  @Override
  public String resolve(String placeholder) {
    if (placeholder == null || placeholder.isBlank()) return placeholder;
    String output = placeholder;
    for (Map.Entry<String, String> entry : assets.entrySet()) {
      output = output.replace(entry.getKey(), entry.getValue());
    }
    return output;
  }

  @Override
  public void register(String placeholder, String unicode) {
    if (placeholder == null || placeholder.isBlank() || unicode == null) return;
    assets.put(placeholder, unicode);
  }
}
