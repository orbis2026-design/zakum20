# OSS Absorption Directive (2026-02-15)

This directive formalizes the network strategy:

- Do not replicate premium or widely adopted S-tier systems.
- Bridge proven open-source capabilities into Zakum.
- Focus custom engineering effort on orchestration, scripting, and UX cohesion.
- Target a 60-day delivery window with 500+ player operational stability.

## Non-Replication Policy

We will not rebuild mature public foundations when they are stronger than a 60-day custom rewrite:

- Anti-cheat: bridge to GrimAC
- Command trees: use CommandAPI
- Protocol backbone: PacketEvents
- Document/cache infra: MongoDB + Redis
- Text runtime: Adventure API

Competitive custom scope remains:

- ACE middleware (trigger/condition/target/effect)
- Capability composition and cross-module orchestration
- Orbis-specific UX, progression logic, and content pipelines

## 3-Dimensional Point Board

Dimensions:

- `[ST]` Stability and long-term uptime
- `[FR]` Feature richness
- `[BX]` Brand/experience

### Foundation and Ops

1. GrimAC + PacketEvents synergy (25 pts) `[ST:10]`
2. Hikari/Mongo/Redis hybrid storage (22 pts) `[ST:10]`
3. Virtual-thread worker pool for non-world I/O (20 pts) `[ST:10]`

### ACE and Animation Web

4. ACE saturation to 50+ effects (25 pts) `[FR:10]`
5. Packet-based crate/pet animator (20 pts) `[FR:10]`
6. GUI bridge saturation and script triggers (18 pts) `[FR:9]`

### Orbis Identity

7. Localization + pre-serialized packet buffer engine (15 pts) `[BX:10]`
8. Gameplay script absorption into ACE configs (15 pts) `[BX:10]`

## Point 7 Implementation Status

Status: **completed in current pass**

Implemented in core:

- `LocalizedChatPacketBuffer` warmup now preloads all known template locales
  (default + configured supported + template-declared locales).
- Prepared cache invalidation now clears message/json/packet prepared caches together.
- Warmup telemetry is tracked (`warmupRuns`, `lastWarmupEntries`, `lastWarmupDurationMs`).
- Chat buffer admin status output now exposes JSON/packet cache sizes and warmup telemetry.
- `ZakumApi` now exposes `chatBuffer()` default accessor for capability-first module usage.

This keeps the architecture aligned with the policy:

- no custom chat plugin clone,
- Adventure + PacketEvents backbone retained,
- Zakum provides a high-performance, localized packet message pipeline as a capability.
