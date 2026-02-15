# Upstream Parity Matrix

Updated (UTC): 2026-02-15T11:38:04Z
Module: orbis-loot
Primary upstream references: ExcellentCrates, open-source weighted reward systems.

## Critical Parity (Must-Have v1)
- Crate definitions with weighted reward pools.
- Key system (virtual + physical item key support).
- Open flow with permission + key validation + cooldown checks.
- Reward execution via ACE-compatible action dispatch.
- Transaction-safe reward and key mutations.
- Command surface for give/open/reload/simulate.
- Config reload with definition validation and fallback-safe behavior.

## Optional Parity (Post-v1)
- Full cinematic crate animations.
- Pity timer and bad-luck protection tuning UI.
- Region-restricted crate stations and hologram cues.
- Dynamic economy pricing hooks for key purchases.
- Seasonal crate rotations synchronized across servers.

## Gap Notes
- v1 must prioritize reliable reward/key correctness over visual animation fidelity.
- External economy integrations remain bridge-owned, not baked into feature module.
