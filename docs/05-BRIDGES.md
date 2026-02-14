# Bridges

Bridges are optional adapters to external plugins.

Principles:
- one bridge per dependency (`zakum-bridge-*`)
- `compileOnly` dependency
- runtime presence check
- no crashes if dependency missing

Example patterns:
- PlaceholderAPI bridge: register placeholders exposing Zakum stats
- Vault bridge: economy abstraction
- LuckPerms bridge: prefix/meta integration
- Citizens bridge: NPC-based menu opening hooks
- EssentialsX bridge: emit actions when Essentials commands are used

Do not:
- hard-depend bridges from zakum-core
- keep references to external plugin classes in core

## Bridge inventory (v1)

- `zakum-bridge-placeholderapi` (soft): PAPI placeholders for Zakum + modules.
- `zakum-bridge-vault` (soft): economy support (Vault).
- `zakum-bridge-luckperms` (soft): prefix/meta reads and optional actions.
- `zakum-bridge-votifier` (soft): vote events → actions.
- `zakum-bridge-citizens` (soft): NPC interactions → menu open hooks.
- `zakum-bridge-essentialsx` (soft): EssentialsX usage → actions.
- `zakum-bridge-commandapi` (**hard**, optional install): replaces `/zakum` with a typed CommandAPI command tree.
  - Requires installing the `CommandAPI` plugin.
  - On enable, it unregisters the fallback `/zakum` command node from `zakum-core` to avoid duplicates.
