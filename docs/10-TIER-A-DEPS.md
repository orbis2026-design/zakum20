# Tier-A Dependencies Policy

Goal: replicate AdvancedTeam-style products while keeping the network stable.

## Rule of thumb

- If it's *infrastructure* (protocol, perms, placeholder ecosystem, bedrock bridge):
  **integrate** and abstract behind a Zakum service/bridge.

- If it's *game/business logic* (battlepass, crates, quests, rewards):
  **own the code** in Zakum modules.

## Tier-A integrations we treat as dependencies

- Packet layer: PacketEvents (via ZakumPackets)
- Commands: CommandAPI (via ZakumBridgeCommandAPI)
- Permissions/meta: LuckPerms
- Placeholders: PlaceholderAPI
- Economy bridge: Vault
- NPC interaction: Citizens
- Bedrock support: Geyser + Floodgate
- Cross-version: ViaVersion (+ friends)

Each integration must live in a bridge module:
- `compileOnly` dependency
- runtime presence checks
- never crash core if missing
- keep hot paths small

## Why not reimplement these?

These projects represent years of edge-case fixes across:
- protocol changes
- platform differences
- third-party expectations

Owning them means owning their churn forever.
