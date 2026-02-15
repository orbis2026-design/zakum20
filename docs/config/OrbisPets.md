# OrbisPets Configuration

Config folder: `plugins/OrbisPets/`

Files:
- `config.yml`

Keys:
- `pets.<id>.name`
- `pets.<id>.entity`
- `pets.<id>.followMode`: `AI` or other modes supported by runtime
- `pets.<id>.xpPerMobKill`
- `pets.<id>.summonScript[]`
- `pets.<id>.dismissScript[]`
- `pets.<id>.levelUpScript[]`
- `levels.maxLevel`, `levels.xpBase`, `levels.xpGrowth`
- `flush.intervalSeconds`

Notes:
- DB-backed persistence (degrades if DB offline).
- Lifecycle script placeholders include:
- `{pet_id}`, `{pet_name}`, `{pet_level}`, `{pet_xp}`


---
*Development Note: Edit this module using IntelliJ IDEA with Gradle Sync enabled.*
