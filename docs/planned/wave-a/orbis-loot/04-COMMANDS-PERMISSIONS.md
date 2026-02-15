# Commands & Permissions

Updated (UTC): 2026-02-15T11:38:04Z
Module: orbis-loot

## Command Policy
- Mutating and grant commands require explicit permission nodes.
- Commands touching balances/keys must produce auditable console logs.
- Simulation commands are required for balancing workflows.

## Baseline Permissions
- `orbisloot.admin` (default: op)
- `orbisloot.reload` (default: op)
- `orbisloot.key.give` (default: op)
- `orbisloot.key.take` (default: op)
- `orbisloot.open` (default: true or crate-scoped override)
- `orbisloot.simulate` (default: op)

## Planned Command Inventory
- `/orbisloot reload`
- `/orbisloot crates list`
- `/orbisloot key give <player> <crate> <amount>`
- `/orbisloot key take <player> <crate> <amount>`
- `/orbisloot open <crate> [player]`
- `/orbisloot simulate <crate> <rolls>`
- `/orbisloot debug crate <id>`

## Audit Requirement
- Inventory of commands/permissions must mirror final `plugin.yml`.
- Any bridge-dependent command paths must fail gracefully if bridge unavailable.
