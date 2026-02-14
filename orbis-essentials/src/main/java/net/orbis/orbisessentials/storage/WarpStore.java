package net.orbis.orbisessentials.storage;

import net.orbis.orbisessentials.model.Warp;
import net.orbis.zakum.api.db.Jdbc;
import net.orbis.zakum.api.util.UuidBytes;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class WarpStore {

  private final String serverId;
  private final Jdbc jdbc;

  public WarpStore(String serverId, Jdbc jdbc) {
    this.serverId = serverId;
    this.jdbc = jdbc;
  }

  public void upsert(Warp w) {
    jdbc.update(
      "INSERT INTO orbis_ess_warps (server_id, name, world_uuid, x, y, z, yaw, pitch) VALUES (?,?,?,?,?,?,?,?) " +
        "ON DUPLICATE KEY UPDATE world_uuid=VALUES(world_uuid), x=VALUES(x), y=VALUES(y), z=VALUES(z), yaw=VALUES(yaw), pitch=VALUES(pitch)",
      serverId,
      w.name().toLowerCase(Locale.ROOT),
      UuidBytes.toBytes(w.world()),
      w.x(), w.y(), w.z(), w.yaw(), w.pitch()
    );
  }

  public void delete(String name) {
    jdbc.update(
      "DELETE FROM orbis_ess_warps WHERE server_id=? AND name=?",
      serverId,
      name.toLowerCase(Locale.ROOT)
    );
  }

  public Warp get(String name) {
    return jdbc.queryOne(
      "SELECT world_uuid, x, y, z, yaw, pitch FROM orbis_ess_warps WHERE server_id=? AND name=?",
      rs -> new Warp(
        name.toLowerCase(Locale.ROOT),
        UuidBytes.fromBytes(rs.getBytes(1)),
        rs.getDouble(2),
        rs.getDouble(3),
        rs.getDouble(4),
        rs.getFloat(5),
        rs.getFloat(6)
      ),
      serverId,
      name.toLowerCase(Locale.ROOT)
    );
  }

  public List<String> listNames() {
    return jdbc.query(
      "SELECT name FROM orbis_ess_warps WHERE server_id=? ORDER BY name ASC",
      rs -> rs.getString(1),
      serverId
    );
  }
}
