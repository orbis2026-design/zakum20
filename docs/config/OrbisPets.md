# OrbisPets Configuration

Config folder: `plugins/OrbisPets/`

Files:
- `config.yml`

Keys:
- `pets.<id>.name`
- `pets.<id>.entity`
- `pets.<id>.followMode`: `AI` or other modes supported by runtime
- `pets.<id>.xpPerMobKill`
- `levels.maxLevel`, `levels.xpBase`, `levels.xpGrowth`
- `flush.intervalSeconds`

Notes:
- DB-backed persistence (degrades if DB offline).
