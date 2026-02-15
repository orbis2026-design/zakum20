# Commands & Permissions

Updated (UTC): 2026-02-15T11:37:13Z
Module: orbis-hud

## Command Policy
- Operator commands must be explicit, idempotent where possible, and provide actionable error text.
- Subcommands are permission-scoped; `orbishud.admin` provides global override.
- Player-targeting commands operate on online players only in v1.

## Baseline Permissions
- `orbishud.admin` (default: op)
  Scope: full access + override for subcommand checks.
- `orbishud.reload` (default: op)
  Scope: `/orbishud reload` only.
- `orbishud.profile.set` (default: op)
  Scope: `/orbishud profile <player> <profile|default>`.

## Command Inventory
- `/orbishud status`
  Permission: `orbishud.admin`
  Output: runtime state, profile counts, tracked/online players, scheduler task id.
- `/orbishud reload`
  Permission: `orbishud.reload`
  Output: reload success + profile count after parse.
- `/orbishud profile <player> <profile>`
  Permission: `orbishud.profile.set`
  Output: profile assignment result; available profile list on validation failure.

## Audit Requirement
- `plugin.yml` command and permission declarations must match runtime checks exactly.
- Tab completion should surface only valid subcommands/profiles for operator workflows.
