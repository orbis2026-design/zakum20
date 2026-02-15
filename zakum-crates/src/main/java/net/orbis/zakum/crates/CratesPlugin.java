package net.orbis.zakum.crates;

import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.api.db.DatabaseState;
import net.orbis.zakum.crates.anim.CrateAnimator;
import net.orbis.zakum.crates.command.CratesCommand;
import net.orbis.zakum.crates.db.CrateBlockStore;
import net.orbis.zakum.crates.db.CratesSchema;
import net.orbis.zakum.crates.listener.CrateBlockListener;
import net.orbis.zakum.crates.listener.CrateGuiListener;
import net.orbis.zakum.api.vault.EconomyService;
import net.orbis.zakum.crates.listener.CrateInteractListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class CratesPlugin extends JavaPlugin {

  private ZakumApi zakum;

  private CrateRegistry registry;
  private CrateBlockStore store;

  private CrateAnimator animator;
  private CrateService service;

  @Override
  public void onEnable() {
    saveDefaultConfig();

    this.zakum = Bukkit.getServicesManager().load(ZakumApi.class);
    if (zakum == null) {
      getLogger().severe("ZakumApi not found. Disabling OrbisCrates.");
      getServer().getPluginManager().disablePlugin(this);
      return;
    }

    if (zakum.database().state() == DatabaseState.ONLINE) {
      CratesSchema.ensure(zakum.database().jdbc());
    } else {
      getLogger().warning("Zakum DB is offline. Crate blocks will not persist.");
    }

    this.registry = new CrateRegistry(CrateLoader.load(this));
    this.store = new CrateBlockStore(zakum);

    int steps = Math.max(10, getConfig().getInt("animation.steps", 30));
    int ticksPerStep = Math.max(1, getConfig().getInt("animation.ticksPerStep", 2));

    EconomyService eco = Bukkit.getServicesManager().load(EconomyService.class);

    var executor = new CrateRewardExecutor(eco);
    this.animator = new CrateAnimator(this, steps, ticksPerStep, executor::execute);
    this.animator.start();

    this.service = new CrateService(animator);

    store.loadAll();

    getServer().getPluginManager().registerEvents(new CrateBlockListener(store), this);
    getServer().getPluginManager().registerEvents(new CrateInteractListener(registry, store, service), this);
    getServer().getPluginManager().registerEvents(new CrateGuiListener(animator), this);

    var cmd = new CratesCommand(this, registry, store, service);
    var c = getCommand("ocrates");
    if (c != null) c.setExecutor(cmd);

    getLogger().info("OrbisCrates enabled. crates=" + registry.size());
  }

  @Override
  public void onDisable() {
    if (animator != null) animator.shutdown();
    animator = null;
    service = null;
    store = null;
    registry = null;
    zakum = null;
  }
}
