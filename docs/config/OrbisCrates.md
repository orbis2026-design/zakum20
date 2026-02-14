# OrbisCrates Configuration

Config folder: `plugins/OrbisCrates/`

Files:
- `config.yml`

Keys:
- `crates.<id>.name`
- `crates.<id>.publicOpen` + `publicRadius`
- `crates.<id>.key.*` (material, name, lore, modelData)
- `crates.<id>.rewards[]`:
  - `weight`
  - `messages[]`
  - `commands[]` (console)
  - `items[]` (material, amount, modelData, name, lore)
- `animation.steps`
- `animation.ticksPerStep`

Dependencies:
- Vault bridge provides `EconomyService` to run economy rewards.
