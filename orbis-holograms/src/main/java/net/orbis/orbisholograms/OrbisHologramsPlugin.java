package net.orbis.orbisholograms;

import net.orbis.orbisholograms.command.OrbisHologramsCommand;
import net.orbis.orbisholograms.config.HologramsConfig;
import net.orbis.orbisholograms.service.DefaultHologramsService;
import net.orbis.orbisholograms.service.HologramsService;
import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.api.plugin.ZakumPluginBase;
import org.bukkit.command.PluginCommand;

public final class OrbisHologramsPlugin extends ZakumPluginBase {

  private HologramsService hologramsService;

  @Override
  protected void onZakumEnable(ZakumApi zakum) {
    saveDefaultConfig();
    HologramsConfig config = HologramsConfig.load(getConfig(), getLogger());
    hologramsService = new DefaultHologramsService(this, zakum, config, getLogger());

    PluginCommand command = getCommand("orbishologram");
    if (command != null) {
      OrbisHologramsCommand executor = new OrbisHologramsCommand(this, hologramsService);
      command.setExecutor(executor);
      command.setTabCompleter(executor);
    } else {
      getLogger().warning("/orbishologram command missing from plugin.yml");
    }

    hologramsService.start();
    getLogger().info(
      "OrbisHolograms enabled. enabled=" + config.enabled() +
        ", definitions=" + config.definitions().size() +
        ", intervalTicks=" + config.renderTickInterval()
    );
  }

  @Override
  protected void onZakumDisable(ZakumApi zakum) {
    if (hologramsService != null) {
      hologramsService.stop();
      hologramsService = null;
    }
    getLogger().info("OrbisHolograms disabled.");
  }
}
