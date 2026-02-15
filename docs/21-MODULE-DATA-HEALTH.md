# Module Data Health Probes

Purpose
- Validate cross-module SQL posture from Zakum core without deploying module-specific diagnostics plugins.
- Execute schema/version and read/write probes for installed Orbis modules.

Config
- `operations.dataHealthProbes.enabled`
- `operations.dataHealthProbes.includeWriteProbe`

Command surface
- `/zakum datahealth status` (summary counters)
- `/zakum datahealth modules` (detailed probe rows)

Probe states
- `PASS`: read probe succeeded (and write probe succeeded when enabled).
- `FAIL`: probe query/update failed.
- `SKIPPED`: required plugin for that probe is not enabled.

Current probe set
- `core.flyway_schema`
- `core.deferred_actions`
- `core.entitlements`
- `core.boosters`
- `battlepass.progress` (OrbisBattlePass)
- `crates.keys` (OrbisCrates)
- `pets.player` (OrbisPets)
- `minipets.player` (OrbisMiniPets)
- `essentials.users` (OrbisEssentials)

Notes
- Write probes are safe no-op updates (`... WHERE 1=0`) to validate DML path without mutating rows.
- Detailed probe output includes latency per probe and plugin gating context.
