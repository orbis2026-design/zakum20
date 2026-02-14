# Action System (Quest Triggers)

Zakum's ActionBus is the canonical event stream that feature plugins consume.

## Why not listen to Bukkit events directly everywhere?
- duplication across products
- inconsistent semantics across plugins
- harder to gate performance (some events are noisy)

Instead:
- Zakum emits normalized actions with simple payloads
- BattlePass/Crates/etc subscribe to these actions

## Emitters
Implemented in: `zakum-core/.../actions/emitters/*`

Examples:
- Block break -> `BLOCK_BREAK`
- Mob kill -> `MOB_KILL`
- Online time -> `ONLINE_TIME_TICK` (sampled, not per tick)
- Movement -> `MOVE_CM` (sampled, not per tick)

## Deferred replay
When a server is down or DB is temporarily offline, actions can be stored
and replayed later up to a configured claim limit.

This prevents:
- lost quest progress during short DB outages
- operators needing to manually reimburse players

Controlled by:
- `actions.deferredReplay.enabled`
- `actions.deferredReplay.claimLimit`
