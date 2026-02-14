package net.orbis.orbisessentials.teleport;

import net.orbis.orbisessentials.util.Color;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Objects;

public final class Messages {

  private final FileConfiguration cfg;

  public Messages(FileConfiguration cfg) {
    this.cfg = Objects.requireNonNull(cfg, "cfg");
  }

  public String pref(String msg) {
    return Color.legacy(cfg.getString("messages.prefix", "&7[&bOrbis&7] ")) + Color.legacy(msg);
  }

  public String noPermission() { return Color.legacy(cfg.getString("messages.noPermission", "&cNo permission.")); }
  public String playerOnly() { return Color.legacy(cfg.getString("messages.playerOnly", "&cPlayers only.")); }

  public String tpWarmup() { return Color.legacy(cfg.getString("messages.tpWarmup", "&7Teleporting in &b{seconds}&7s. Don't move.")); }
  public String tpCancelledMove() { return Color.legacy(cfg.getString("messages.tpCancelledMove", "&cTeleport cancelled (you moved).")); }
  public String tpSuccess() { return Color.legacy(cfg.getString("messages.tpSuccess", "&aTeleported.")); }

  public String notFound() { return Color.legacy(cfg.getString("messages.notFound", "&cNot found.")); }
  public String homeSet() { return Color.legacy(cfg.getString("messages.homeSet", "&aHome set: &b{name}")); }
  public String homeDeleted() { return Color.legacy(cfg.getString("messages.homeDeleted", "&aHome deleted: &b{name}")); }
  public String warpSet() { return Color.legacy(cfg.getString("messages.warpSet", "&aWarp set: &b{name}")); }
  public String warpDeleted() { return Color.legacy(cfg.getString("messages.warpDeleted", "&aWarp deleted: &b{name}")); }
  public String tpaSent() { return Color.legacy(cfg.getString("messages.tpaSent", "&7Sent teleport request to &b{player}&7.")); }
  public String tpaReceived() { return Color.legacy(cfg.getString("messages.tpaReceived", "&7Teleport request from &b{player}&7. Use &b/otpaccept&7 or &b/otpdeny&7.")); }
  public String tpaExpired() { return Color.legacy(cfg.getString("messages.tpaExpired", "&cTeleport request expired.")); }
  public String tpaDenied() { return Color.legacy(cfg.getString("messages.tpaDenied", "&cTeleport request denied.")); }
  public String tpaAccepted() { return Color.legacy(cfg.getString("messages.tpaAccepted", "&aTeleport request accepted.")); }
  public String backMissing() { return Color.legacy(cfg.getString("messages.backMissing", "&cNo previous location.")); }
}
