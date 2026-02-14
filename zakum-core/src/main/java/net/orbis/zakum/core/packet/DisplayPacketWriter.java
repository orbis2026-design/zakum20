package net.orbis.zakum.core.packet;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Packet-only display helper for 1.21.11.
 */
public final class DisplayPacketWriter {

  private static final int DISPLAY_ITEM_METADATA_INDEX = 23;
  private static final int DISPLAY_INTERPOLATION_METADATA_INDEX = 12;
  private static final int TEXT_DISPLAY_TEXT_METADATA_INDEX = 23;
  private static final int DEFAULT_INTERPOLATION_TICKS = 3;

  private DisplayPacketWriter() {}

  public static void spawnGhostItem(Player viewer, Location loc, int entityId) {
    spawnGhostItem(viewer, loc, null, entityId);
  }

  public static void spawnGhostItem(Player viewer, Location loc, ItemStack item, int entityId) {
    spawnGhostItem(viewer, loc, item, entityId, DEFAULT_INTERPOLATION_TICKS);
  }

  public static void spawnGhostItem(Player viewer, Location loc, ItemStack item, int entityId, int interpolationTicks) {
    if (viewer == null || loc == null) return;
    try {
      Class<?> packetEventsClass = Class.forName("com.github.retrooper.packetevents.PacketEvents");
      Object api = packetEventsClass.getMethod("getAPI").invoke(null);
      if (api == null) return;

      Class<?> entityTypesClass = Class.forName("com.github.retrooper.packetevents.protocol.entity.type.EntityTypes");
      Object itemDisplayType = entityTypesClass.getField("ITEM_DISPLAY").get(null);

      Class<?> vector3dClass = Class.forName("com.github.retrooper.packetevents.util.Vector3d");
      Object position = vector3dClass
        .getConstructor(double.class, double.class, double.class)
        .newInstance(loc.getX(), loc.getY(), loc.getZ());

      Class<?> spawnClass = Class.forName("com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity");
      Constructor<?> ctor = findSpawnConstructor(spawnClass);
      if (ctor == null) return;
      Object spawnPacket = newSpawnPacket(ctor, entityId, itemDisplayType, position);

      sendPacket(api, viewer, spawnPacket);
      if (item != null) {
        Object metadataPacket = createItemMetadataPacket(entityId, item, interpolationTicks);
        if (metadataPacket != null) {
          sendPacket(api, viewer, metadataPacket);
        }
      }
    } catch (Throwable ignored) {
      // Packet backend is optional.
    }
  }

  public static boolean spawnTextLabel(Player viewer, Location loc, String label, int entityId) {
    return spawnTextLabel(viewer, loc, label, entityId, DEFAULT_INTERPOLATION_TICKS);
  }

  public static boolean spawnTextLabel(Player viewer, Location loc, String label, int entityId, int interpolationTicks) {
    if (viewer == null || loc == null || label == null) return false;
    try {
      Class<?> packetEventsClass = Class.forName("com.github.retrooper.packetevents.PacketEvents");
      Object api = packetEventsClass.getMethod("getAPI").invoke(null);
      if (api == null) return false;

      Class<?> entityTypesClass = Class.forName("com.github.retrooper.packetevents.protocol.entity.type.EntityTypes");
      Object textDisplayType = entityTypesClass.getField("TEXT_DISPLAY").get(null);

      Class<?> vector3dClass = Class.forName("com.github.retrooper.packetevents.util.Vector3d");
      Object position = vector3dClass
        .getConstructor(double.class, double.class, double.class)
        .newInstance(loc.getX(), loc.getY(), loc.getZ());

      Class<?> spawnClass = Class.forName("com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity");
      Constructor<?> ctor = findSpawnConstructor(spawnClass);
      if (ctor == null) return false;
      Object spawnPacket = newSpawnPacket(ctor, entityId, textDisplayType, position);

      Object metadataPacket = createTextMetadataPacket(entityId, Component.text(label), interpolationTicks);
      if (metadataPacket == null) return false;

      sendPacket(api, viewer, spawnPacket);
      sendPacket(api, viewer, metadataPacket);
      return true;
    } catch (Throwable ignored) {
      // Packet backend is optional.
      return false;
    }
  }

