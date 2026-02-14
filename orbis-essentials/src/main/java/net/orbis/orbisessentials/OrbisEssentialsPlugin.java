package net.orbis.orbisessentials;

import net.orbis.orbisessentials.model.Home;
import net.orbis.orbisessentials.model.LastLocation;
import net.orbis.orbisessentials.model.Warp;
import net.orbis.orbisessentials.storage.EssSchema;
import net.orbis.orbisessentials.storage.HomeStore;
import net.orbis.orbisessentials.storage.UserStore;
import net.orbis.orbisessentials.storage.WarpStore;
import net.orbis.orbisessentials.teleport.Messages;
import net.orbis.orbisessentials.teleport.TeleportService;
import net.orbis.orbisessentials.teleport.TpaManager;
import net.orbis.orbisessentials.util.LocSerde;
import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.api.db.DatabaseState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.UUID;

public final class OrbisEssentialsPlugin extends JavaPlugin {

  private ZakumApi zakum;
  private String serverId;

  private Messages msg;
  private TeleportService tp;
  private TpaManager tpa;

  private HomeStore homes;
  private WarpStore warps;
  private UserStore users;

  @Override
  public void onEnable() {
    saveDefaultConfig();

    this.zakum = Bukkit.getServicesManager().load(ZakumApi.class);
    if (zakum == null) {
      getLogger().severe("ZakumApi not found. Disabling OrbisEssentials.");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }

    this.serverId = zakum.server().serverId();
    this.msg = new Messages(getConfig());
    this.tp = new TeleportService(this, msg);
    this.tpa = new TpaManager();

    if (zakum.database().state() == DatabaseState.ONLINE) {
      EssSchema.ensure(zakum.database().jdbc());
      this.homes = new HomeStore(serverId, zakum.database().jdbc());
      this.warps = new WarpStore(serverId, zakum.database().jdbc());
      this.users = new UserStore(serverId, zakum.database().jdbc());
      getLogger().info("DB: ONLINE");
    } else {
      getLogger().warning("DB: OFFLINE (homes/warps/back persistence disabled)");
    }

    Bukkit.getPluginManager().registerEvents(new OrbisEssentialsListener(tp), this);

    getLogger().info("OrbisEssentials enabled. serverId=" + serverId);
  }

  @Override
  public void onDisable() {
    zakum = null;
    serverId = null;
    msg = null;
    tp = null;
    tpa = null;
    homes = null;
    warps = null;
    users = null;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    String cmd = command.getName().toLowerCase(Locale.ROOT);

    if (!(sender instanceof Player p)) {
      sender.sendMessage(msg == null ? "Players only." : msg.pref(msg.playerOnly()));
      return true;
    }

    return switch (cmd) {
      case "ospawn" -> cmdSpawn(p);
      case "osetspawn" -> cmdSetSpawn(p);
      case "ohome" -> cmdHome(p, args);
      case "osethome" -> cmdSetHome(p, args);
      case "odelhome" -> cmdDelHome(p, args);
      case "owarp" -> cmdWarp(p, args);
      case "osetwarp" -> cmdSetWarp(p, args);
      case "odelwarp" -> cmdDelWarp(p, args);
      case "oback" -> cmdBack(p);
      case "otpa" -> cmdTpa(p, args);
      case "otpaccept" -> cmdTpAccept(p);
      case "otpdeny" -> cmdTpDeny(p);
      default -> false;
    };
  }

  private boolean cmdSpawn(Player p) {
    Location spawn = LocSerde.readSpawn(getConfig().getConfigurationSection("spawn"));
    if (spawn == null) {
      p.sendMessage(msg.pref(msg.notFound()));
      return true;
    }

    rememberBack(p);

    int warm = Math.max(0, getConfig().getInt("teleport.warmupSeconds", 0));
    boolean cancelMove = getConfig().getBoolean("teleport.cancelOnMove.enabled", true);
    double maxMove = Math.max(0.0, getConfig().getDouble("teleport.cancelOnMove.maxDistanceBlocks", 0.5));
    int cd = Math.max(0, getConfig().getInt("teleport.cooldownSeconds", 0));

    tp.teleport(p, spawn, warm, cancelMove, maxMove, cd);
    return true;
  }

  private boolean cmdSetSpawn(Player p) {
    if (!p.hasPermission("orbis.essentials.admin")) {
      p.sendMessage(msg.pref(msg.noPermission()));
      return true;
    }

    LocSerde.writeSpawn(getConfig().getConfigurationSection("spawn"), p.getLocation());
    saveConfig();
    p.sendMessage(msg.pref("&aSpawn set."));
    return true;
  }

