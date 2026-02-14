package net.orbis.zakum.core.bootstrap;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import net.orbis.zakum.core.concurrent.EarlySchedulerRuntime;

public final class ZakumBootstrap implements PluginBootstrap {

  @Override
  public void bootstrap(BootstrapContext context) {
    EarlySchedulerRuntime.initializeEarlyExecutor();
    context.getLogger().info("Zakum bootstrap initialized.");
  }
}
