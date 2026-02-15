package net.orbis.orbishud;

import net.orbis.orbishud.command.OrbisHudCommand;
import net.orbis.orbishud.config.HudConfig;
import net.orbis.orbishud.listener.HudPlayerListener;
import net.orbis.orbishud.service.DefaultHudService;
import net.orbis.orbishud.service.HudService;
import net.orbis.zakum.api.ZakumApi;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class OrbisHudPlugin extends JavaPlugin {

  private ZakumApi zakumApi;
  private HudService hudService;

  @Override
  public void onEnable() {
    saveDefaultConfig();

    this.zakumApi = Bukkit.getServicesManager().load(ZakumApi.class);
    if (zakumApi == null) {
      getLogger().severe("ZakumApi not found. Disabling OrbisHud.");
      getServer().getPluginManager().disablePlugin(this);
      return;
    }

    HudConfig initialConfig = HudConfig.load(getConfig(), getLogger());
    this.hudService = new DefaultHudService(this, zakumApi, initialConfig, getLogger());

    getServer().getPluginManager().registerEvents(new HudPlayerListener(hudService), this);

    PluginCommand command = getCommand("orbishud");
    if (command != null) {
      OrbisHudCommand executor = new OrbisHudCommand(this, hudService);
      command.setExecutor(executor);
      command.setTabCompleter(executor);
    } else {
      getLogger().warning("/orbishud command not found in plugin.yml");
    }

    hudService.start();
    getLogger().info(
      "OrbisHud enabled. enabled=" + initialConfig.enabled() +
        ", intervalTicks=" + initialConfig.updateIntervalTicks() +
        ", profiles=" + hudService.availableProfiles().size() +
        ", defaultProfile=" + initialConfig.defaultProfile()
    );
  }

  @Override
  public void onDisable() {
    if (hudService != null) {
      hudService.stop();
      hudService = null;
    }
    zakumApi = null;
  }

  public HudService hudService() {
    return hudService;
  }
}
