# Commands & Permissions

Updated (UTC): 2026-02-15T11:38:29Z
Module: orbis-worlds

## Command Policy
- Destructive operations require explicit confirmation flow and admin permission.
- Teleport and state-change commands must log actor, target world, and result.
- Async-heavy operations return progress/status feedback to caller.

## Baseline Permissions
- `orbisworlds.admin` (default: op)
- `orbisworlds.reload` (default: op)
- `orbisworlds.create` (default: op)
- `orbisworlds.import` (default: op)
- `orbisworlds.delete` (default: op)
- `orbisworlds.load` (default: op)
- `orbisworlds.unload` (default: op)
- `orbisworlds.teleport` (default: op)

## Planned Command Inventory
- `/orbisworld list`
- `/orbisworld info <world>`
- `/orbisworld create <id> [environment] [seed]`
- `/orbisworld import <id> <folder>`
- `/orbisworld load <id>`
- `/orbisworld unload <id>`
- `/orbisworld tp <world> [player]`
- `/orbisworld setspawn <world>`
- `/orbisworld delete <id> [confirm]`
- `/orbisworld reload`

## Audit Requirement
- `plugin.yml` command/permission declarations remain aligned with implementation.
- Deletion and import operations require explicit safeguards to prevent accidental data loss.
