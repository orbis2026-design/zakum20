# Upstream Parity Matrix

Updated (UTC): 2026-02-15T11:37:13Z
Module: orbis-hud
Primary upstream references: TAB (NEZNAMY), scoreboard-focused HUD patterns.

## Critical Parity (Must-Have v1)
- Sidebar HUD with profile-based title/line templates.
- Deterministic placeholder replacement (`%player%`, `%world%`, `%online%`, `%ping%`, coordinates, health, `%server_id%`, `%time%`).
- Per-player profile override (`/orbishud profile <player> <profile>`).
- Reload-safe config parsing with fallback default profile and line sanitization.
- Stable runtime lifecycle: join/quit hooks, task restart on reload, cleanup on disable.
- Spectator-mode suppression support (`hud.hideWhenInSpectator`).
- Signature-based no-op rendering to avoid unnecessary scoreboard writes.
- Strict boundary compliance: depends on `zakum-api` only.

## Optional Parity (Post-v1)
- Per-world or per-gamemode automatic profile routing.
- Packet-level message buffering for HUD text fragments.
- PlaceholderAPI expansion via dedicated bridge module.
- Animated scoreboards, conditional lines, and profile inheritance.
- Cross-server persisted HUD preferences (Redis-backed).

## Gap Notes
- v1 code currently covers all listed critical parity items.
- Optional parity remains intentionally deferred to Wave B to protect 60-day delivery scope.
