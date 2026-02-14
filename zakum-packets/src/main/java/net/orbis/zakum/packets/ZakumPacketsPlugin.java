package net.orbis.zakum.packets;

import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.api.config.ZakumSettings;
import net.orbis.zakum.api.packets.PacketService;
import net.orbis.zakum.packets.impl.PacketEventsPacketService;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Packet layer plugin.
 *
 * Backed by PacketEvents (installed as a separate plugin).
 */
public final class ZakumPacketsPlugin extends JavaPlugin implements Listener {

  private PacketService service;

  @Override
  public void onEnable() {
    ZakumApi api = Bukkit.getServicesManager().load(ZakumApi.class);
    if (api == null) {
      getLogger().severe("ZakumApi not found. Disabling ZakumPackets.");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }

    ZakumSettings settings = api.settings();
    if (!settings.packets().enabled() || settings.packets().backend() != ZakumSettings.Packets.Backend.PACKETEVENTS) {
      getLogger().info("Packets disabled in Zakum config (packets.enabled=false or backend!=PACKETEVENTS).");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }

    if (Bukkit.getPluginManager().getPlugin("PacketEvents") == null) {
      getLogger().severe("PacketEvents is not installed. Install PacketEvents to enable ZakumPackets.");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }

    // Initialize PacketEvents.
    PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
    PacketEvents.getAPI().load();
    PacketEvents.getAPI().init();

        this.service = new PacketEventsPacketService(this, api, settings.packets());
    Bukkit.getServicesManager().register(PacketService.class, service, this, ServicePriority.Normal);

    Bukkit.getPluginManager().registerEvents(this, this);

    getLogger().info("ZakumPackets enabled (PacketEvents backend).");
  }

  @Override
  public void onDisable() {
    HandlerList.unregisterAll((org.bukkit.plugin.Plugin) this);

    if (service != null) {
      try { Bukkit.getServicesManager().unregister(PacketService.class, service); } catch (Throwable ignored) {}
      service = null;
    }

    try {
      if (PacketEvents.getAPI() != null) PacketEvents.getAPI().terminate();
    } catch (Throwable ignored) {}
  }

  @EventHandler
  public void onPluginDisable(PluginDisableEvent e) {
    if (e.getPlugin().getName().equalsIgnoreCase("PacketEvents")) {
      getLogger().warning("PacketEvents disabled; disabling ZakumPackets.");
      Bukkit.getPluginManager().disablePlugin(this);
    }
  }
}
