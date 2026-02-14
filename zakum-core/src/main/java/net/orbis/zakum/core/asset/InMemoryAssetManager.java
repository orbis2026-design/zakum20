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
  public String resolveGlyph(String identifier) {
    if (identifier == null || identifier.isBlank()) return identifier;
    return assets.getOrDefault(identifier, identifier);
  }

  @Override
  public void registerAsset(String identifier, String value) {
    if (identifier == null || identifier.isBlank() || value == null) return;
    assets.put(identifier, value);
  }
}
