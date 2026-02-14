package net.orbis.zakum.core.social;

import net.kyori.adventure.text.Component;
import net.orbis.zakum.api.asset.AssetManager;
import net.orbis.zakum.api.config.ZakumSettings;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Rewrites Java custom-font glyphs to Bedrock-safe text.
 */
public final class BedrockGlyphRemapper {

  private final Map<String, String> glyphMap;

  public BedrockGlyphRemapper(AssetManager assets, ZakumSettings.Chat.Bedrock cfg) {
    this.glyphMap = buildGlyphMap(assets, cfg);
  }

  public Component remap(Component input) {
    if (input == null || glyphMap.isEmpty()) return input;
    Component out = input;
    for (Map.Entry<String, String> entry : glyphMap.entrySet()) {
      out = out.replaceText(builder -> builder.matchLiteral(entry.getKey()).replacement(entry.getValue()));
    }
    return out;
  }

  private static Map<String, String> buildGlyphMap(AssetManager assets, ZakumSettings.Chat.Bedrock cfg) {
    if (assets == null || cfg == null || cfg.fallbackGlyphs().isEmpty()) return Map.of();

    LinkedHashMap<String, String> out = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : cfg.fallbackGlyphs().entrySet()) {
      String placeholder = entry.getKey();
      String fallback = entry.getValue();
      if (placeholder == null || placeholder.isBlank()) continue;
      if (fallback == null || fallback.isBlank()) continue;

      String glyph = assets.resolve(placeholder);
      if (glyph == null || glyph.isBlank()) continue;
      out.put(glyph, fallback);
    }
    return out.isEmpty() ? Map.of() : Map.copyOf(out);
  }
}
