package net.orbis.zakum.bridge.placeholderapi;

import net.orbis.zakum.api.placeholders.PlaceholderService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class OrbisBridgePlaceholderApiPlugin extends JavaPlugin {

  private PlaceholderService impl;

  @Override
  public void onEnable() {
    if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
      getLogger().warning("PlaceholderAPI not found. Disabling OrbisBridgePlaceholderAPI.");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }

    this.impl = new PlaceholderApiServiceImpl();

    Bukkit.getServicesManager().register(
      PlaceholderService.class,
      impl,
      this,
      ServicePriority.Normal
    );

    getLogger().info("OrbisBridgePlaceholderAPI enabled.");
  }

  @Override
  public void onDisable() {
    if (impl != null) {
      Bukkit.getServicesManager().unregister(PlaceholderService.class, impl);
    }
    impl = null;
  }
}
