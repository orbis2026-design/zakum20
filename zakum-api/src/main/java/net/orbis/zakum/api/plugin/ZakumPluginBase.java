package net.orbis.zakum.api.plugin;

import net.orbis.zakum.api.ZakumApi;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.Optional;

/**
 * Base class for feature plugins that require Zakum to be present.
 *
 * This standardizes:
 * - ZakumApi service resolution
 * - fatal startup handling when Zakum is missing
 * - predictable shutdown hooks
 */
public abstract class ZakumPluginBase extends JavaPlugin {

  private ZakumApi zakumApi;

  @Override
  public final void onEnable() {
    ZakumApi api = Bukkit.getServicesManager().load(ZakumApi.class);
    if (api == null) {
      getLogger().severe("ZakumApi not found. Disabling " + getName() + ".");
      getServer().getPluginManager().disablePlugin(this);
      return;
    }

    this.zakumApi = api;
    try {
      onZakumEnable(api);
    } catch (Throwable t) {
      getLogger().severe("Startup failed for " + getName() + ": " + t.getMessage());
      t.printStackTrace();
      getServer().getPluginManager().disablePlugin(this);
    }
  }

  @Override
  public final void onDisable() {
    ZakumApi api = this.zakumApi;
    this.zakumApi = null;
    if (api == null) return;
    try {
      onZakumDisable(api);
    } catch (Throwable t) {
      getLogger().warning("Shutdown error for " + getName() + ": " + t.getMessage());
    }
  }

  /**
   * Called after ZakumApi is resolved and before plugin is considered enabled.
   */
  protected abstract void onZakumEnable(ZakumApi zakum) throws Exception;

  /**
   * Called during disable if startup completed.
   */
  protected void onZakumDisable(ZakumApi zakum) {}

  /**
   * Accessor for subclasses after startup.
   */
  protected final ZakumApi zakum() {
    return Objects.requireNonNull(zakumApi, "ZakumApi is not available (plugin not started or already stopped).");
  }

  /**
   * Resolve optional runtime service from Bukkit ServicesManager.
   */
  protected final <T> Optional<T> optionalService(Class<T> type) {
    return Optional.ofNullable(Bukkit.getServicesManager().load(type));
  }

  /**
   * Resolve required runtime service from Bukkit ServicesManager.
   */
  protected final <T> T requiredService(Class<T> type, String reason) {
    T service = Bukkit.getServicesManager().load(type);
    if (service != null) return service;
    throw new IllegalStateException("Required service missing: " + type.getSimpleName() + " (" + reason + ")");
  }
}
