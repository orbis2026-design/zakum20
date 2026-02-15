package net.orbis.orbisworlds;

import net.orbis.orbisworlds.command.OrbisWorldsCommand;
import net.orbis.orbisworlds.config.WorldsConfig;
import net.orbis.orbisworlds.service.DefaultWorldsService;
import net.orbis.orbisworlds.service.WorldsService;
import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.api.plugin.ZakumPluginBase;
import org.bukkit.command.PluginCommand;

public final class OrbisWorldsPlugin extends ZakumPluginBase {

  private WorldsService worldsService;

  @Override
  protected void onZakumEnable(ZakumApi zakum) {
    saveDefaultConfig();
    WorldsConfig config = WorldsConfig.load(getConfig(), getLogger());
    worldsService = new DefaultWorldsService(this, zakum, config, getLogger());

    PluginCommand command = getCommand("orbisworld");
    if (command != null) {
      OrbisWorldsCommand executor = new OrbisWorldsCommand(this, worldsService);
      command.setExecutor(executor);
      command.setTabCompleter(executor);
    } else {
      getLogger().warning("/orbisworld command missing from plugin.yml");
    }

    worldsService.start();
    getLogger().info(
      "OrbisWorlds enabled. enabled=" + config.enabled() +
        ", managedWorlds=" + config.managedWorlds().size() +
        ", intervalTicks=" + config.updateIntervalTicks()
    );
  }

  @Override
  protected void onZakumDisable(ZakumApi zakum) {
    if (worldsService != null) {
      worldsService.stop();
      worldsService = null;
    }
    getLogger().info("OrbisWorlds disabled.");
  }
}
