# Zakum (core) Configuration

Config folder: `plugins/Zakum/`

Files:
- `config.yml` (central typed settings)

Key sections (top-level):
- `server.id` (required): per-server scoping identity (battlepass, warps, etc.)
- `database.*`: MySQL/Hikari + failover
- `controlPlane.*`: optional Orbis Cloud Bot API endpoint + key
- `entitlements.cache.*`: cache sizing/TTL for premium checks
- `boosters.*`: booster behavior (handled by BoosterService)
- `actions.*`: action emitters + movement sampling + deferred replay
- `operations.startupValidator.*`: module startup/load-order validator controls
- `datastore.*`: optional Mongo/Redis profile+session capability
- `economy.global.*`: optional Redis global economy capability
- `cache.burst.*`: shared transient burst-cache service (Redis + local fallback)
- `http.*`: shared OkHttp client defaults + resilience guards
- `cache.defaults.*`: shared cache posture (future systems)
- `observability.metrics.*`: embedded Prometheus endpoint
- `packets.*`: packet layer toggle + PacketEvents backend

Related docs:
- `docs/03-CONFIG.md`
- `docs/04-ACTIONS.md`
- `docs/06-DATABASE.md`
- `docs/07-OBSERVABILITY.md`
- `docs/08-PACKETS.md`


---
*Development Note: Edit this module using IntelliJ IDEA with Gradle Sync enabled.*
