# OrbisHud Configuration

Updated (UTC): 2026-02-15T11:40:55Z
Config folder: `plugins/OrbisHud/`
Primary file: `config.yml`

## Runtime Model
- OrbisHud renders a scoreboard sidebar from named profiles.
- Profiles are selected by default profile or forced per-player profile override.
- Config is hot-reloadable via `/orbishud reload` with validation/fallback behavior.

## Key Reference

### `hud.enabled`
- Type: boolean
- Default: `true`
- Validation: none
- Behavior: global HUD runtime toggle.

### `hud.updateIntervalTicks`
- Type: integer
- Default: `20`
- Validation: clamped to `[5, 200]`
- Behavior: periodic refresh interval.

### `hud.defaultProfile`
- Type: string
- Default: `default`
- Validation:
  - normalized to lowercase
  - if missing from `profiles`, falls back to first valid profile
- Behavior: default profile for players without forced override.

### `hud.hideWhenInSpectator`
- Type: boolean
- Default: `true`
- Validation: none
- Behavior: clears HUD for spectator mode while enabled.

### `profiles`
- Type: map
- Required logically: yes
- Validation:
  - profile id regex: `[a-z0-9_-]{1,32}`
  - duplicate normalized ids overwrite previous entry with warning
  - if no valid profiles remain, fallback profile is injected

### `profiles.<id>.title`
- Type: string
- Default: `&b&lORBIS`
- Validation: blank -> default title
- Runtime behavior: colorized and truncated to scoreboard-safe length.

### `profiles.<id>.lines[]`
- Type: list of strings
- Validation:
  - blank lines removed
  - if empty, fallback lines injected
  - max 15 lines (extra trimmed)
- Runtime behavior: placeholders replaced per player and scoreboard-line safe truncation applied.

## Built-in Placeholders
- `%player%`
- `%display_name%`
- `%world%`
- `%online%`
- `%ping%`
- `%x%`, `%y%`, `%z%`
- `%health%`, `%max_health%`
- `%server_id%`
- `%time%` (UTC `HH:mm:ss`)

## Commands
- `/orbishud status`
- `/orbishud reload`
- `/orbishud profile <player> <profile|default>`

## Permissions
- `orbishud.admin` (status + global override)
- `orbishud.reload`
- `orbishud.profile.set`

## Example
```yaml
hud:
  enabled: true
  updateIntervalTicks: 20
  defaultProfile: default
  hideWhenInSpectator: true

profiles:
  default:
    title: "&b&lORBIS"
    lines:
      - "&7Player: &f%player%"
      - "&7World: &b%world%"
      - "&7Online: &a%online%"
      - "&7Ping: &e%ping%ms"
      - "&7Server: &b%server_id%"
      - "&7UTC: &f%time%"
```
