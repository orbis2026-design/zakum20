# Packet Culling Validation Runbook

Goal: verify outbound packet culling is active, respects perf-mode, and has safe fallbacks.

Prereqs
- Paper 1.21.11 (or target server version)
- PacketEvents plugin installed
- ZakumPackets plugin installed
- Zakum core configured with packets.enabled=true and packets.backend=PACKETEVENTS

Config quick-check (zakum-core config.yml)
- packets.enabled: true
- packets.backend: PACKETEVENTS
- packets.outbound: true
- packets.culling.enabled: true
- packets.culling.packetNames: includes WORLD_PARTICLES / ENTITY_METADATA / ENTITY_EFFECT / ENTITY_ANIMATION
- packets.culling.respectPerfMode: true (default)
- packets.culling.bypassPermission: zakum.packets.cull.bypass (default)
- packets.culling.probeIntervalTicks: 100 (default)

Validation steps
1) Boot server with PacketEvents + ZakumPackets installed.
2) Run /zakum packetcull status
   - hookRegistered=true
   - backend=packetevents
   - hookCount>=1
   - hookLastChanged shows a recent timestamp after attach
3) Run /zakum packetcull sample [player]
   - If no sample yet, it queues one. Re-run after a second.
   - Confirm density, ageMs, mode, bypass, threshold values.
4) Perf-mode check
   - /perfmode off (QUALITY) then /zakum packetcull sample
     - wouldDrop should be false (QUALITY bypass)
   - /perfmode on (PERFORMANCE) then /zakum packetcull sample
     - threshold should be ~50% of base
5) Permission bypass check
   - Grant zakum.packets.cull.bypass to a player
   - /zakum packetcull sample
     - bypass=true, wouldDrop=false
6) Live density check
   - Create high-density area (mobs/players/particles) near the target.
   - Watch /zakum packetcull status
     - packetsObserved increases
     - packetsDropped increases under high density
     - dropRate rises above 0

Troubleshooting
- hookRegistered=false: ensure PacketEvents + ZakumPackets are installed and packets.enabled=true.
- hookLastChanged=never: PacketService hasn't attached yet; check packets.culling.probeIntervalTicks or wait a few seconds.
- packetsDropped stays 0 under load: ensure packetNames contains emitted packet types and packets.outbound=true.
- wouldDrop=false with high density: check maxSampleAgeMs and sample freshness.

Notes
- Sampling is batched for large online counts to keep samples fresh within maxSampleAgeMs.
- Culling never runs if runtimeEnabled=false or packet backend is unavailable.
