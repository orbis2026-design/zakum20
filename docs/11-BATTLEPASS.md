# BattlePass (OrbisBattlePass)

This module is the **AdvancedTeam-style BattlePass** foundation, built on Zakum services.

## Storage scope (critical)

BattlePass progress is stored under:

- `server_id = battlepass.progressServerIdOverride` if configured, otherwise `ZakumSettings.server.id`
- `season = battlepass.seasons.current`

This lets each backend server have **independent BattlePasses**, even if servers launch at different times.

Premium can be evaluated:
- **SERVER** scope (default): premium applies per `server_id`
- **NETWORK** scope: premium is global (Velocity-style entitlement)

Config:
```yml
battlepass:
  premiumScope: "SERVER"  # SERVER or GLOBAL/NETWORK
  premiumEntitlementKey: "battlepass_premium"
  progressServerIdOverride: ""
```

## Quests

Quests live in `plugins/OrbisBattlePass/quests.yml` (operator-editable).

Each quest has:
- `cadence`: SEASON | DAILY | WEEKLY
- `availableWeeks`: optional, only for WEEKLY (planned week schedules)
- `steps`: multi-step objective list

Actions arrive from Zakum `ActionBus` as:
- `type` (string)
- `amount` (long)
- `key` / `value` (string filters)

A step matches if:
- `type` matches (case-insensitive)
- optional `key` and `value` match if set
- `progress += amount` until `required`

## Points and tiers

Completing a quest grants points:
- `points`
- + `premiumBonusPoints` if premium and configured

Boosters:
- `BATTLEPASS_PROGRESS` boosts action amounts
- `BATTLEPASS_POINTS` boosts points granted

Tier is derived from `rewards.yml` thresholds using binary search:
- `tier = max tier where points >= pointsRequired`

## Rewards + claiming

Rewards live in `plugins/OrbisBattlePass/rewards.yml`.

v1 reward types:
- `COMMAND`: console command list

Claiming:
- Free track is always claimable once tier reached.
- Premium track requires premium at claim-time.
- Claims are insert-only and persisted to `orbis_battlepass_claims`.

Commands:
- `/battlepass` (shows your tier/points)
- `/battlepass claim <tier> [free|premium|both]`
- `/battlepass claimall`

Admin:
- `/battlepass status`
- `/battlepass reload`
- `/battlepass givepoints <player> <amount>`

## Tables

- `orbis_battlepass_progress`
- `orbis_battlepass_step_progress`
- `orbis_battlepass_periods` (daily/weekly rollover markers)
- `orbis_battlepass_claims` (reward claims)

## Scale notes

- Action processing is main-thread, but work is bounded:
  - candidate quest filtering via QuestIndex
  - tier calculation is O(log tiers)
- DB writes are delta-based and chunked.
- Claims are insert-only and chunked.


## GUI

Players can open the menu:
- `/battlepass` (default opens menu)
- `/battlepass menu`

Pages:
- Rewards page shows tier rewards, and supports click-to-claim:
  - Left click: claim free
  - Right click: claim premium
  - Shift+Left: claim both
- Quests page lists quest progress.

## Leaderboard

- `/battlepass top [page]`

Leaderboard is refreshed async and cached:
```yml
battlepass:
  leaderboard:
    refreshSeconds: 30
    maxEntries: 250
```

## PlaceholderAPI

If PlaceholderAPI is installed, OrbisBattlePass registers:
- `%orbisbp_tier%`
- `%orbisbp_points%`
- `%orbisbp_premium%`
- `%orbisbp_season%`
- `%orbisbp_server%`

Quest placeholders:
- `%orbisbp_quest_<questId>_step%`
- `%orbisbp_quest_<questId>_progress%`
- `%orbisbp_quest_<questId>_required%`


---
*Development Note: Edit this module using IntelliJ IDEA with Gradle Sync enabled.*