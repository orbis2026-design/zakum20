package net.orbis.zakum.bridge.votifier;

import com.vexsoftware.votifier.model.VotifierEvent;
import com.vexsoftware.votifier.model.Vote;
import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.api.actions.DeferredAction;
import net.orbis.zakum.api.actions.DeferredActionService;
import net.orbis.zakum.api.actions.ActionEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public final class OrbisBridgeVotifierPlugin extends JavaPlugin implements Listener {

  private ZakumApi zakum;
  private DeferredActionService deferred;

  private boolean deferredEnabled;
  private long deferredTtlSeconds;
  private boolean deferredServerScope;

  @Override
  public void onEnable() {
    saveDefaultConfig();
    this.zakum = Bukkit.getServicesManager().load(ZakumApi.class);
    if (zakum == null) {
      getLogger().severe("ZakumApi not found. Disabling OrbisBridgeVotifier.");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }

    this.deferred = Bukkit.getServicesManager().load(DeferredActionService.class);

    this.deferredEnabled = getConfig().getBoolean("deferred.enabled", true);
    this.deferredTtlSeconds = Math.max(60, getConfig().getLong("deferred.ttlSeconds", 604800));
    this.deferredServerScope = getConfig().getBoolean("deferred.serverScope", true);

    // We do not hard-disable if NuVotifier isn't present, because many vote plugins still provide VotifierEvent.
    Bukkit.getPluginManager().registerEvents(this, this);

    getLogger().info("OrbisBridgeVotifier enabled.");
  }

  @Override
  public void onDisable() {
    zakum = null;
    deferred = null;
  }

  @EventHandler
  public void onVote(VotifierEvent event) {
    if (zakum == null || event == null) return;

    Vote v = event.getVote();
    if (v == null) return;

    String username = safe(v.getUsername());
    if (username.isBlank()) return;

    var p = Bukkit.getPlayerExact(username);
    if (p == null) {
      if (deferredEnabled && deferred != null) {
        String sid = deferredServerScope ? zakum.server().serverId() : null;
        deferred.enqueue(sid, username, new DeferredAction(
          "vote_received",
          1,
          "service",
          safe(v.getServiceName()).toUpperCase(Locale.ROOT)
        ), deferredTtlSeconds, "votifier");
      }
      return;
    }

    String service = safe(v.getServiceName()).toUpperCase(Locale.ROOT);
    if (service.isBlank()) service = "UNKNOWN";

    zakum.actions().publish(new ActionEvent(
      "vote_received",
      p.getUniqueId(),
      1,
      "service",
      service
    ));
  }

  private static String safe(String s) {
    return s == null ? "" : s.trim();
  }
}