  private boolean cmdHome(Player p, String[] args) {
    if (homes == null) {
      p.sendMessage(msg.pref("&cHomes unavailable (DB offline)."));
      return true;
    }

    String name = args.length >= 1 ? args[0] : "home";
    name = sanitizeName(name);

    var h = homes.get(p.getUniqueId(), name);
    if (h == null) {
      p.sendMessage(msg.pref(msg.notFound()));
      return true;
    }

    Location dest = loc(h.world(), h.x(), h.y(), h.z(), h.yaw(), h.pitch());
    if (dest == null) {
      p.sendMessage(msg.pref(msg.notFound()));
      return true;
    }

    rememberBack(p);

    int warm = Math.max(0, getConfig().getInt("teleport.warmupSeconds", 0));
    boolean cancelMove = getConfig().getBoolean("teleport.cancelOnMove.enabled", true);
    double maxMove = Math.max(0.0, getConfig().getDouble("teleport.cancelOnMove.maxDistanceBlocks", 0.5));
    int cd = Math.max(0, getConfig().getInt("teleport.cooldownSeconds", 0));

    tp.teleport(p, dest, warm, cancelMove, maxMove, cd);
    return true;
  }

  private boolean cmdSetHome(Player p, String[] args) {
    if (homes == null) {
      p.sendMessage(msg.pref("&cHomes unavailable (DB offline)."));
      return true;
    }

    String name = args.length >= 1 ? args[0] : "home";
    name = sanitizeName(name);
    if (name.isBlank()) {
      p.sendMessage(msg.pref("&cInvalid home name."));
      return true;
    }

    int max = Math.max(0, getConfig().getInt("homes.max", 3));
    if (max > 0 && homes.count(p.getUniqueId()) >= max && homes.get(p.getUniqueId(), name) == null) {
      p.sendMessage(msg.pref("&cHome limit reached."));
      return true;
    }

    var l = p.getLocation();
    if (l.getWorld() == null) return true;

    Home h = new Home(p.getUniqueId(), name, l.getWorld().getUID(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());

    zakum.async().execute(() -> homes.upsert(h));
    p.sendMessage(msg.pref(msg.homeSet().replace("{name}", name)));
    return true;
  }

  private boolean cmdDelHome(Player p, String[] args) {
    if (homes == null) {
      p.sendMessage(msg.pref("&cHomes unavailable (DB offline)."));
      return true;
    }

    String name = args.length >= 1 ? args[0] : "home";
    name = sanitizeName(name);

    String nn = name;
    zakum.async().execute(() -> homes.delete(p.getUniqueId(), nn));
    p.sendMessage(msg.pref(msg.homeDeleted().replace("{name}", name)));
    return true;
  }

  private boolean cmdWarp(Player p, String[] args) {
    if (warps == null) {
      p.sendMessage(msg.pref("&cWarps unavailable (DB offline)."));
      return true;
    }
    if (args.length < 1) return false;

    String name = sanitizeName(args[0]);
    var w = warps.get(name);
    if (w == null) {
      p.sendMessage(msg.pref(msg.notFound()));
      return true;
    }

    Location dest = loc(w.world(), w.x(), w.y(), w.z(), w.yaw(), w.pitch());
    if (dest == null) {
      p.sendMessage(msg.pref(msg.notFound()));
      return true;
    }

    rememberBack(p);

    int warm = Math.max(0, getConfig().getInt("teleport.warmupSeconds", 0));
    boolean cancelMove = getConfig().getBoolean("teleport.cancelOnMove.enabled", true);
    double maxMove = Math.max(0.0, getConfig().getDouble("teleport.cancelOnMove.maxDistanceBlocks", 0.5));
    int cd = Math.max(0, getConfig().getInt("teleport.cooldownSeconds", 0));

    tp.teleport(p, dest, warm, cancelMove, maxMove, cd);
    return true;
  }

  private boolean cmdSetWarp(Player p, String[] args) {
    if (!p.hasPermission("orbis.essentials.admin")) {
      p.sendMessage(msg.pref(msg.noPermission()));
      return true;
    }
    if (warps == null) {
      p.sendMessage(msg.pref("&cWarps unavailable (DB offline)."));
      return true;
    }
    if (args.length < 1) return false;

    String name = sanitizeName(args[0]);
    if (name.isBlank()) {
      p.sendMessage(msg.pref("&cInvalid warp name."));
      return true;
    }

    var l = p.getLocation();
    if (l.getWorld() == null) return true;

    Warp w = new Warp(name, l.getWorld().getUID(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    zakum.async().execute(() -> warps.upsert(w));
    p.sendMessage(msg.pref(msg.warpSet().replace("{name}", name)));
    return true;
  }

  private boolean cmdDelWarp(Player p, String[] args) {
    if (!p.hasPermission("orbis.essentials.admin")) {
      p.sendMessage(msg.pref(msg.noPermission()));
      return true;
    }
    if (warps == null) {
      p.sendMessage(msg.pref("&cWarps unavailable (DB offline)."));
      return true;
    }
    if (args.length < 1) return false;

    String name = sanitizeName(args[0]);
    String nn = name;
    zakum.async().execute(() -> warps.delete(nn));
    p.sendMessage(msg.pref(msg.warpDeleted().replace("{name}", name)));
    return true;
  }

  private boolean cmdBack(Player p) {
    if (users == null) {
      p.sendMessage(msg.pref("&cBack unavailable (DB offline)."));
      return true;
    }

    var last = users.getLast(p.getUniqueId());
    if (last == null) {
      p.sendMessage(msg.pref(msg.backMissing()));
      return true;
    }

    Location dest = loc(last.world(), last.x(), last.y(), last.z(), last.yaw(), last.pitch());
    if (dest == null) {
      p.sendMessage(msg.pref(msg.notFound()));
      return true;
    }

    int warm = Math.max(0, getConfig().getInt("teleport.warmupSeconds", 0));
    boolean cancelMove = getConfig().getBoolean("teleport.cancelOnMove.enabled", true);
    double maxMove = Math.max(0.0, getConfig().getDouble("teleport.cancelOnMove.maxDistanceBlocks", 0.5));
    int cd = Math.max(0, getConfig().getInt("teleport.cooldownSeconds", 0));

    tp.teleport(p, dest, warm, cancelMove, maxMove, cd);
    return true;
  }

  private boolean cmdTpa(Player p, String[] args) {
    if (args.length < 1) return false;

    Player target = Bukkit.getPlayerExact(args[0]);
    if (target == null) {
      p.sendMessage(msg.pref(msg.notFound()));
      return true;
    }
    if (target.getUniqueId().equals(p.getUniqueId())) return true;

    long exp = Math.max(5, getConfig().getLong("tpa.expireSeconds", 60));
    tpa.request(p, target, System.currentTimeMillis() + exp * 1000L);

    p.sendMessage(msg.pref(msg.tpaSent().replace("{player}", target.getName())));
    target.sendMessage(msg.pref(msg.tpaReceived().replace("{player}", p.getName())));
    return true;
  }

  private boolean cmdTpAccept(Player target) {
    var r = tpa.popForTarget(target.getUniqueId());
    if (r == null) {
      target.sendMessage(msg.pref(msg.tpaExpired()));
      return true;
    }

    Player requester = Bukkit.getPlayer(r.requester());
    if (requester == null) {
      target.sendMessage(msg.pref(msg.tpaExpired()));
      return true;
    }

    rememberBack(requester);

    int warm = Math.max(0, getConfig().getInt("tpa.warmupSeconds", 0));
    boolean cancelMove = getConfig().getBoolean("teleport.cancelOnMove.enabled", true);
    double maxMove = Math.max(0.0, getConfig().getDouble("teleport.cancelOnMove.maxDistanceBlocks", 0.5));
    int cd = Math.max(0, getConfig().getInt("teleport.cooldownSeconds", 0));

    tp.teleport(requester, target.getLocation(), warm, cancelMove, maxMove, cd);

    requester.sendMessage(msg.pref(msg.tpaAccepted()));
    target.sendMessage(msg.pref(msg.tpaAccepted()));
    return true;
  }

  private boolean cmdTpDeny(Player target) {
    var r = tpa.popForTarget(target.getUniqueId());
    if (r == null) {
      target.sendMessage(msg.pref(msg.tpaExpired()));
      return true;
    }

    Player requester = Bukkit.getPlayer(r.requester());
    if (requester != null) requester.sendMessage(msg.pref(msg.tpaDenied()));
    target.sendMessage(msg.pref(msg.tpaDenied()));
    return true;
  }

  private void rememberBack(Player p) {
    if (users == null) return;
    var l = p.getLocation();
    if (l.getWorld() == null) return;

    var last = new LastLocation(l.getWorld().getUID(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    zakum.async().execute(() -> users.setLast(p.getUniqueId(), last));
  }

  private static Location loc(UUID worldId, double x, double y, double z, float yaw, float pitch) {
    World w = null;
    for (World ww : Bukkit.getWorlds()) {
      if (ww.getUID().equals(worldId)) { w = ww; break; }
    }
    if (w == null) return null;
    return new Location(w, x, y, z, yaw, pitch);
  }

  private static String sanitizeName(String s) {
    if (s == null) return "";
    String x = s.trim().toLowerCase(Locale.ROOT);
    x = x.replaceAll("[^a-z0-9_-]", "");
    if (x.length() > 32) x = x.substring(0, 32);
    return x;
  }
}
