package net.orbis.orbisessentials.storage;

import net.orbis.orbisessentials.model.Home;
import net.orbis.zakum.api.db.Jdbc;
import net.orbis.zakum.api.util.UuidBytes;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class HomeStore {

  private final String serverId;
  private final Jdbc jdbc;

  public HomeStore(String serverId, Jdbc jdbc) {
    this.serverId = serverId;
    this.jdbc = jdbc;
  }

  public void upsert(Home h) {
    jdbc.update(
      "INSERT INTO orbis_ess_homes (server_id, uuid, name, world_uuid, x, y, z, yaw, pitch) VALUES (?,?,?,?,?,?,?,?,?) " +
        "ON DUPLICATE KEY UPDATE world_uuid=VALUES(world_uuid), x=VALUES(x), y=VALUES(y), z=VALUES(z), yaw=VALUES(yaw), pitch=VALUES(pitch)",
      serverId,
      UuidBytes.toBytes(h.owner()),
      h.name().toLowerCase(Locale.ROOT),
      UuidBytes.toBytes(h.world()),
      h.x(), h.y(), h.z(), h.yaw(), h.pitch()
    );
  }

  public void delete(UUID owner, String name) {
    jdbc.update(
      "DELETE FROM orbis_ess_homes WHERE server_id=? AND uuid=? AND name=?",
      serverId,
      UuidBytes.toBytes(owner),
      name.toLowerCase(Locale.ROOT)
    );
  }

  public Home get(UUID owner, String name) {
    return jdbc.queryOne(
      "SELECT world_uuid, x, y, z, yaw, pitch FROM orbis_ess_homes WHERE server_id=? AND uuid=? AND name=?",
      rs -> new Home(
        owner,
        name.toLowerCase(Locale.ROOT),
        UuidBytes.fromBytes(rs.getBytes(1)),
        rs.getDouble(2),
        rs.getDouble(3),
        rs.getDouble(4),
        rs.getFloat(5),
        rs.getFloat(6)
      ),
      serverId,
      UuidBytes.toBytes(owner),
      name.toLowerCase(Locale.ROOT)
    );
  }

  public List<String> listNames(UUID owner) {
    return jdbc.query(
      "SELECT name FROM orbis_ess_homes WHERE server_id=? AND uuid=? ORDER BY name ASC",
      rs -> rs.getString(1),
      serverId,
      UuidBytes.toBytes(owner)
    );
  }

  public int count(UUID owner) {
    Integer n = jdbc.queryOne(
      "SELECT COUNT(*) FROM orbis_ess_homes WHERE server_id=? AND uuid=?",
      rs -> rs.getInt(1),
      serverId,
      UuidBytes.toBytes(owner)
    );
    return n == null ? 0 : n;
  }
}
