# GrimAC -> ACE Bridge

Overview
- Converts Grim flag events into ACE script executions.
- Runs ACE execution on the flagged player's entity scheduler.
- Adds production safeguards to avoid script spam under dense flag storms.

Config
```yaml
anticheat:
  grim:
    enabled: false
    cooldownMsPerCheck: 750
    maxFlagsPerMinutePerPlayer: 120
    includeVerboseMetadata: true
    aceScript:
      - "[MESSAGE] <red>Anticheat flag: <gold>%check%</gold>"
```

Safeguards
- `cooldownMsPerCheck`: suppresses repeated same-check flags for the same player.
- `maxFlagsPerMinutePerPlayer`: hard cap on accepted flags per player per minute (`0` disables cap).
- runtime toggle via command: no restart required.

Command / Control
- `/zakum grim status`
- `/zakum grim enable`
- `/zakum grim disable`

Telemetry
- `grim_flag_event`: raw observed flag events.
- `grim_flag_ace`: accepted events that executed ACE.
- `grim_flag_cooldown_skip`: suppressed by per-check cooldown.
- `grim_flag_rate_limit_skip`: suppressed by per-player rate cap.
- `grim_flag_disabled_skip`: suppressed because runtime bridge disabled.
- `grim_flag_error`: ACE execution failure.

Metadata passed to ACE
- `check`
- `vl`
- `verbose` (when enabled)
- `player`
- `uuid`
- `event_class`
