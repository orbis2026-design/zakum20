# Zakum Suite - Modular Minecraft Plugin Ecosystem

**Version:** 0.1.0-SNAPSHOT  
**Target Platform:** Paper 1.21.11 | Java 21 | Gradle Kotlin DSL  
**Status:** Development (Foundation Phase)

> **DEVELOPMENT STANDARD:** This project is developed using **IntelliJ IDEA 2024.1.2+** with **Minecraft Development Plugin** support.

---

## Overview

Zakum is a modular Minecraft plugin ecosystem providing network-level infrastructure, seamless third-party integrations, and feature-rich player experiences. The project consists of **27 modules** organized into four categories:

- **4 Core Modules:** API, Core, Packets, BattlePass
- **11 Bridge Modules:** Third-party plugin integrations (Vault, PlaceholderAPI, LuckPerms, MythicMobs, Jobs, WorldGuard, FAWE, and more)
- **8 Feature Modules:** Player-facing features (**Crates** ✅, Pets, MiniaturePets, Teams, Essentials, GUI, HUD, Holograms, Worlds, Loot)
- **Stub Modules:** 4 modules in planning/stub state

**Current Status:** 120/120 steps complete (100%) ✅ | **Phase 3:** COMPLETE (10/10 steps - 100%) ✅

---

## Quick Start

### Prerequisites

- **Java 21** (Temurin/Adoptium recommended)
- **Gradle 9.3.1** (wrapper included)
- **IntelliJ IDEA 2024.1.2+** (recommended) with Minecraft Development Plugin
- **Paper 1.21.11** server for deployment

### Building

```bash
# Full build (all 23 modules)
./gradlew clean build

# Individual module
./gradlew :zakum-core:build

# Verification (API boundaries, descriptors, conventions)
./gradlew verifyPlatformInfrastructure
```

**Output JARs:**
- `zakum-core/build/libs/Zakum-<version>.jar`
- `zakum-battlepass/build/libs/ZakumBattlePass-<version>.jar`
- `[other modules]/build/libs/[ModuleName]-<version>.jar`

---

## Module Status

### ✅ Production Ready (16 modules - 59%)

**Core Infrastructure (4 modules):**
- **zakum-api** - Public API layer
- **zakum-core** - Runtime implementation with HikariCP, Flyway, metrics
- **zakum-packets** - Packet manipulation via PacketEvents
- **zakum-battlepass** - Seasonal progression (100% feature complete)

**Feature Modules (4 modules):**
- **orbis-essentials** - Essential commands (homes, warps, tpa, spawn)
- **orbis-gui** - YAML-driven GUI system
- **orbis-hud** - HUD overlay system (actionbar/bossbar)
- **zakum-crates** ⭐ **NEW** - Advanced crate system (100% complete, production ready)

**Bridge Modules (11 modules - All Production Ready):**
- zakum-bridge-placeholderapi
- zakum-bridge-vault
- zakum-bridge-luckperms
- zakum-bridge-votifier
- zakum-bridge-citizens
- zakum-bridge-essentialsx
- zakum-bridge-commandapi
- zakum-bridge-mythicmobs
- zakum-bridge-jobs
- zakum-bridge-worldguard
- zakum-bridge-fawe

### 🚧 In Development (3 modules - 11%)

- **zakum-pets** (~40% complete) - Ability system and GUI in progress
- **zakum-miniaturepets** (~80% complete) - Chunk optimization needed
- **orbis-holograms** (~30% complete) - Core implementation in progress

### ⏰ Planned (2 modules - 7%)

- **orbis-worlds** - World management (Multiverse-Core parity)
- **orbis-loot** - Advanced loot system (ExcellentCrates parity)

### ❌ Stub/Planning (6 modules - 22%)

- **zakum-teams** - Team management system (Iridium replacement)
- Other modules in planning phase

**Note:** See [MODULE_STATUS.md](MODULE_STATUS.md) for detailed module information.

---

## Bridge Integrations

All bridge modules follow a consistent pattern:
- Runtime detection (safe if dependency missing)
- Reload-safe registration
- ActionBus event emission
- `compileOnly` dependency scope

**Example: MythicMobs Bridge**
```java
// Automatically emits actions when MythicMobs are killed
ActionBus bus = ZakumApiProvider.get().getActionBus();
bus.emit("custom_mob_kill", uuid, Map.of("key", "mythic", "value", mobName));
```

---

## Documentation

### 📖 Quick Navigation
**→ [DOCUMENTATION_INDEX.md](DOCUMENTATION_INDEX.md)** - Fast access to all documentation

