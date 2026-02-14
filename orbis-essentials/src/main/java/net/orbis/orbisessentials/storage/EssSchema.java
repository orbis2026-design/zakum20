package net.orbis.orbisessentials.storage;

import net.orbis.zakum.api.db.Jdbc;

public final class EssSchema {

  private EssSchema() {}

  public static void ensure(Jdbc jdbc) {
    jdbc.update(
      "CREATE TABLE IF NOT EXISTS orbis_ess_homes (" +
        "server_id VARCHAR(64) NOT NULL," +
        "uuid BINARY(16) NOT NULL," +
        "name VARCHAR(32) NOT NULL," +
        "world_uuid BINARY(16) NOT NULL," +
        "x DOUBLE NOT NULL," +
        "y DOUBLE NOT NULL," +
        "z DOUBLE NOT NULL," +
        "yaw FLOAT NOT NULL," +
        "pitch FLOAT NOT NULL," +
        "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
        "PRIMARY KEY (server_id, uuid, name)," +
        "KEY idx_uuid (uuid)" +
      ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"
    );

    jdbc.update(
      "CREATE TABLE IF NOT EXISTS orbis_ess_warps (" +
        "server_id VARCHAR(64) NOT NULL," +
        "name VARCHAR(32) NOT NULL," +
        "world_uuid BINARY(16) NOT NULL," +
        "x DOUBLE NOT NULL," +
        "y DOUBLE NOT NULL," +
        "z DOUBLE NOT NULL," +
        "yaw FLOAT NOT NULL," +
        "pitch FLOAT NOT NULL," +
        "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
        "PRIMARY KEY (server_id, name)" +
      ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"
    );

    jdbc.update(
      "CREATE TABLE IF NOT EXISTS orbis_ess_users (" +
        "server_id VARCHAR(64) NOT NULL," +
        "uuid BINARY(16) NOT NULL," +
        "last_world_uuid BINARY(16) NULL," +
        "last_x DOUBLE NULL," +
        "last_y DOUBLE NULL," +
        "last_z DOUBLE NULL," +
        "last_yaw FLOAT NULL," +
        "last_pitch FLOAT NULL," +
        "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
        "PRIMARY KEY (server_id, uuid)" +
      ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"
    );
  }
}
