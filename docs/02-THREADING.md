# Threading and Performance Posture

## Guideline
**Bukkit/Paper API is not generally thread-safe.**

Safe pattern:
- async work (DB/HTTP) -> compute result -> schedule sync task to apply to world/player
- avoid per-tick loops unless strictly required

Zakum conventions:
- `ZakumApi.async()` is the shared executor
- DB manager does connect/retry asynchronously
- ControlPlane uses async HTTP callbacks

Paper scheduler docs: keep heavy work out of the main thread. (See Paper docs.)

## What fails longterm if you violate this?
- random concurrency bugs (rare, hard to reproduce)
- chunk loads on main thread -> TPS collapse
- join burst + HTTP outage -> thread starvation if no bulkheads/circuit breakers
- memory growth from unbounded task queues

Zakum specifically guards against:
- infinite DB reconnect loops on main thread (it never happens)
- excessive movement sampling (config clamped)
- oversized caches (clamped)
