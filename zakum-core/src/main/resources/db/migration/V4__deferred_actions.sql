CREATE TABLE IF NOT EXISTS zakum_deferred_actions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    server_id VARCHAR(32),
    player_name_lc VARCHAR(32) NOT NULL,
    type VARCHAR(64) NOT NULL,
    amount BIGINT DEFAULT 0,
    k VARCHAR(255) DEFAULT '',
    v TEXT,
    source VARCHAR(64),
    expires_at BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_zakum_deferred_fetch (server_id, player_name_lc, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
