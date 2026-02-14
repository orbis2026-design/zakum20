# Refactor Directive Integration Spec

Date: 2026-02-14

This document translates the legacy fork directive into implementation-ready
requirements for the current Zakum codebase.

## Target baseline

- Java: 21
- Paper API: `1.21.11-R0.1-SNAPSHOT`
- PacketEvents: `2.11.x`

## API contracts to add (next milestones)

Planned under `zakum-api`:

- `net.orbis.zakum.api.ace.AceEngine`
- `net.orbis.zakum.api.progression.ProgressionService`
- `net.orbis.zakum.api.animation.AnimationService`
- `net.orbis.zakum.api.ui.GuiBridge`
- `net.orbis.zakum.api.concurrent.ZakumScheduler`

`ZakumApi` extension plan:
- Keep current methods for backward compatibility.
- Add typed accessors for the new services.
- Register each as optional capability using `CapabilityRegistry`.

## Scheduler standard

All world mutation must run on region/entity schedulers:

- `runAsync(Runnable)` for pure async work only
- `runAtLocation(Location, Runnable)` for location-scoped world work
- `runAtEntity(Entity, Runnable)` for entity-scoped world work

No Bukkit world mutation on virtual-thread executor.

## ACE engine standard

Core parser/runtime will support:

- effect token: `[EFFECT_KEY]`
- target token: `@SELF|@VICTIM|@NEARBY|...`
- params/value segment as key/value map
- namespaced keys for module effects:
  - `PET_*`
  - `CRATE_*`
  - `BP_*`
  - custom plugin namespaces

Feature modules may register effects but should not implement duplicate parsers.

## Animation standard

`AnimationService` provides:

- packet-backed one-viewer animations
- native Display-entity animations
- shared API contract for crates/pets/cosmetics

Implementation must isolate PacketEvents internals from feature modules.

## PDC migration standard

Item identity key:
- namespace: `zakum`
- key: `id`

All lore-based identity checks are migration targets.

## Compatibility constraints

- Do not hard-couple feature modules to `zakum-core` classes.
- Keep bridge integrations in `zakum-bridge-*` modules.
- Preserve SemVer policy for `zakum-api` additions/deprecations.
