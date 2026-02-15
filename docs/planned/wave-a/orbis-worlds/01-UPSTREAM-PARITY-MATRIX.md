# Upstream Parity Matrix

Updated (UTC): 2026-02-15T11:38:29Z
Module: orbis-worlds
Primary upstream references: Multiverse-Core world lifecycle and teleport administration.

## Critical Parity (Must-Have v1)
- World registry with create/import/load/unload/delete operations.
- Teleport routing with safe-location resolution.
- Persistent world metadata (environment, seed, generator, flags).
- Command and permission model for operator world control.
- Reload-safe reconciliation of configured worlds vs loaded runtime worlds.
- Explicit compatibility with Paper 1.21.11 world APIs.

## Optional Parity (Post-v1)
- Portal graph and cross-world routing UI.
- Per-world weather/time automation profiles.
- World templates with snapshot/clone tooling.
- Region-aware spawn rules and phased world states.
- Cross-server world metadata sync.

## Gap Notes
- Wave A excludes portal and clone subsystems by design.
- Core target is predictable world lifecycle safety first.
