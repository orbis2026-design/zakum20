# Command System (CommandAPI Bridge)

Zakum supports two command layers:

1) Fallback command (plugin.yml) in `zakum-core`
   - minimal, always available

2) CommandAPI bridge (`zakum-bridge-commandapi`)
   - rich suggestions + typed arguments
   - install only if you want CommandAPI UX

## Bridge plugin

Plugin: `ZakumBridgeCommandAPI`

- depends on: `Zakum`, `CommandAPI`
- owns: `/zakum ...`
- removes the fallback `/zakum` registration best-effort to avoid duplicates

## Current command tree (bridge)

- `/zakum status`
- `/zakum packets status`
- `/zakum entitlements check|grant|revoke|invalidate ...`
- `/zakum boosters multiplier|grant_all|grant_player ...`

All DB operations run async and reply sync to the sender.

## Why this matters

Command spam can kill performance if you:
- query DB synchronously
- allocate heavily per command
- do long formatting on main thread

The bridge keeps commands lightweight and typed.
