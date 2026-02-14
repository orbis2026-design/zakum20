package net.orbis.zakum.bridge.citizens;

import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.api.actions.ActionEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;

public final class OrbisBridgeCitizensPlugin extends JavaPlugin implements Listener {

  private static final String NPC_META_KEY = "NPC";

  private ZakumApi zakum;

  @Override
  public void onEnable() {
    this.zakum = Bukkit.getServicesManager().load(ZakumApi.class);
    if (zakum == null) {
      getLogger().severe("ZakumApi not found. Disabling OrbisBridgeCitizens.");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }

    if (Bukkit.getPluginManager().getPlugin("Citizens") == null) {
      getLogger().warning("Citizens not found. Disabling OrbisBridgeCitizens.");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }

    Bukkit.getPluginManager().registerEvents(this, this);
    zakum.getBridgeManager().registerBridge("citizens");
    getLogger().info("OrbisBridgeCitizens enabled.");
  }

  @Override
  public void onDisable() {
    if (zakum != null) {
      zakum.getBridgeManager().unregisterBridge("citizens");
    }
    zakum = null;
  }

  @EventHandler
  public void onNpcRightClick(PlayerInteractEntityEvent e) {
    if (zakum == null) return;

    Player p = e.getPlayer();
    Entity clicked = e.getRightClicked();
    if (p == null || clicked == null) return;

    if (!clicked.hasMetadata(NPC_META_KEY)) return;

    String npcId = resolveNpcId(clicked);
    if (npcId.isBlank()) npcId = "UNKNOWN";

    zakum.actions().publish(new ActionEvent(
      "npc_interact",
      p.getUniqueId(),
      1,
      "npc",
      npcId
    ));
  }

  private static String resolveNpcId(Entity entity) {
    try {
      List<MetadataValue> values = entity.getMetadata(NPC_META_KEY);
      if (values == null || values.isEmpty()) return "";

      Object npcObj = values.get(0).value();
      if (npcObj == null) return "";

      // Citizens NPC type: net.citizensnpcs.api.npc.NPC
      // We avoid a hard dependency by reflection.
      Class<?> c = npcObj.getClass();

      // Prefer getId(): int
      try {
        var m = c.getMethod("getId");
        Object id = m.invoke(npcObj);
        if (id != null) return String.valueOf(id);
      } catch (NoSuchMethodException ignored) {}

      // Fallback getName(): String
      try {
        var m = c.getMethod("getName");
        Object name = m.invoke(npcObj);
        if (name != null) return String.valueOf(name).trim().toUpperCase(Locale.ROOT);
      } catch (NoSuchMethodException ignored) {}

      return "";
    } catch (Exception ignored) {
      return "";
    }
  }
}
