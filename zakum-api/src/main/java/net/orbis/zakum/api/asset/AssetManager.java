package net.orbis.zakum.api.asset;

public interface AssetManager {

  String resolveGlyph(String identifier);

  void registerAsset(String identifier, String value);
}
