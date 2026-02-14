package net.orbis.zakum.core.packet;

import net.orbis.zakum.api.concurrent.ZakumScheduler;
import net.orbis.zakum.api.packet.AnimationService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
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
 * Packet-first display animation with safe fallback to live entities when PacketEvents is unavailable.
 */
public final class PacketAnimationService implements AnimationService {

  private static final int DISPLAY_ITEM_METADATA_INDEX = 23;

  private final Plugin plugin;
  private final ZakumScheduler scheduler;

  public PacketAnimationService(Plugin plugin, ZakumScheduler scheduler) {
    this.plugin = plugin;
    this.scheduler = scheduler;
  }

  @Override
  public void spawnDisplay(Player viewer, Location loc, ItemStack item) {
    if (viewer == null || loc == null || item == null || loc.getWorld() == null) return;

    int entityId = ThreadLocalRandom.current().nextInt(2_000_000, 3_000_000);
    if (tryPacketDisplay(viewer, loc, item, entityId)) {
      scheduler.runTaskLater(plugin, () -> tryDestroyPacketDisplay(viewer, entityId), 40L);
      return;
    }

    spawnFallbackEntity(loc, item);
  }

  private void spawnFallbackEntity(Location loc, ItemStack item) {
    scheduler.runAtLocation(loc, () -> {
      World world = loc.getWorld();
      if (world == null) return;

      ItemDisplay display = world.spawn(loc, ItemDisplay.class, e -> {
        e.setItemStack(item.clone());
        e.setBillboard(Display.Billboard.CENTER);
        e.setGravity(false);
        e.setPersistent(false);
      });

      scheduler.runTaskLater(plugin, () -> {
        if (display.isValid()) display.remove();
      }, 40L);
    });
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

      Object spawnPacket = spawnCtor.newInstance(
        entityId,
        Optional.of(UUID.randomUUID()),
        itemDisplayType,
        vector3d,
        0f,
        0f,
        0f,
        0,
        Optional.empty()
      );

      Class<?> conversionClass = Class.forName("io.github.retrooper.packetevents.util.SpigotConversionUtil");
      Object peItem = conversionClass.getMethod("fromBukkitItemStack", ItemStack.class).invoke(null, item);

      Class<?> entityDataTypesClass = Class.forName("com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes");
      Object itemType = entityDataTypesClass.getField("ITEMSTACK").get(null);

      Class<?> entityDataClass = Class.forName("com.github.retrooper.packetevents.protocol.entity.data.EntityData");
      Object entityData = entityDataClass
        .getConstructor(int.class, Class.forName("com.github.retrooper.packetevents.protocol.entity.data.EntityDataType"), Object.class)
        .newInstance(DISPLAY_ITEM_METADATA_INDEX, itemType, peItem);

      Class<?> metadataClass = Class.forName("com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata");
      Object metadataPacket = metadataClass
        .getConstructor(int.class, List.class)
        .newInstance(entityId, List.of(entityData));

      sendPacket(api, viewer, spawnPacket);
      sendPacket(api, viewer, metadataPacket);
      return true;
    } catch (Throwable ignored) {
      return false;
    }
  }

  private boolean tryDestroyPacketDisplay(Player viewer, int entityId) {
    try {
      Class<?> packetEventsClass = Class.forName("com.github.retrooper.packetevents.PacketEvents");
      Object api = packetEventsClass.getMethod("getAPI").invoke(null);
      if (api == null) return false;

      Class<?> destroyClass = Class.forName("com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities");
      Object destroyPacket = destroyClass.getConstructor(int.class).newInstance(entityId);
      sendPacket(api, viewer, destroyPacket);
      return true;
    } catch (Throwable ignored) {
      return false;
    }
  }

  private static Constructor<?> findSpawnConstructor(Class<?> spawnClass) {
    for (Constructor<?> c : spawnClass.getConstructors()) {
      Class<?>[] p = c.getParameterTypes();
      if (p.length != 9) continue;
      if (p[0] == int.class
        && Optional.class.isAssignableFrom(p[1])
        && p[4] == float.class
        && p[5] == float.class
        && p[6] == float.class
        && p[7] == int.class
        && Optional.class.isAssignableFrom(p[8])) {
        return c;
      }
    }
    return null;
  }

  private static void sendPacket(Object api, Player viewer, Object packet) throws Exception {
    Method getPlayerManager = api.getClass().getMethod("getPlayerManager");
    Object playerManager = getPlayerManager.invoke(api);
    Method sendPacket = playerManager.getClass().getMethod("sendPacket", Object.class, Object.class);
    sendPacket.invoke(playerManager, viewer, packet);
  }
}
