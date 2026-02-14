# Zakum Core (Orbis Network) Overview

Zakum is the shared core ("XXXlib") for the Orbis Minecraft network.

Primary goals:
- **Stability over months of uptime**
- **Minimal main-thread work** (world simulation stays smooth)
- **Typed configuration**
- **Async-first I/O** (DB + HTTP)
- **Bridges/adapters** for popular plugins without baking those APIs into every feature plugin

---

## Runtime shape

On each backend Paper server:

- `Zakum` (this plugin) boots first
- It exposes `ZakumApi` through Bukkit ServicesManager
- Feature plugins (BattlePass, Crates, Pets, etc.) depend on `ZakumApi` only

On the proxy (Velocity) side:

- Orbis has separate proxy gating + routing logic
- Zakum stays backend-focused, but keeps a small optional **ControlPlane** client interface for bot/API integration

---

## Golden rules (performance + uptime)

1) **No DB or HTTP on the main thread.**
   - DB/HTTP: async executor only
   - World changes: sync only

2) **Treat external dependencies as unreliable.**
   - DB failover loop is async and guarded
   - ControlPlane client has optional circuit breaker + retry

3) **Keep integration code in bridges.**
   - Each plugin integration lives in its own module (`zakum-bridge-*`)
   - `compileOnly` + runtime detection
   - No bridge logic in hot paths unless enabled

4) **Immutable config snapshot.**
   - `ZakumSettings` is loaded once at startup (and later on reload)
   - Systems read from that snapshot, not from raw YAML lookups

---

## Modules quick map

- `zakum-api`:
  - Stable interfaces: `ZakumApi`, `ActionBus`, `ZakumDatabase`, `EntitlementService`, `BoosterService`
  - Typed config snapshot: `ZakumSettings`
  - ControlPlane abstraction: `ControlPlaneClient` + `ZakumHttpResponse`

- `zakum-core`:
  - Implements the API + runtime services
  - Database pool + migrations (`SqlManager`)
  - Action emitters + deferred action replay
  - Entitlements + boosters
  - Optional metrics endpoint (Prometheus)

- `zakum-battlepass`:
  - BattlePass logic using actions + DB + entitlements
  - (expanded iteratively)

- Bridges (`zakum-bridge-*`):
  - Optional adapters for PlaceholderAPI, Vault, LuckPerms, Citizens, EssentialsX, etc.

- `orbis-essentials`:
  - A functional Essentials-style backend plugin (homes/warps/spawn/tpa/back)

See: `docs/01-MODULES.md` for deeper details.

- `docs/13-CONFIG-FOLDERS.md` (per-plugin config folders)


---
*Development Note: Edit this module using IntelliJ IDEA with Gradle Sync enabled.*