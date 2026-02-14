package net.orbis.orbisessentials.util;

import org.bukkit.ChatColor;

public final class Color {
  private Color() {}

  public static String legacy(String s) {
    if (s == null) return "";
    return ChatColor.translateAlternateColorCodes('&', s);
  }
}
