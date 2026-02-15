# Upstream Parity Matrix

Updated (UTC): 2026-02-15T11:37:37Z
Module: orbis-holograms
Primary upstream references: DecentHolograms, lightweight DisplayEntity runtimes.

## Critical Parity (Must-Have v1)
- CRUD lifecycle for holograms (create, move, edit lines, delete).
- Persistent storage for hologram definitions on disk with atomic save behavior.
- Runtime spawn/despawn by viewer distance and world load state.
- Placeholder replacement for core tokens (`%player%`, `%world%`, `%server_id%`).
- Reload-safe registry rebuild without duplicate entities.
- Permissioned command surface for operators.
- Compatibility with Paper 1.21.11 Display entities.

## Optional Parity (Post-v1)
- Per-line animation timelines and transitions.
- Click interactions and action dispatch integration.
- Packet-only hologram mode for high population shards.
- Condition-based visibility rules (permission, region, quest state).
- Remote hologram sync via Redis events.

## Gap Notes
- Implementation not started; this document defines first executable parity target.
- Upstream animation breadth is intentionally excluded from Wave A critical path.
