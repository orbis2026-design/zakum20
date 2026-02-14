# Core/API Foundation Plan (100 Points)

This roadmap defines the implementation order for a future-proof Zakum core/API
that can support the full plugin ecosystem on Paper/Spigot 1.21.11 with Java 21.

## Point-scored execution order

1. **22 pts** - API contract freeze + capability registry + semver policy
2. **18 pts** - Scheduler/storage foundation (async + migration boundaries + bounded queues)
3. **16 pts** - Action/ability DSL kernel (trigger/condition/target/effect)
4. **14 pts** - Bridge SDK + Tier-A bridges
5. **10 pts** - Progression/reward primitives
6. **8 pts** - Item/model/entity abstraction layer
7. **6 pts** - Chat/moderation + typed command stack
8. **4 pts** - API compatibility tests + integration harness
9. **2 pts** - Developer docs/examples and release templates

## Milestone 1 (22 pts) status

Status: **complete**

Completed in this pass:
- Added `Capability`, `CapabilityRegistry`, and standard `ZakumCapabilities` keys in `zakum-api`.
- Extended `ZakumApi` with `capabilities()` + convenience `capability(...)`.
- Wired core runtime lookup via `ServicesManagerCapabilityRegistry` in `zakum-core`.
- Registered `CapabilityRegistry` as a Bukkit service.

Remaining for milestone 1:
- Add API compatibility checks in CI (binary + source compatibility gates).

## API compatibility policy (SemVer)

- `zakum-api` follows SemVer.
- **MAJOR**: breaking API or behavioral contract changes.
- **MINOR**: backward-compatible additions (new interfaces/default methods/capabilities).
- **PATCH**: bug fixes and non-breaking internal improvements.

Deprecation policy:
- Any public API removal must be deprecated for at least one MINOR release first.
- Deprecated APIs must include replacement guidance in Javadoc.
- Removal notes must be listed in release notes under `Breaking Changes`.

Compatibility rules:
- Feature plugins must compile against `zakum-api` only, never `zakum-core`.
- Integrations with external plugins must live in bridge modules (`zakum-bridge-*`).
- Optional integrations are discovered via `CapabilityRegistry` or Bukkit ServicesManager.

## Legacy Fork Directive Integration (Feb 14, 2026)

Source directive: `Zakum Core Refactor Directive v1.0` (fork of older source).

### Adopt directly

- ACE-style script engine as a first-class core capability.
- Progression math as shared service (tiers/xp/level/prestige), not per-plugin duplication.
- GUI abstraction (`GuiBridge`) so feature modules stop binding to one UI renderer.
- PDC-first item identity (`NamespacedKey("zakum","id")`) across items/enchantments/pets/crates.
- Java 21-first modeling (records for DTO snapshots, sealed/typed contracts where useful).

### Adopt with adaptation for current target

- Scheduler abstraction:
  - keep `runAsync`, but world/entity mutations must route through Paper region/entity scheduler.
  - add `runAtLocation` and `runAtEntity` in API contract.
- Packet/display animations:
  - keep packet animation service, but expose it as capability-backed abstraction.
  - support both packet-only preview mode and native Display-entity mode.
- ACE module compaction:
  - move reusable effect logic into core engine using namespaced effect keys.
  - keep module-specific orchestration in each feature plugin.

### Reject or supersede from legacy directive

- Do not downgrade dependency lines to older versions from the fork:
  - remain on `paper-api:1.21.11-R0.1-SNAPSHOT` (not `1.21.1`).
  - remain on modern PacketEvents line in this repo (`2.11.x`, not `2.4.0`).
- Do not replace Bukkit `ServicesManager` integration with global static-only access.
  - service resolution remains primary; static accessors are optional convenience wrappers only.

### Impact on next milestones

- Milestone 2 (18 pts): includes `ZakumScheduler` API + Paper/Folia-safe implementation.
- Milestone 3 (16 pts): includes `AceEngine` + namespaced effect registry + parser/runtime.
- Milestone 5 (10 pts): includes `ProgressionService`.
- Milestone 6 (8 pts): includes `AnimationService` and `GuiBridge` contracts.
- Cross-cutting: PDC migration standard applies to all item-identification systems.
