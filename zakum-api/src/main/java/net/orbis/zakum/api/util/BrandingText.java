package net.orbis.zakum.api.util;

import org.bukkit.ChatColor;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared legacy-string renderer that supports:
 * - ampersand legacy colors
 * - minimessage-like gradient tags: <gradient:#RRGGBB:#RRGGBB>text</gradient>
 * - automatic brand/rank gradients for Orbis network naming
 */
public final class BrandingText {

  private static final Pattern GRADIENT_PATTERN = Pattern.compile(
    "(?i)<gradient:#([0-9a-f]{6}):#([0-9a-f]{6})>(.*?)</gradient>",
    Pattern.DOTALL
  );

  private static final Map<String, String[]> NAMED_GRADIENTS = new LinkedHashMap<>();

  static {
    NAMED_GRADIENTS.put("Orbis", new String[]{"38bdf8", "60a5fa"});
    NAMED_GRADIENTS.put("Cirrus", new String[]{"8EDCFF", "D8F4FF"});
    NAMED_GRADIENTS.put("Cumulus", new String[]{"FFB3C7", "FFF0D8"});
    NAMED_GRADIENTS.put("Stratus", new String[]{"A8FFE3", "FFF2B0"});
    NAMED_GRADIENTS.put("Nimbus", new String[]{"D3B6FF", "6FA8FF"});
    NAMED_GRADIENTS.put("Zenith", new String[]{"9FB7FF", "FFEAA6"});
  }

  private BrandingText() {
  }

  public static String render(String input) {
    if (input == null || input.isEmpty()) return "";
    String out = parseGradientTags(input);
    out = applyNamedGradients(out);
    return ChatColor.translateAlternateColorCodes('&', out);
  }

  public static String brandTag() {
    return "<gradient:#38bdf8:#60a5fa>Orbis</gradient>";
  }

  public static String rankTag(String rank) {
    if (rank == null || rank.isBlank()) return rank == null ? "" : rank;
    String normalized = rank.trim().toLowerCase(Locale.ROOT);
    for (Map.Entry<String, String[]> entry : NAMED_GRADIENTS.entrySet()) {
      if (entry.getKey().toLowerCase(Locale.ROOT).equals(normalized)) {
        return "<gradient:#" + entry.getValue()[0] + ":#" + entry.getValue()[1] + ">" + entry.getKey() + "</gradient>";
      }
    }
    return rank;
  }

  private static String parseGradientTags(String input) {
    Matcher matcher = GRADIENT_PATTERN.matcher(input);
    StringBuilder output = new StringBuilder();
    while (matcher.find()) {
      String fromHex = matcher.group(1);
      String toHex = matcher.group(2);
      String text = matcher.group(3);
      String replacement = applyGradient(text, fromHex, toHex);
      matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(output);
    return output.toString();
  }

  private static String applyNamedGradients(String input) {
    String out = input;
    for (Map.Entry<String, String[]> entry : NAMED_GRADIENTS.entrySet()) {
      String word = entry.getKey();
      String[] colors = entry.getValue();
      String replacement = applyGradient(word, colors[0], colors[1]);
      out = out.replaceAll("(?i)\\b" + Pattern.quote(word) + "\\b", Matcher.quoteReplacement(replacement));
    }
    return out;
  }

  private static String applyGradient(String text, String fromHex, String toHex) {
    if (text == null || text.isEmpty()) return "";

    int[] from = hexToRgb(fromHex);
    int[] to = hexToRgb(toHex);
    int len = text.length();
    StringBuilder out = new StringBuilder(len * 14);

    for (int i = 0; i < len; i++) {
      double factor = len == 1 ? 0.0D : (double) i / (double) (len - 1);
      int r = lerp(from[0], to[0], factor);
      int g = lerp(from[1], to[1], factor);
      int b = lerp(from[2], to[2], factor);
      out.append(sectionHex(r, g, b)).append(text.charAt(i));
    }
    return out.toString();
  }

  private static int[] hexToRgb(String hex) {
    String sanitized = hex == null ? "FFFFFF" : hex.replace("#", "");
    if (sanitized.length() != 6) sanitized = "FFFFFF";
    return new int[]{
      Integer.parseInt(sanitized.substring(0, 2), 16),
      Integer.parseInt(sanitized.substring(2, 4), 16),
      Integer.parseInt(sanitized.substring(4, 6), 16)
    };
  }

  private static int lerp(int a, int b, double factor) {
    return (int) Math.round(a + (b - a) * factor);
  }

  private static String sectionHex(int r, int g, int b) {
    String hex = String.format("%02x%02x%02x", clampColor(r), clampColor(g), clampColor(b));
    StringBuilder sb = new StringBuilder(14);
    sb.append('\u00A7').append('x');
    for (int i = 0; i < 6; i++) {
      sb.append('\u00A7').append(hex.charAt(i));
    }
    return sb.toString();
  }

  private static int clampColor(int value) {
    return Math.max(0, Math.min(255, value));
  }
}
