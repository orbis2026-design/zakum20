# OrbisBridgeVotifier Configuration

Config folder: `plugins/OrbisBridgeVotifier/`

Files:
- `config.yml`

Keys:
- `deferred.enabled`: store offline vote actions for later claim
- `deferred.ttlSeconds`: expiry (default 7 days)
- `deferred.serverScope`: per-server (`true`) or network-wide (`false`)

Emits actions:
- `vote` (immediate for online players)
- Deferred replay uses Zakum `DeferredActionService` for offline claims.
