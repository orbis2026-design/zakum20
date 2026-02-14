# Bridges (third‑party integrations)

Zakum emits **vanilla action events** out of the box (block break, movement, kills, etc).
Bridges add **plugin-specific action events** when the external plugin is installed.

All bridges are **reload-safe**:
- If the target plugin is missing, the bridge disables itself.
- They never hard-crash the server on missing classes.
- They publish to Zakum's `ActionBus` (Bukkit service).

---

## OrbisBridgeMythicMobs

**Softdepends:** MythicMobs  
**Action(s) emitted:**
- `custom_mob_kill` (`key=mythic`, `value=<internalMobName>`, `amount=1`)

This is designed for BattlePass quests like “kill 10 custom mobs”.

Example quest step:

```yml
- type: "custom_mob_kill"
  key: "mythic"
  value: "SkeletalKnight"
  required: 10
```

---

## OrbisBridgeJobs (Jobs Reborn)

**Softdepends:** Jobs  
**Config:** `plugins/OrbisBridgeJobs/config.yml`  
**Action(s) emitted:**
- `jobs_action` (`key=job_action`, `value=<jobName>:<actionType>`, `amount=1`)
- `jobs_money` (`key=job`, `value=<jobName>`, `amount=<scaled money>`)
- `jobs_exp` (`key=job`, `value=<jobName>`, `amount=<scaled exp>`)

Scaling preserves decimals by multiplying into a `long` (defaults: `100` => 2 decimals).

---

## OrbisBridgeSuperiorSkyblock2

**Softdepends:** SuperiorSkyblock2  
**Action(s) emitted:**
- `skyblock_island_create` (`key=schematic`, `value=<schematicName>`, `amount=1`)

Example quest step:

```yml
- type: "skyblock_island_create"
  key: "schematic"
  value: "*"
  required: 1
```
