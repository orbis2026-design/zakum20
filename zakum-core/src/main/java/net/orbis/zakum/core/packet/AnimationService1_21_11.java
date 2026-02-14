package net.orbis.zakum.core.packet;

import net.orbis.zakum.api.concurrent.ZakumScheduler;
import net.orbis.zakum.api.packet.AnimationService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 1.21.11 packet animation layer for personal visuals.
 * Index 23 is used for ItemDisplay itemstack metadata.
 */
public class AnimationService1_21_11 implements AnimationService {

  private static final int DISPLAY_ITEM_METADATA_INDEX = 23;

  private final Plugin plugin;
  private final ZakumScheduler scheduler;

  public AnimationService1_21_11(Plugin plugin, ZakumScheduler scheduler) {
    this.plugin = plugin;
    this.scheduler = scheduler;
  }

  @Override
  public void spawnDisplay(Player viewer, Location loc, ItemStack item) {
    spawnCrateItem(viewer, loc, item);
  }

  public void spawnCrateItem(Player viewer, Location loc) {
    spawnCrateItem(viewer, loc, new ItemStack(Material.CHEST));
  }

  public void spawnCrateItem(Player viewer, Location loc, ItemStack item) {
    if (viewer == null || loc == null || item == null || loc.getWorld() == null) return;

    int entityId = ThreadLocalRandom.current().nextInt(2_000_000, 2_050_000);
    if (!tryPacketDisplay(viewer, loc, item, entityId)) return;
    scheduler.runTaskLater(plugin, () -> tryDestroyPacketDisplay(viewer, entityId), 40L);
  }

  private boolean tryPacketDisplay(Player viewer, Location loc, ItemStack item, int entityId) {
    try {
      Class<?> packetEventsClass = Class.forName("com.github.retrooper.packetevents.PacketEvents");
      Object api = packetEventsClass.getMethod("getAPI").invoke(null);
      if (api == null) return false;

      Class<?> entityTypesClass = Class.forName("com.github.retrooper.packetevents.protocol.entity.type.EntityTypes");
      Object itemDisplayType = entityTypesClass.getField("ITEM_DISPLAY").get(null);

      Class<?> vector3dClass = Class.forName("com.github.retrooper.packetevents.util.Vector3d");
      Object vector3d = vector3dClass
        .getConstructor(double.class, double.class, double.class)
        .newInstance(loc.getX(), loc.getY(), loc.getZ());

      Class<?> spawnClass = Class.forName("com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity");
      Constructor<?> spawnCtor = findSpawnConstructor(spawnClass);
      if (spawnCtor == null) return false;
      Object spawnPacket = newSpawnPacket(spawnCtor, entityId, itemDisplayType, vector3d);

      Class<?> conversionClass = loadSpigotConversionUtil();
      Object peItem = conversionClass.getMethod("fromBukkitItemStack", ItemStack.class).invoke(null, item);

      Class<?> entityDataTypesClass = Class.forName("com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes");
      Object itemType = entityDataTypesClass.getField("ITEMSTACK").get(null);

      Class<?> entityDataClass = Class.forName("com.github.retrooper.packetevents.protocol.entity.data.EntityData");
      Object entityData = entityDataClass
        .getConstructor(int.class, Class.forName("com.github.retrooper.packetevents.protocol.entity.data.EntityDataType"), Object.class)
        .newInstance(DISPLAY_ITEM_METADATA_INDEX, itemType, peItem);

      Class<?> metadataClass = Class.forName("com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata");
      Object metadataPacket = metadataClass.getConstructor(int.class, List.class).newInstance(entityId, List.of(entityData));

      sendPacket(api, viewer, spawnPacket);
      sendPacket(api, viewer, metadataPacket);
      return true;
    } catch (Throwable ignored) {
      return false;
    }
  }

  protected boolean tryDestroyPacketDisplay(Player viewer, int entityId) {
    try {
      Class<?> packetEventsClass = Class.forName("com.github.retrooper.packetevents.PacketEvents");
      Object api = packetEventsClass.getMethod("getAPI").invoke(null);
      if (api == null) return false;

      Class<?> destroyClass = Class.forName("com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities");
      Object destroyPacket;
      try {
        destroyPacket = destroyClass.getConstructor(int.class).newInstance(entityId);
      } catch (NoSuchMethodException ignored) {
        destroyPacket = destroyClass.getConstructor(int[].class).newInstance((Object) new int[]{entityId});
      }
      sendPacket(api, viewer, destroyPacket);
      return true;
    } catch (Throwable ignored) {
      return false;
    }
  }

  private static Constructor<?> findSpawnConstructor(Class<?> spawnClass) {
    for (Constructor<?> c : spawnClass.getConstructors()) {
      Class<?>[] p = c.getParameterTypes();
      if (p.length == 9 && p[0] == int.class && p[4] == float.class && p[5] == float.class && p[6] == float.class && p[7] == int.class) {
        return c;
      }
    }
    return null;
  }

  private static Object newSpawnPacket(Constructor<?> ctor, int entityId, Object itemDisplayType, Object position) throws Exception {
    Class<?>[] p = ctor.getParameterTypes();
    Object[] args = new Object[9];
    args[0] = entityId;
    args[1] = Optional.class.isAssignableFrom(p[1]) ? Optional.of(UUID.randomUUID()) : UUID.randomUUID();
    args[2] = itemDisplayType;
    args[3] = position;
    args[4] = 0f;
    args[5] = 0f;
    args[6] = 0f;
    args[7] = 0;
    args[8] = Optional.class.isAssignableFrom(p[8]) ? Optional.empty() : null;
    return ctor.newInstance(args);
  }

  private static Class<?> loadSpigotConversionUtil() throws ClassNotFoundException {
    try {
      return Class.forName("com.github.retrooper.packetevents.util.SpigotConversionUtil");
    } catch (ClassNotFoundException ignored) {
      return Class.forName("io.github.retrooper.packetevents.util.SpigotConversionUtil");
    }
  }

  private static void sendPacket(Object api, Player viewer, Object packet) throws Exception {
    Method getPlayerManager = api.getClass().getMethod("getPlayerManager");
    Object playerManager = getPlayerManager.invoke(api);
    for (Method method : playerManager.getClass().getMethods()) {
      if (!method.getName().equals("sendPacket")) continue;
      Class<?>[] params = method.getParameterTypes();
      if (params.length != 2) continue;
      if (!params[0].isAssignableFrom(viewer.getClass())) continue;
      if (!params[1].isAssignableFrom(packet.getClass()) && params[1] != Object.class) continue;
      method.invoke(playerManager, viewer, packet);
      return;
    }
    throw new NoSuchMethodException("No compatible sendPacket method found");
  }
}
