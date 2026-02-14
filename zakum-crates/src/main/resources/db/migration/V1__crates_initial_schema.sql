CREATE TABLE IF NOT EXISTS orbis_crates_keys (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    server_id VARCHAR(32) NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    crate_id VARCHAR(32) NOT NULL,
    quantity INT DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_player_crate (server_id, player_uuid, crate_id),
    INDEX idx_player (player_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS orbis_crates_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    crate_id VARCHAR(32) NOT NULL,
    reward_id VARCHAR(64) NOT NULL,
    opened_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_history_player (player_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
