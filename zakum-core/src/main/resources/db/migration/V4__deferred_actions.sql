CREATE TABLE IF NOT EXISTS zakum_deferred_actions (
  id BIGINT NOT NULL AUTO_INCREMENT,

  server_id VARCHAR(64) NULL,
  player_name_lc VARCHAR(16) NOT NULL,

  type VARCHAR(64) NOT NULL,
  amount BIGINT NOT NULL,
  k VARCHAR(64) NOT NULL,
  v VARCHAR(128) NOT NULL,

  source VARCHAR(64) NULL,
  expires_at BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

  PRIMARY KEY (id),
  KEY idx_player (player_name_lc),
  KEY idx_server_player (server_id, player_name_lc),
  KEY idx_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
