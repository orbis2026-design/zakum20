# Plugin Ecosystem Consolidation

Updated (UTC): 2026-02-15T11:24:20Z

## Scope
This document consolidates planned module execution into phased waves so multiple agents can work in parallel without stepping on API boundaries.

## Wave A Modules
- orbis-hud
- orbis-holograms
- orbis-loot
- orbis-worlds

## Governance Rules
- Feature modules compile against zakum-api only.
- No net.orbis.zakum.core.* imports in feature code.
- External ecosystem integrations live in zakum-bridge-* modules.
- Planning docs are additive and timestamped in UTC.

## Wave A Deliverables
- Parity matrix per module.
- Full config spec per module.
- Commands/permissions matrix per module.
- Test plan per module.
- Upstream research manifests completed and marked research-complete.