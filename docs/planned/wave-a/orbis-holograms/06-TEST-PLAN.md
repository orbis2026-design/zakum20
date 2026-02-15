# Test Plan

Updated (UTC): 2026-02-15T11:37:37Z
Module: orbis-holograms

## Unit Matrix
- Config parser validation for ranges/enums/definition shape.
- Registry diff algorithm (add/update/remove).
- Placeholder substitution correctness for known tokens.

## Integration Matrix
- Spawn/despawn behavior across world load/unload and player movement.
- Reload cycles do not leak entities or listeners.
- Persistence write/read round-trip for mixed hologram definitions.

## Manual Matrix
- Create/edit/delete holograms live on Paper 1.21.11 and verify immediate updates.
- Stress with 100+ holograms and 30+ players to validate caps and tick budget.
- Validate hide-through-walls toggle behavior across common structures.
- Corrupt persistence file intentionally and verify fallback + warning behavior.
