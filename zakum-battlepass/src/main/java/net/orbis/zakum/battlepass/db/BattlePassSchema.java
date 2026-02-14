package net.orbis.zakum.battlepass.db;

import net.orbis.zakum.api.db.Jdbc;

/**
 * v1 bootstrap: create BattlePass tables if they do not exist.
 *
 * Migration strategy:
 * - v1 uses CREATE IF NOT EXISTS so you can ship without manual DB steps.
 * - Later: move to Flyway with strict versioned migrations.
 */
public final class BattlePassSchema {

  private BattlePassSchema() {}

  public static void ensureTables(Jdbc jdbc) {
    jdbc.update("""
      CREATE TABLE IF NOT EXISTS orbis_battlepass_progress (
        server_id VARCHAR(64) NOT NULL,
        season INT NOT NULL,
        uuid BINARY(16) NOT NULL,
        tier INT NOT NULL DEFAULT 0,
        points BIGINT NOT NULL DEFAULT 0,
        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        PRIMARY KEY (server_id, season, uuid),
        KEY idx_season_points (server_id, season, points)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
      """);

    jdbc.update("""
      CREATE TABLE IF NOT EXISTS orbis_battlepass_step_progress (
        server_id VARCHAR(64) NOT NULL,
        season INT NOT NULL,
        uuid BINARY(16) NOT NULL,
        quest_id VARCHAR(64) NOT NULL,
        step_idx INT NOT NULL DEFAULT 0,
        progress BIGINT NOT NULL DEFAULT 0,
        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        PRIMARY KEY (server_id, season, uuid, quest_id),
        KEY idx_quest (server_id, season, quest_id)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
      """);

    jdbc.update("""
      CREATE TABLE IF NOT EXISTS orbis_battlepass_entitlements (
        -- If premiumScope=GLOBAL, use server_id='GLOBAL'
        server_id VARCHAR(64) NOT NULL,
        uuid BINARY(16) NOT NULL,
        premium BOOLEAN NOT NULL DEFAULT FALSE,
        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        PRIMARY KEY (server_id, uuid)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
      """);
    jdbc.update("""
      CREATE TABLE IF NOT EXISTS orbis_battlepass_claims (
        server_id VARCHAR(64) NOT NULL,
        season INT NOT NULL,
        uuid BINARY(16) NOT NULL,
        tier INT NOT NULL,
        track VARCHAR(16) NOT NULL,
        claimed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (server_id, season, uuid, tier, track),
        KEY idx_player (server_id, season, uuid)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
      """);



    jdbc.update("""
      CREATE TABLE IF NOT EXISTS orbis_battlepass_periods (
        server_id VARCHAR(64) NOT NULL,
        season INT NOT NULL,
        uuid BINARY(16) NOT NULL,
        daily_day BIGINT NOT NULL DEFAULT 0,
        weekly_week BIGINT NOT NULL DEFAULT 0,
        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        PRIMARY KEY (server_id, season, uuid)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
      """);

    // Backup batches + archives (ops safety for season rollovers).
    jdbc.update("""
      CREATE TABLE IF NOT EXISTS orbis_battlepass_backup_batches (
        batch_id BIGINT NOT NULL,
        server_id VARCHAR(64) NOT NULL,
        season INT NOT NULL,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        created_by VARCHAR(64) NULL,
        note VARCHAR(255) NULL,
        status VARCHAR(16) NOT NULL DEFAULT 'RUNNING',
        error TEXT NULL,
        PRIMARY KEY (batch_id),
        KEY idx_server_season (server_id, season, created_at)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
      """);

    jdbc.update("""
      CREATE TABLE IF NOT EXISTS orbis_battlepass_progress_archive (
        id BIGINT NOT NULL AUTO_INCREMENT,
        batch_id BIGINT NOT NULL,
        archived_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        server_id VARCHAR(64) NOT NULL,
        season INT NOT NULL,
        uuid BINARY(16) NOT NULL,
        tier INT NOT NULL,
        points BIGINT NOT NULL,
        updated_at TIMESTAMP NULL,
        PRIMARY KEY (id),
        KEY idx_batch (batch_id),
        KEY idx_player (server_id, season, uuid)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
      """);

    jdbc.update("""
      CREATE TABLE IF NOT EXISTS orbis_battlepass_step_progress_archive (
        id BIGINT NOT NULL AUTO_INCREMENT,
        batch_id BIGINT NOT NULL,
        archived_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        server_id VARCHAR(64) NOT NULL,
        season INT NOT NULL,
        uuid BINARY(16) NOT NULL,
        quest_id VARCHAR(64) NOT NULL,
        step_idx INT NOT NULL,
        progress BIGINT NOT NULL,
        updated_at TIMESTAMP NULL,
        PRIMARY KEY (id),
        KEY idx_batch (batch_id),
        KEY idx_player (server_id, season, uuid),
        KEY idx_quest (server_id, season, quest_id)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
      """);

    jdbc.update("""
      CREATE TABLE IF NOT EXISTS orbis_battlepass_claims_archive (
        id BIGINT NOT NULL AUTO_INCREMENT,
        batch_id BIGINT NOT NULL,
        archived_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        server_id VARCHAR(64) NOT NULL,
        season INT NOT NULL,
        uuid BINARY(16) NOT NULL,
        tier INT NOT NULL,
        track VARCHAR(16) NOT NULL,
        claimed_at TIMESTAMP NULL,
        PRIMARY KEY (id),
        KEY idx_batch (batch_id),
        KEY idx_player (server_id, season, uuid)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
      """);

    jdbc.update("""
      CREATE TABLE IF NOT EXISTS orbis_battlepass_periods_archive (
        id BIGINT NOT NULL AUTO_INCREMENT,
        batch_id BIGINT NOT NULL,
        archived_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        server_id VARCHAR(64) NOT NULL,
        season INT NOT NULL,
        uuid BINARY(16) NOT NULL,
        daily_day BIGINT NOT NULL,
        weekly_week BIGINT NOT NULL,
        updated_at TIMESTAMP NULL,
        PRIMARY KEY (id),
        KEY idx_batch (batch_id),
        KEY idx_player (server_id, season, uuid)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
      """);
  }
}
