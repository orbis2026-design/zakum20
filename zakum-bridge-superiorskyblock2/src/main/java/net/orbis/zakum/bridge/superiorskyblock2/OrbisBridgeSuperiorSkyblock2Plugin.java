package net.orbis.zakum.bridge.superiorskyblock2;

import net.orbis.zakum.api.actions.ActionBus;
import net.orbis.zakum.api.actions.ActionEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;

public final class OrbisBridgeSuperiorSkyblock2Plugin extends JavaPlugin {

  private ActionBus bus;

  @Override
  public void onEnable() {
    if (Bukkit.getPluginManager().getPlugin("SuperiorSkyblock2") == null) {
      getLogger().info("SuperiorSkyblock2 not found. Disabling OrbisBridgeSuperiorSkyblock2.");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }

    var rsp = Bukkit.getServicesManager().getRegistration(ActionBus.class);
    if (rsp == null || rsp.getProvider() == null) {
      getLogger().warning("Zakum ActionBus service not found. Disabling OrbisBridgeSuperiorSkyblock2.");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }
    this.bus = rsp.getProvider();

    try {
      hookIslandCreate();
      getLogger().info("OrbisBridgeSuperiorSkyblock2 enabled.");
    } catch (Throwable t) {
      getLogger().severe("Failed to hook SuperiorSkyblock2 events: " + t.getMessage());
      t.printStackTrace();
      Bukkit.getPluginManager().disablePlugin(this);
    }
  }

  @Override
  public void onDisable() {
    bus = null;
  }

  private void hookIslandCreate() throws Throwable {
    Class<?> eventClass = Class.forName("com.bgsoftware.superiorskyblock.api.events.IslandCreateEvent", false, getClassLoader());
    MethodHandles.Lookup lookup = MethodHandles.publicLookup();

    MethodHandle mhGetPlayer = lookup.findVirtual(eventClass, "getPlayer", MethodType.methodType(Object.class));
    MethodHandle mhGetSchematic = lookup.findVirtual(eventClass, "getSchematic", MethodType.methodType(String.class));

    Listener dummy = new Listener() {};
    EventExecutor exec = (ignoredListener, event) -> {
      try {
        Object superiorPlayer = mhGetPlayer.invoke(event);
        if (superiorPlayer == null) return;

        // SuperiorPlayer#getUniqueId()
        Object uuidObj = superiorPlayer.getClass().getMethod("getUniqueId").invoke(superiorPlayer);
        if (!(uuidObj instanceof java.util.UUID uuid)) return;

        String schematic = (String) mhGetSchematic.invoke(event);
        if (schematic == null) schematic = "default";

        bus.publish(new ActionEvent(
          "skyblock_island_create",
          uuid,
          1,
          "schematic",
          schematic
        ));
      } catch (Throwable t) {
        getLogger().warning("IslandCreate hook error: " + t.getClass().getSimpleName() + ": " + t.getMessage());
      }
    };

    Bukkit.getPluginManager().registerEvent((Class<? extends Event>) eventClass, dummy, EventPriority.MONITOR, exec, this, true);
  }
}
