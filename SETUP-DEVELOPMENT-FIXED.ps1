# ============================================================================
# ZAKUM SUITE - RESOURCE FIXER
# Generates missing config and SQL files for Zakum Crates
# ============================================================================

$PROJECT_ROOT = $PSScriptRoot
$CRATES_RES = "$PROJECT_ROOT\zakum-crates\src\main\resources"

# Ensure directories exist
New-Item -ItemType Directory -Path "$CRATES_RES\db\migration" -Force | Out-Null

# 1. Generate config.yml
$configYml = @'
# Zakum Crates Configuration
settings:
  storage:
    table-prefix: "orbis_crates_"
  
  gui:
    title: "Global Crates"
    rows: 3
    fill-item: GRAY_STAINED_GLASS_PANE

crates:
  vote:
    name: "&a&lVote Crate"
    type: ROULETTE
    preview: true
    block: ENDER_CHEST
    key:
      material: TRIPWIRE_HOOK
      name: "&aVote Key"
      lore:
        - "&7Use this at the crate area"
        - "&7to win rewards!"
      glow: true
  
  rare:
    name: "&6&lRare Crate"
    type: CSGO
    preview: true
    block: TRAPPED_CHEST
    key:
      material: BLAZE_ROD
      name: "&6Rare Key"
      glow: true
'@
Set-Content -Path "$CRATES_RES\config.yml" -Value $configYml -Encoding UTF8
Write-Host "✓ Created config.yml" -ForegroundColor Green

# 2. Generate rewards.yml
$rewardsYml = @'
rewards:
  vote_diamond:
    crate: vote
    chance: 50.0
    display:
      material: DIAMOND
      name: "&b2x Diamonds"
    commands:
      - "give %player% diamond 2"
  
  vote_cash:
    crate: vote
    chance: 20.0
    display:
      material: PAPER
      name: "&a$1000 Cash"
    commands:
      - "eco give %player% 1000"

  rare_sword:
    crate: rare
    chance: 5.0
    display:
      material: DIAMOND_SWORD
      name: "&6Legendary Sword"
      enchanted: true
    items:
      - material: DIAMOND_SWORD
        name: "&6Legendary Sword"
        enchantments:
          DAMAGE_ALL: 5
'@
Set-Content -Path "$CRATES_RES\rewards.yml" -Value $rewardsYml -Encoding UTF8
Write-Host "✓ Created rewards.yml" -ForegroundColor Green

# 3. Generate SQL Schema
$sqlSchema = @'
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
'@
Set-Content -Path "$CRATES_RES\db\migration\V1__crates_initial_schema.sql" -Value $sqlSchema -Encoding UTF8
Write-Host "✓ Created V1__crates_initial_schema.sql" -ForegroundColor Green

Write-Host ""
Write-Host "Resources repaired. You can now build the project." -ForegroundColor Cyan