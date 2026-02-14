package net.orbis.orbisessentials.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

public final class LocSerde {

  private LocSerde() {}

  public static Location readSpawn(ConfigurationSection sec) {
    if (sec == null) return null;
    String w = sec.getString("world", "");
    if (w == null || w.isBlank()) return null;

    World world = Bukkit.getWorld(w);
    if (world == null) return null;

    double x = sec.getDouble("x");
    double y = sec.getDouble("y");
    double z = sec.getDouble("z");
    float yaw = (float) sec.getDouble("yaw");
    float pitch = (float) sec.getDouble("pitch");
    return new Location(world, x, y, z, yaw, pitch);
  }

  public static void writeSpawn(ConfigurationSection sec, Location loc) {
    if (sec == null || loc == null || loc.getWorld() == null) return;
    sec.set("world", loc.getWorld().getName());
    sec.set("x", loc.getX());
    sec.set("y", loc.getY());
    sec.set("z", loc.getZ());
    sec.set("yaw", loc.getYaw());
    sec.set("pitch", loc.getPitch());
  }
}
