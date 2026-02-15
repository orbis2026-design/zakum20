package net.orbis.orbisloot;

import net.orbis.orbisloot.command.OrbisLootCommand;
import net.orbis.orbisloot.config.LootConfig;
import net.orbis.orbisloot.service.DefaultLootService;
import net.orbis.orbisloot.service.LootService;
import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.api.plugin.ZakumPluginBase;
import org.bukkit.command.PluginCommand;

public final class OrbisLootPlugin extends ZakumPluginBase {

  private LootService lootService;

  @Override
  protected void onZakumEnable(ZakumApi zakum) {
    saveDefaultConfig();
    LootConfig config = LootConfig.load(getConfig(), getLogger());
    lootService = new DefaultLootService(this, zakum, config, getLogger());

    PluginCommand command = getCommand("orbisloot");
    if (command != null) {
      OrbisLootCommand executor = new OrbisLootCommand(this, lootService);
      command.setExecutor(executor);
      command.setTabCompleter(executor);
    } else {
      getLogger().warning("/orbisloot command missing from plugin.yml");
    }

    lootService.start();
    getLogger().info(
      "OrbisLoot enabled. enabled=" + config.enabled() +
        ", crates=" + config.crates().size() +
        ", cleanupIntervalTicks=" + config.cleanupIntervalTicks()
    );
  }

  @Override
  protected void onZakumDisable(ZakumApi zakum) {
    if (lootService != null) {
      lootService.stop();
      lootService = null;
    }
    getLogger().info("OrbisLoot disabled.");
  }
}
