# E2E Feature Board (Gamemode + Minigame Platform)

Updated (UTC): 2026-02-15T11:59:52Z
Scope: Paper/Spigot 1.21.11 + Java 21, zero premium dependency strategy.

## Scoring Model
- 200 total points.
- Highest-value architecture work ships first.
- Items are scored for direct impact on building and operating real gameplay plugins.

## Board
1. 30 pts - Core runtime/API backbone
   - Zakum core lifecycle, scheduler abstraction, API contracts, capability registry.
   - Status: in-progress (26/30)
2. 20 pts - Plugin creation tooling + process gates
   - module generator, plugin bootstrap base class, descriptor/build boundary checks.
   - Status: complete (20/20)
3. 30 pts - Data/state spine
   - SQL+Hikari+Flyway, Mongo profile path, Redis session/cache/economy sync.
   - Status: in-progress (16/30)
4. 20 pts - Protocol + command substrate
   - PacketEvents bridge, packet runtime, CommandAPI typed trees.
   - Status: in-progress (12/20)
5. 20 pts - Bridge baseline
   - Vault, PlaceholderAPI, LuckPerms, EssentialsX, Jobs, MythicMobs, etc.
   - Status: in-progress (14/20)
6. 20 pts - Scriptability/security kernel
   - ACE engine/effects, diagnostics, GrimAC -> ACE hardening.
   - Status: in-progress (12/20)
7. 20 pts - Feature foundation modules
   - battlepass, crates, pets, essentials, gui, hud.
   - Status: in-progress (16/20)
8. 20 pts - Wave A expansion pack
   - orbis-worlds, orbis-holograms, orbis-loot.
   - Status: started (8/20)
9. 20 pts - Ops reliability and soak readiness
   - data health probes, task diagnostics, packet runbooks, stress harness.
   - Status: in-progress (14/20)

Current board score: 138 / 200

## What Was Started In This Pass
- Added missing Wave A modules to settings and created buildable module baselines:
  - `orbis-worlds`
  - `orbis-holograms`
  - `orbis-loot`
- Each module now has:
  - Zakum plugin bootstrap
  - validated config loader
  - runtime service lifecycle (`start/stop/reload/status`)
  - operator command surface with permissions (`status`, `reload`; loot includes `simulate`)

## Next Value Slice (Recommended)
1. 12 pts - Implement real world lifecycle ops in `orbis-worlds` (`create/load/unload/tp/delete` safeguards).
2. 10 pts - Implement entity-backed hologram spawn/despawn diff runtime in `orbis-holograms`.
3. 10 pts - Implement key balances + transactional open flow in `orbis-loot`.
4. 8 pts - Add module-level test coverage for new Wave A services/config parsers.
