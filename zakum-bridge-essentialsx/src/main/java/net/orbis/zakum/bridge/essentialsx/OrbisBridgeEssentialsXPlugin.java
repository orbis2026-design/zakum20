package net.orbis.zakum.bridge.essentialsx;

import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.api.actions.ActionEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class OrbisBridgeEssentialsXPlugin extends JavaPlugin implements Listener {

  private ZakumApi zakum;

  private boolean enabledEmit;
  private boolean requireEssentials;

  private final Map<String, CommandMap> commands = new HashMap<>();

  @Override
  public void onEnable() {
    saveDefaultConfig();

    this.enabledEmit = getConfig().getBoolean("emit.enabled", true);
    this.requireEssentials = getConfig().getBoolean("emit.requireEssentials", true);

    if (!enabledEmit) {
      getLogger().info("emit.enabled=false; disabling bridge logic.");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }

    if (requireEssentials && Bukkit.getPluginManager().getPlugin("Essentials") == null) {
      getLogger().warning("Essentials not found. Disabling OrbisBridgeEssentialsX (requireEssentials=true).");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }

    this.zakum = Bukkit.getServicesManager().load(ZakumApi.class);
    if (zakum == null) {
      getLogger().severe("ZakumApi not found. Disabling OrbisBridgeEssentialsX.");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }

    loadCommandMap();

    Bukkit.getPluginManager().registerEvents(this, this);
    getLogger().info("OrbisBridgeEssentialsX enabled. mapped=" + commands.size());
  }

  @Override
  public void onDisable() {
    zakum = null;
    commands.clear();
  }

  @EventHandler
  public void onCmd(PlayerCommandPreprocessEvent e) {
    if (zakum == null) return;

    String msg = e.getMessage();
    if (msg == null || msg.isBlank()) return;

    // Strip leading slash and split.
    String raw = msg.charAt(0) == '/' ? msg.substring(1) : msg;
    String[] parts = raw.trim().split("\\s+");
    if (parts.length == 0) return;

    String base = parts[0].toLowerCase(Locale.ROOT);

    CommandMap m = commands.get(base);
    if (m == null || !m.enabled) return;

    String captured = "";
    if (m.captureArgumentIndex >= 0 && parts.length > m.captureArgumentIndex) {
      captured = parts[m.captureArgumentIndex].trim();
    }

    zakum.actions().publish(new ActionEvent(
      m.actionType,
      e.getPlayer().getUniqueId(),
      1,
      m.key,
      captured.isBlank() ? base.toUpperCase(Locale.ROOT) : captured.toUpperCase(Locale.ROOT)
    ));
  }

  private void loadCommandMap() {
    commands.clear();

    var sec = getConfig().getConfigurationSection("commands");
    if (sec == null) return;

    for (String cmd : sec.getKeys(false)) {
      var c = sec.getConfigurationSection(cmd);
      if (c == null) continue;

      boolean enabled = c.getBoolean("enabled", true);
      String actionType = c.getString("actionType", cmd + "_use");
      int argIdx = c.getInt("captureArgumentIndex", -1);
      String key = c.getString("key", cmd);

      commands.put(cmd.toLowerCase(Locale.ROOT), new CommandMap(enabled, actionType, argIdx, key));
    }
  }

  private record CommandMap(boolean enabled, String actionType, int captureArgumentIndex, String key) {}
}
