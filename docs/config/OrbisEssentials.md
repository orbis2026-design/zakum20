# OrbisEssentials Configuration

Config folder: `plugins/OrbisEssentials/`

Files:
- `config.yml`

Keys:
- `teleport.warmupSeconds`
- `teleport.cancelOnMove.enabled`
- `teleport.cancelOnMove.maxDistanceBlocks`
- `teleport.cooldownSeconds`
- `tpa.expireSeconds`
- `tpa.warmupSeconds`
- `homes.max`
- `spawn.*` (world,x,y,z,yaw,pitch)
- `messages.*` (prefix + localized messages)

Notes:
- Homes/warps/back are DB-backed; if Zakum DB is offline they degrade gracefully.


---
*Development Note: Edit this module using IntelliJ IDEA with Gradle Sync enabled.*