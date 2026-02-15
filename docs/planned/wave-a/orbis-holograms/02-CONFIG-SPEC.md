# Config Specification

Updated (UTC): 2026-02-15T11:37:37Z
Module: orbis-holograms

## Root Contract
- All keys validate at startup/reload.
- Invalid numeric values are clamped.
- Invalid hologram ids/locations are skipped with warnings (plugin remains enabled).

## Key Schema
- `holograms.enabled` (boolean, default: `true`)
  Behavior: global runtime toggle.
- `holograms.renderTickInterval` (int, default: `10`, range: `1..100`)
  Behavior: viewer evaluation frequency.
- `holograms.viewDistance` (int, default: `48`, range: `8..128`)
  Behavior: spawn radius per viewer.
- `holograms.maxVisiblePerPlayer` (int, default: `64`, range: `1..256`)
  Behavior: hard cap to protect TPS.
- `holograms.hideThroughWalls` (boolean, default: `false`)
  Behavior: LOS-gated rendering when enabled.
- `storage.format` (enum: `yaml|json`, default: `yaml`)
  Behavior: serialization backend.
- `storage.file` (string, default: `holograms.yml`)
  Behavior: relative data path for persisted definitions.
- `storage.autosaveIntervalTicks` (int, default: `1200`, range: `100..12000`)
  Behavior: periodic flush interval.
- `definitions.<id>.world` (string, required)
- `definitions.<id>.x|y|z` (double, required)
- `definitions.<id>.billboard` (enum, default: `CENTER`)
- `definitions.<id>.shadowed` (boolean, default: `false`)
- `definitions.<id>.lines[]` (1-20 lines, required)

## Validation Behavior
- Missing world references mark entry as inactive and log warning.
- Empty line lists reject definition (entry skipped).
- Duplicate ids resolve by last-defined entry with warning.
- Reload applies diff: remove stale entities, then spawn new/changed entries.
