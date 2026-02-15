# OrbisHolograms Configuration

Updated (UTC): 2026-02-15T11:59:52Z

## Keys
- `holograms.enabled` (boolean, default: `true`)
- `holograms.renderTickInterval` (int, default: `10`, clamped `1..100`)
- `holograms.viewDistance` (int, default: `48`, clamped `8..128`)
- `holograms.maxVisiblePerPlayer` (int, default: `64`, clamped `1..256`)
- `holograms.hideThroughWalls` (boolean, default: `false`)
- `definitions.<id>.world` (string, default: `world`)
- `definitions.<id>.x|y|z` (double)
- `definitions.<id>.lines[]` (1..20 non-blank lines)

## Commands
- `/orbishologram status`
- `/orbishologram reload`

## Permissions
- `orbisholograms.admin`
- `orbisholograms.reload`
