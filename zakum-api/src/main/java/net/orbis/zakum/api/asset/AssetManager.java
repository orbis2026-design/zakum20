package net.orbis.zakum.api.asset;

public interface AssetManager {

  default void init() {}

  String resolve(String placeholder);

  void register(String placeholder, String unicode);

  // Compatibility aliases for older modules.
  default String resolveGlyph(String identifier) {
    return resolve(identifier);
  }

  default void registerAsset(String identifier, String value) {
    register(identifier, value);
  }
}
