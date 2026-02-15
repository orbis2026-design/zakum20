package net.orbis.orbisessentials.util;

import net.orbis.zakum.api.util.BrandingText;

public final class Color {
  private Color() {}

  public static String legacy(String s) {
    return BrandingText.render(s);
  }
}