  private static Constructor<?> findSpawnConstructor(Class<?> spawnClass) {
    for (Constructor<?> ctor : spawnClass.getConstructors()) {
      Class<?>[] params = ctor.getParameterTypes();
      if (params.length != 9) continue;
      if (params[0] != int.class) continue;
      if (params[4] != float.class || params[5] != float.class || params[6] != float.class) continue;
      if (params[7] != int.class) continue;
      return ctor;
    }
    return null;
  }

  private static Object newSpawnPacket(Constructor<?> ctor, int entityId, Object type, Object position) throws Exception {
    Class<?>[] params = ctor.getParameterTypes();
    Object[] args = new Object[9];
    args[0] = entityId;
    args[1] = Optional.class.isAssignableFrom(params[1]) ? Optional.of(UUID.randomUUID()) : UUID.randomUUID();
    args[2] = type;
    args[3] = position;
    args[4] = 0f;
    args[5] = 0f;
    args[6] = 0f;
    args[7] = 0;
    args[8] = Optional.class.isAssignableFrom(params[8]) ? Optional.empty() : null;
    return ctor.newInstance(args);
  }

  private static Object createItemMetadataPacket(int entityId, ItemStack item, int interpolationTicks) throws Exception {
    Class<?> conversionClass = loadSpigotConversionUtil();
    Object peItem = conversionClass.getMethod("fromBukkitItemStack", ItemStack.class).invoke(null, item);

    Class<?> entityDataTypesClass = Class.forName("com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes");
    Object itemType = resolveEntityDataType(entityDataTypesClass, "ITEMSTACK");
    if (itemType == null) return null;

    List<Object> entries = new ArrayList<>(2);
    Object itemData = createEntityData(DISPLAY_ITEM_METADATA_INDEX, itemType, peItem);
    if (itemData == null) return null;
    entries.add(itemData);

    Object interpolationType = resolveEntityDataType(entityDataTypesClass, "INT", "VAR_INT");
    Object interpolationData = createEntityData(
      DISPLAY_INTERPOLATION_METADATA_INDEX,
      interpolationType,
      Math.max(0, interpolationTicks)
    );
    if (interpolationData != null) {
      entries.add(interpolationData);
    }

    Class<?> metadataClass = Class.forName("com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata");
    return metadataClass.getConstructor(int.class, List.class).newInstance(entityId, entries);
  }

  private static Object createTextMetadataPacket(int entityId, Component text, int interpolationTicks) throws Exception {
    Class<?> entityDataTypesClass = Class.forName("com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes");
    Object textType = resolveEntityDataType(entityDataTypesClass, "ADV_COMPONENT");
    if (textType == null) return null;

    List<Object> entries = new ArrayList<>(2);
    Object textData = createEntityData(TEXT_DISPLAY_TEXT_METADATA_INDEX, textType, text);
    if (textData == null) return null;
    entries.add(textData);

    Object interpolationType = resolveEntityDataType(entityDataTypesClass, "INT", "VAR_INT");
    Object interpolationData = createEntityData(
      DISPLAY_INTERPOLATION_METADATA_INDEX,
      interpolationType,
      Math.max(0, interpolationTicks)
    );
    if (interpolationData != null) {
      entries.add(interpolationData);
    }

    Class<?> metadataClass = Class.forName("com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata");
    return metadataClass.getConstructor(int.class, List.class).newInstance(entityId, entries);
  }

  private static Object createEntityData(int index, Object dataType, Object value) throws Exception {
    if (dataType == null) return null;
    Class<?> entityDataTypeClass = Class.forName("com.github.retrooper.packetevents.protocol.entity.data.EntityDataType");
    Class<?> entityDataClass = Class.forName("com.github.retrooper.packetevents.protocol.entity.data.EntityData");
    return entityDataClass
      .getConstructor(int.class, entityDataTypeClass, Object.class)
      .newInstance(index, dataType, value);
  }

  private static Object resolveEntityDataType(Class<?> typesClass, String... names) {
    for (String name : names) {
      if (name == null || name.isBlank()) continue;
      try {
        return typesClass.getField(name).get(null);
      } catch (Throwable ignored) {
        // Probe fallback names.
      }
    }
    return null;
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
      if (method.getParameterCount() != 2) continue;
      method.invoke(playerManager, viewer, packet);
      return;
    }
  }
}
