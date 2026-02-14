# OrbisBattlePass Configuration

Config folder: `plugins/OrbisBattlePass/`

Files:
- `config.yml` (scope, season/week, flush intervals, leaderboard, NPC menus)
- `quests.yml` (quest definitions)
- `rewards.yml` (tier thresholds + reward tracks)

`config.yml` keys:
- `battlepass.premiumScope`: `SERVER` or `GLOBAL` (network-wide entitlement)
- `battlepass.premiumEntitlementKey`: entitlement key (default `battlepass_premium`)
- `battlepass.progressServerIdOverride`: force a custom scope key (optional)
- `battlepass.timezone`: timezone for daily/weekly rollover
- `battlepass.weeks.current`: current week (for planned weekly quests)
- `battlepass.seasons.current`: current season number
- `battlepass.seasons.resetOnSeasonChange`: reset progress when season changes
- `battlepass.flush.intervalSeconds`: flush deltas to DB
- `battlepass.premiumRefresh.intervalSeconds`: refresh premium state for online players
- `battlepass.leaderboard.refreshSeconds`: async refresh cadence
- `battlepass.leaderboard.maxEntries`: size of cached snapshot
- `battlepass.npcMenus.enabled`: enable NPC menu opens
- `battlepass.npcMenus.openMainNpcIds`: Citizens NPC ids/names that open the main menu

Gameplay-facing:
- `/battlepass` opens GUI
- `/battlepass claim|claimall` for reward claiming
- `/battlepass top` shows leaderboard (chat)


---
*Development Note: Edit this module using IntelliJ IDEA with Gradle Sync enabled.*