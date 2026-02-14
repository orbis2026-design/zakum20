package net.orbis.zakum.bridge.vault;

import net.orbis.zakum.api.vault.EconomyService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class OrbisBridgeVaultPlugin extends JavaPlugin {

  private EconomyService economy;

  @Override
  public void onEnable() {
    if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
      getLogger().warning("Vault not found. Disabling OrbisBridgeVault.");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }

    var rsp = getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
    if (rsp == null || rsp.getProvider() == null) {
      getLogger().warning("Vault found, but no Economy provider is registered. Disabling OrbisBridgeVault.");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }

    this.economy = new VaultEconomyService(rsp.getProvider());

    Bukkit.getServicesManager().register(
      EconomyService.class,
      economy,
      this,
      ServicePriority.Normal
    );

    getLogger().info("OrbisBridgeVault enabled. economy=" + rsp.getProvider().getName());
  }

  @Override
  public void onDisable() {
    if (economy != null) {
      Bukkit.getServicesManager().unregister(EconomyService.class, economy);
    }
    economy = null;
  }
}
