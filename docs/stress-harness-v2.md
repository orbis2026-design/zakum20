# Stress Harness v2 (500-Player)

Purpose
- Synthetic actor load generator for ACE and packet-heavy paths.
- Exercises chat buffer, visuals, economy, and RTP combos under controlled safety gates.

Key Concepts
- Virtual players: synthetic actor pool size used for distribution + metadata.
- Iterations: total ACE script executions for the run.
- Matrix: weighted set of ACE scripts (built-in defaults).

Config (zakum-core config.yml)
operations:
  stress:
    enabled: false
    defaultIterations: 5000
    maxIterations: 200000
    cooldownSeconds: 30
    virtualPlayers: 500
    iterationsPerTick: 25
    maxDurationSeconds: 120
    minOnlinePlayers: 1
    minTps: 17.5
    abortBelowTpsSeconds: 10
    maxErrors: 250
    allowRtp: true
    allowEconomy: true
    allowChat: true
    allowVisuals: true

Commands
- /zakum stress start [iterations] [virtualPlayers]
- /zakum stress stop
- /zakum stress status

Status output
- Shows planned/scheduled/completed iterations, errors, TPS, and scenario counts.
- stopReason reveals whether the run completed, timed out, or aborted.

Safety Gates
- cooldownSeconds prevents rapid re-runs.
- minOnlinePlayers blocks runs without enough live targets.
- minTps + abortBelowTpsSeconds auto-aborts under low TPS.
- maxDurationSeconds hard-stops long runs.
- maxErrors aborts if too many script failures.

Built-in Matrix (defaults)
- chat_key: MESSAGE_KEY (buffered localized chat)
- actionbar_key: ACTION_BAR_KEY (buffered actionbar)
- visual_pulse: particles + sound
- aoe_particles: radius particle spray
- economy_tick: GIVE_MONEY
- xp_tick: GIVE_XP
- rtp_economy_combo: GIVE_MONEY + RTP + ACTION_BAR_KEY

Notes
- RTP/economy scenarios are skipped automatically if toggled off or economy is unavailable.
- Visuals will respect the circuit breaker and perf-mode settings as configured.
- For true 500-player simulation, raise iterations and keep iterationsPerTick high enough to saturate.
