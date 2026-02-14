# Packet TextDisplay LOD Fallback

When adaptive LOD triggers (ping/tps/perf mode/density), crate item animations downgrade to a
packet-based TextDisplay label instead of an actionbar message. The display is per-viewer and
is destroyed automatically after ~2 seconds (40 ticks).

Behavior
- Normal path: ItemDisplay packet animation.
- LOD downgrade: TextDisplay packet label near the animation location.
- Fallback: If packet dispatch fails, actionbar label is used.

Config touchpoints (zakum-core config.yml)
- visuals.lod.enabled
- visuals.lod.maxPingMs
- visuals.lod.minTps
- visuals.culling.enabled (density-based downgrade)
- /perfmode on|off|auto (per-player override)

Observability
- metrics action: animation_lod_text_display
- metrics action: animation_lod_actionbar (fallback only)

Operational notes
- Requires PacketEvents plugin to be present for packet-based TextDisplay.
- Label text is derived from the item type name (lowercased, underscores replaced).
- Each viewer receives its own entity id and destroy packet (no shared entities).

Troubleshooting
- No text label under LOD: confirm PacketEvents is installed and the packet backend is healthy.
- Actionbar still used: packet spawn failed or PacketEvents not available.
- Stale labels: verify scheduler is running; destroy runs after 40 ticks per viewer.
