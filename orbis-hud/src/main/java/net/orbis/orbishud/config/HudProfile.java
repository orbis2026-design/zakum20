package net.orbis.orbishud.config;

import java.util.List;
import java.util.Locale;

public record HudProfile(
  String id,
  String title,
  List<String> lines
) {

  public HudProfile {
    id = id == null || id.isBlank() ? "default" : id.trim().toLowerCase(Locale.ROOT);
    title = title == null || title.isBlank() ? "&b&lORBIS" : title;
    lines = lines == null ? List.of() : List.copyOf(lines);
  }
}