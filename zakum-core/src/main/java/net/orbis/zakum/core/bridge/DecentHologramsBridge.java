package net.orbis.zakum.core.bridge;

import org.bukkit.Location;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

public final class DecentHologramsBridge {

  private DecentHologramsBridge() {}

  public static boolean createHologram(String id, Location location, List<String> lines) {
    if (location == null || lines == null || lines.isEmpty()) return false;
    for (String className : List.of(
      "eu.decentsoftware.holograms.api.DHAPI",
      "eu.decentsoftware.holograms.api.DecentHologramsAPI"
    )) {
      try {
        Class<?> apiClass = Class.forName(className);
        if (invokeCreate(apiClass, id, location, lines)) return true;
      } catch (ClassNotFoundException ignored) {
        // The plugin is optional.
      } catch (Throwable ignored) {
        return false;
      }
    }
    return false;
  }

  private static boolean invokeCreate(Class<?> apiClass, String id, Location location, List<String> lines) throws Exception {
    for (Method method : apiClass.getMethods()) {
      if (!Modifier.isStatic(method.getModifiers())) continue;
      if (!method.getName().equals("createHologram")) continue;
      Class<?>[] params = method.getParameterTypes();
      if (params.length < 2) continue;
      if (params[0] != String.class) continue;
      if (!Location.class.isAssignableFrom(params[1])) continue;

      if (params.length == 3 && List.class.isAssignableFrom(params[2])) {
        method.invoke(null, id, location, lines);
        return true;
      }
      if (params.length == 3 && params[2] == String.class) {
        method.invoke(null, id, location, lines.get(0));
        return true;
      }
      if (params.length == 2) {
        Object hologram = method.invoke(null, id, location);
        applyLines(hologram, lines);
        return true;
      }
    }
    return false;
  }

  private static void applyLines(Object hologram, List<String> lines) {
    if (hologram == null || lines.isEmpty()) return;
    try {
      Method setLines = hologram.getClass().getMethod("setLines", List.class);
      setLines.invoke(hologram, lines);
    } catch (Throwable ignored) {
      // Ignore if this DH version exposes a different mutator API.
    }
  }
}
