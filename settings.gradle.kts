rootProject.name = "zakum"

// Core modules
include(
  "zakum-api",
  "zakum-core",
  "zakum-packets"
)

// Feature modules
include(
  "zakum-battlepass",
  "zakum-crates",
  "zakum-pets",
  "zakum-miniaturepets"
)

// Bridge modules
include(
  "zakum-bridge-placeholderapi",
  "zakum-bridge-vault",
  "zakum-bridge-luckperms",
  "zakum-bridge-votifier",
  "zakum-bridge-citizens",
  "zakum-bridge-essentialsx",
  "zakum-bridge-commandapi",
  "zakum-bridge-mythicmobs",
  "zakum-bridge-jobs"
)

// Orbis modules
include(
  "orbis-essentials",
  "orbis-gui",
  "orbis-hud",
  "orbis-worlds",
  "orbis-holograms",
  "orbis-loot"
)

