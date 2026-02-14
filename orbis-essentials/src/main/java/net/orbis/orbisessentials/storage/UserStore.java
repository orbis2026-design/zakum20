package net.orbis.orbisessentials.storage;

import net.orbis.orbisessentials.model.LastLocation;
import net.orbis.zakum.api.db.Jdbc;
import net.orbis.zakum.api.util.UuidBytes;

import java.util.UUID;

public final class UserStore {

  private final String serverId;
  private final Jdbc jdbc;

  public UserStore(String serverId, Jdbc jdbc) {
    this.serverId = serverId;
    this.jdbc = jdbc;
  }

  public void setLast(UUID uuid, LastLocation loc) {
    jdbc.update(
      "INSERT INTO orbis_ess_users (server_id, uuid, last_world_uuid, last_x, last_y, last_z, last_yaw, last_pitch) VALUES (?,?,?,?,?,?,?,?) " +
        "ON DUPLICATE KEY UPDATE last_world_uuid=VALUES(last_world_uuid), last_x=VALUES(last_x), last_y=VALUES(last_y), last_z=VALUES(last_z), last_yaw=VALUES(last_yaw), last_pitch=VALUES(last_pitch)",
      serverId,
      UuidBytes.toBytes(uuid),
      loc == null ? null : UuidBytes.toBytes(loc.world()),
      loc == null ? null : loc.x(),
      loc == null ? null : loc.y(),
      loc == null ? null : loc.z(),
      loc == null ? null : loc.yaw(),
      loc == null ? null : loc.pitch()
    );
  }

  public LastLocation getLast(UUID uuid) {
    return jdbc.queryOne(
      "SELECT last_world_uuid, last_x, last_y, last_z, last_yaw, last_pitch FROM orbis_ess_users WHERE server_id=? AND uuid=?",
      rs -> {
        byte[] w = rs.getBytes(1);
        if (w == null) return null;
        return new LastLocation(
          UuidBytes.fromBytes(w),
          rs.getDouble(2),
          rs.getDouble(3),
          rs.getDouble(4),
          rs.getFloat(5),
          rs.getFloat(6)
        );
      },
      serverId,
      UuidBytes.toBytes(uuid)
    );
  }
}
