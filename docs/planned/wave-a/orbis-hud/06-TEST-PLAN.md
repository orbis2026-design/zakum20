# Test Plan

Updated (UTC): 2026-02-15T11:37:13Z
Module: orbis-hud

## Unit Matrix
- `HudConfig.load` clamps interval, injects fallback profile, and trims oversized line sets.
- Profile resolution returns default when forced/unknown profile is missing.
- Placeholder replacement output remains deterministic for fixed player/server state.
- Command permission gates block unauthorized subcommands.

## Integration Matrix
- Plugin enable with Zakum present: service starts, listeners/command are registered.
- Plugin enable without Zakum: plugin disables cleanly with clear log reason.
- Reload path: scheduler is replaced exactly once; no orphan tasks remain.
- Join/quit lifecycle: player state is created and then cleared, scoreboard restored to main.

## Manual Matrix
- On Paper 1.21.11, verify `/orbishud status` values change correctly with player joins/leaves.
- Apply invalid config values and run `/orbishud reload`; confirm warning logs + fallback behavior.
- Force profile assignment and revert to default; verify HUD content updates immediately.
- Toggle spectator mode and confirm hide/show behavior per config.
- Execute repeated reload cycles (50+) and monitor console for task leaks/errors.
