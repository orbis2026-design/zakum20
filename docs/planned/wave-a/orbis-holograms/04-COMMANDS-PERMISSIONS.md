# Commands & Permissions

Updated (UTC): 2026-02-15T11:37:37Z
Module: orbis-holograms

## Command Policy
- All mutating commands are admin-scoped.
- Command execution must return entity id + operation result.
- Bulk operations (reload/import) must log summary counts.

## Baseline Permissions
- `orbisholograms.admin` (default: op)
- `orbisholograms.create` (default: op)
- `orbisholograms.edit` (default: op)
- `orbisholograms.delete` (default: op)
- `orbisholograms.reload` (default: op)
- `orbisholograms.teleport` (default: op)

## Planned Command Inventory
- `/orbishologram list [world]`
- `/orbishologram create <id> [world] [x y z]`
- `/orbishologram move <id> [x y z]`
- `/orbishologram line set <id> <index> <text...>`
- `/orbishologram line add <id> <text...>`
- `/orbishologram line remove <id> <index>`
- `/orbishologram tp <id>`
- `/orbishologram delete <id>`
- `/orbishologram reload`

## Audit Requirement
- Final `plugin.yml` and implementation must keep one-to-one command/permission parity.
- No direct dependencies on non-Zakum feature modules.
