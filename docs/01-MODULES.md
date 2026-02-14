# Modules and Interaction Map

## zakum-api (stable surface)

### `ZakumApi`
Resolved via ServicesManager:
```java
ZakumApi api = Bukkit.getServicesManager().load(ZakumApi.class);
```

Provides:
- `async()` executor (shared pool)
- `database()` (online/offline state + JDBC access)
- `actions()` (event bus for quest triggers)
- `entitlements()` (premium/free checks, server-scoped keys)
- `boosters()` (time-based modifiers)
- `controlPlane()` (optional HTTP bridge to Orbis Cloud Bot)
- `settings()` (typed, immutable config snapshot)

### Why ServicesManager?
- clean dependency boundary
- no static globals required
- feature plugins can soft-depend and degrade gracefully

---

## zakum-core (runtime)

### Boot order
1) Load YAML (`config.yml`)
2) Build typed `ZakumSettings` snapshot
3) Start async executor pool
4) Start metrics (optional)
5) Start DB manager (async connect + migrations)
6) Start entitlements cache + boosters
7) Register `ZakumApi` service
8) Register action emitters (config-driven)

### Threading model
- Main thread: listeners that only **emit actions** and update small in-memory state
- Async pool: DB, HTTP, heavy serialization, background reconciliation

See: `docs/02-THREADING.md`.

---

## zakum-packets (packet layer)

Provides:
- `PacketService` via ServicesManager
- PacketEvents-backed packet hooks (inbound/outbound)

Notes:
- Runs hooks on the packet thread (Netty). Do not call Bukkit world APIs directly.
- Uses central Zakum config under `packets.*`.

---

## Feature plugins (battlepass/crates/pets/etc)

Feature plugins should:
- read config
- register quest definitions / reward definitions
- subscribe to `ActionBus`
- update player progress in DB (async)
- deliver rewards on main thread
- keep memory usage bounded with caches

---

## Bridges

Each bridge:
- uses `compileOnly` dependency on the external plugin
- checks presence at runtime
- registers listeners or hooks to emit actions (or provide data)

Bridges must:
- never crash Zakum if dependency is missing
- never spam async tasks on hot paths

See: `docs/05-BRIDGES.md`.

Bridge inventory is documented there (including `zakum-bridge-commandapi`).
