package net.orbis.zakum.bridge.luckperms;

import net.orbis.zakum.api.luckperms.LuckPermsService;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class OrbisBridgeLuckPermsPlugin extends JavaPlugin {

  private LuckPermsService impl;

  @Override
  public void onEnable() {
    if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
      getLogger().warning("LuckPerms not found. Disabling OrbisBridgeLuckPerms.");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }

    final LuckPerms lp;
    try {
      lp = LuckPermsProvider.get();
    } catch (Exception e) {
      getLogger().warning("LuckPermsProvider.get() failed: " + e.getMessage());
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }

    this.impl = new LuckPermsServiceImpl(lp);

    Bukkit.getServicesManager().register(
      LuckPermsService.class,
      impl,
      this,
      ServicePriority.Normal
    );

    getLogger().info("OrbisBridgeLuckPerms enabled.");
  }

  @Override
  public void onDisable() {
    if (impl != null) {
      Bukkit.getServicesManager().unregister(LuckPermsService.class, impl);
    }
    impl = null;
  }
}