### Quick Reference
- **[MODULE_STATUS.md](MODULE_STATUS.md)** ⭐ **START HERE** - Complete module inventory and status
- **[ROADMAP.md](ROADMAP.md)** - Development roadmap and timeline
- **[DEVELOPMENT_STANDARD.md](DEVELOPMENT_STANDARD.md)** - Development standards and prompt format
- **[CHANGELOG.md](CHANGELOG.md)** - Version history
- **[RELEASE_NOTES.md](RELEASE_NOTES.md)** - Release documentation
- **[PROJECT_COMPLETE.md](PROJECT_COMPLETE.md)** - Phase 1-2 completion summary

### Developer Guides
- **[DEVELOPMENT-GUIDE.md](DEVELOPMENT-GUIDE.md)** - IntelliJ setup and workflows
- **[DEPENDENCY-MANIFEST.md](DEPENDENCY-MANIFEST.md)** - Complete dependency list
- **[AUTOMATION_SYSTEM.md](AUTOMATION_SYSTEM.md)** - CI/CD documentation

### Architecture Documentation
- **Core Architecture:** `docs/00-OVERVIEW.md` through `docs/21-MODULE-DATA-HEALTH.md`
- **Configuration Reference:** `docs/config/*.md` (13 files)
- **Bridge Integration:** `docs/05-BRIDGES.md`, `docs/12-bridges.md`
- **Plugin Development:** `docs/23-PLUGIN-DEVKIT.md`

---

## Key Features

### zakum-core
- HikariCP connection pooling (25 connections)
- Flyway database migrations (12 migrations)
- Entitlements cache (75k entries)
- Prometheus metrics endpoint
- Resilience4j (circuit breaker, retry, bulkhead, rate limiter)
- Shaded dependencies (18 relocations to avoid conflicts)

### zakum-battlepass
- YAML-driven quest system
- Multi-step objectives (kill, craft, mine, etc.)
- Seasonal/daily/weekly cadence
- Premium scope (SERVER/GLOBAL)
- Point tiers with configurable rewards
- PlaceholderAPI integration

### orbis-essentials
- `/home`, `/sethome`, `/delhome`
- `/warp`, `/setwarp`, `/delwarp`
- `/spawn`, `/setspawn`
- `/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny`
- `/back` (death/teleport return)

### Bridge Pattern
All bridges detect dependencies at runtime and register cleanly:
```java
if (getServer().getPluginManager().getPlugin("TargetPlugin") == null) {
    getLogger().warning("TargetPlugin not found; disabling bridge");
    getServer().getPluginManager().disablePlugin(this);
    return;
}
```

---

## Development Workflow

### IntelliJ IDEA Setup
1. Open project in IntelliJ IDEA 2024.1.2+
2. Install Minecraft Development Plugin
3. Wait for Gradle sync to complete
4. Verify Java 21 SDK in Project Structure
5. Build individual modules via Gradle tab

### Verification Tasks
```bash
# API boundary enforcement (features only depend on zakum-api)
./gradlew verifyApiBoundaries

# Plugin descriptor validation (plugin.yml contracts)
./gradlew verifyPluginDescriptors

# Module build conventions (Paper API, version expansion)
./gradlew verifyModuleBuildConventions

# Shadow JAR audit (relocation verification)
./gradlew releaseShadedCollisionAudit

# All platform checks
./gradlew verifyPlatformInfrastructure
```

---

## Technology Stack

| Component | Version | Purpose |
|-----------|---------|---------|
| Paper API | 1.21.11-R0.1-SNAPSHOT | Minecraft server API |
| Java | 21 | Language runtime |
| Gradle | 9.3.1 + Kotlin DSL | Build system |
| HikariCP | 5.1.0 | Database connection pooling |
| Flyway | 10.10.0 | Database migrations |
| Caffeine | 3.2.3 | High-performance caching |
| Resilience4j | 2.2.0 | Circuit breaker, retry, bulkhead |
| Micrometer | 1.14.4 | Metrics framework |
| Prometheus | 1.14.4 | Metrics export |
| PacketEvents | 2.5.0 | Packet manipulation |
| CommandAPI | 11.1.0 | Typed commands |

---

## Contributing

### Development Priorities (Week 1)
1. ✅ Complete documentation baseline
2. ⏰ Verify build system (all 23 modules)
3. ⏰ Add test infrastructure (JUnit 5)
4. ⏰ Security scanning (CodeQL, OWASP)

See **[EXECUTION_STATUS.md](EXECUTION_STATUS.md)** for current priorities and **[DEVELOPMENT_PLAN.md](DEVELOPMENT_PLAN.md)** for the 323-step implementation plan.

---

## License

[Specify license here]

---

## Support

- **Documentation:** See `/docs` directory
- **Issues:** GitHub Issues
- **Discussions:** GitHub Discussions

---

**Last Updated:** 2026-02-18  
**Next Milestone:** Phase 1 Complete (Week 4)
