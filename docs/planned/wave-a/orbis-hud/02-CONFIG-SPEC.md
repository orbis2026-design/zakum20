# Config Specification

Updated (UTC): 2026-02-15T11:37:13Z
Module: orbis-hud
Source of truth: `orbis-hud/src/main/resources/config.yml` and `HudConfig.load(...)`.

## Root Contract
- All keys are parsed on startup and on `/orbishud reload`.
- Invalid values are clamped or replaced with safe defaults.
- Missing/empty profile sets auto-create a fallback `default` profile.
- Config reload never hard-crashes plugin runtime.

## Key Schema
- `hud.enabled` (boolean, default: `true`)
  Behavior: master runtime toggle. If false, renderer clears player HUD and scheduler no-ops.
- `hud.updateIntervalTicks` (integer, default: `20`)
  Validation: clamped to `[5, 200]`.
  Behavior: periodic refresh interval.
- `hud.defaultProfile` (string, default: `default`)
  Validation: normalized to lowercase; if unknown, fallback to first valid profile with warning log.
- `hud.hideWhenInSpectator` (boolean, default: `true`)
  Behavior: sidebar hidden while player is spectator.
- `profiles` (map, required logically)
  Validation: if missing/empty, fallback profile generated.
- `profiles.<id>.title` (string, default: `&b&lORBIS`)
  Validation: blank -> default title; render-time truncate to scoreboard-safe length.
- `profiles.<id>.lines[]` (list of strings)
  Validation:
  - null/blank lines removed
  - if empty after sanitization, fallback lines injected
  - max 15 lines; extra lines trimmed with warning

Supported placeholders:
- `%player%`, `%display_name%`
- `%world%`, `%online%`, `%ping%`
- `%x%`, `%y%`, `%z%`
- `%health%`, `%max_health%`
- `%server_id%`, `%time%` (UTC)

## Validation Behavior
- Every correction path emits a warning (clamp/fallback/trim).
- Parser never throws fatal errors for malformed user content.
- Runtime always yields a resolvable default profile.
