package net.orbis.zakum.minipets.util;

import net.orbis.zakum.api.util.BrandingText;

public final class Colors {
  private Colors() {}
  public static String color(String s) {
    return BrandingText.render(s);
  }
}
