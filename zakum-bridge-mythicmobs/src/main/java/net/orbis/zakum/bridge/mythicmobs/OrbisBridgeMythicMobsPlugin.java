package net.orbis.zakum.bridge.mythicmobs;

import net.orbis.zakum.api.actions.ActionBus;
import net.orbis.zakum.api.actions.ActionEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Objects;

public final class OrbisBridgeMythicMobsPlugin extends JavaPlugin {

  // Support both modern and legacy class names.
  private static final List<String> EVENT_CLASSES = List.of(
    "io.lumine.mythic.bukkit.events.MythicMobDeathEvent",
    "io.lumine.xikage.mythicmobs.api.bukkit.events.MythicMobDeathEvent"
  );

  private ActionBus bus;

  @Override
  public void onEnable() {
    if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
      getLogger().info("MythicMobs not found. Disabling OrbisBridgeMythicMobs.");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }

    var rsp = Bukkit.getServicesManager().getRegistration(ActionBus.class);
    if (rsp == null || rsp.getProvider() == null) {
      getLogger().warning("Zakum ActionBus service not found. Disabling OrbisBridgeMythicMobs.");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }
    this.bus = rsp.getProvider();

    Class<?> eventClass = null;
    for (String cn : EVENT_CLASSES) {
      try {
        eventClass = Class.forName(cn, false, getClassLoader());
        break;
      } catch (ClassNotFoundException ignored) {
      }
    }

    if (eventClass == null) {
      getLogger().warning("MythicMobs is installed but MythicMobDeathEvent class was not found. Disabling OrbisBridgeMythicMobs.");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }

    try {
      registerMythicDeathHook(eventClass);
      getLogger().info("OrbisBridgeMythicMobs enabled. eventClass=" + eventClass.getName());
    } catch (Throwable t) {
      getLogger().severe("Failed to hook MythicMobDeathEvent: " + t.getMessage());
      t.printStackTrace();
      Bukkit.getPluginManager().disablePlugin(this);
    }
  }

  @Override
  public void onDisable() {
    bus = null;
  }

  private void registerMythicDeathHook(Class<?> eventClass) throws Throwable {
    // Method handles (resolved once).
    MethodHandles.Lookup lookup = MethodHandles.publicLookup();

    MethodHandle mhGetKiller = lookup.findVirtual(
      eventClass,
      "getKiller",
      MethodType.methodType(LivingEntity.class)
    );

    MethodHandle mhGetMobType = lookup.findVirtual(
      eventClass,
      "getMobType",
      MethodType.methodType(Object.class)
    );

    // MythicMob interface has getInternalName() in modern API.
    // We resolve it lazily based on the runtime return type.
    Listener dummy = new Listener() {};
    EventExecutor exec = (ignoredListener, event) -> {
      try {
        LivingEntity killer = (LivingEntity) mhGetKiller.invoke(event);
        if (!(killer instanceof Player p)) return;

        Object mobType = mhGetMobType.invoke(event);
        if (mobType == null) return;

        String internalName = resolveInternalName(mobType);
        if (internalName == null || internalName.isBlank()) return;

        bus.publish(new ActionEvent(
          "custom_mob_kill",
          p.getUniqueId(),
          1,
          "mythic",
          internalName
        ));
      } catch (Throwable t) {
        // Never propagate into the event pipeline.
        getLogger().warning("MythicMobs hook error: " + t.getClass().getSimpleName() + ": " + t.getMessage());
      }
    };

    Bukkit.getPluginManager().registerEvent(
      (Class<? extends Event>) eventClass,
      dummy,
      EventPriority.MONITOR,
      exec,
      this,
      true
    );
  }

  private String resolveInternalName(Object mobType) {
    try {
      // Fast path: modern API method.
      var m = mobType.getClass().getMethod("getInternalName");
      Object v = m.invoke(mobType);
      return Objects.toString(v, null);
    } catch (ReflectiveOperationException ignored) {
    }

    try {
      // Fallback: some versions may expose "getName" or "getMobName".
      for (String name : List.of("getName", "getMobName", "getTypeName")) {
        try {
          var m = mobType.getClass().getMethod(name);
          Object v = m.invoke(mobType);
          String s = Objects.toString(v, null);
          if (s != null && !s.isBlank()) return s;
        } catch (ReflectiveOperationException ignored2) {
        }
      }
    } catch (Throwable ignored) {
    }
    return null;
  }
}
