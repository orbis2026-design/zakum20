# Changelog

All notable changes to the Zakum Suite project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Foundation Hardening - Phase 1, Week 1 (2026-02-18)

#### Session ZAKUM-DEV-2026-02-18-001 CONTINUED
- **Fixed** API boundary violations in 4 modules:
  - zakum-teams: Removed zakum-core dependency (feature modules must use zakum-api only)
  - zakum-bridge-rosestacker: Removed zakum-core dependency
  - zakum-bridge-worldguard: Removed zakum-core dependency
  - zakum-bridge-fawe: Removed zakum-core dependency
- **Fixed** Missing dependency declarations in bridge modules:
  - zakum-bridge-rosestacker: Added direct Maven coordinate for RoseStacker API
  - zakum-bridge-worldguard: Added direct Maven coordinate for WorldGuard API
  - zakum-bridge-fawe: Added direct Maven coordinates for FastAsyncWorldEdit
- **Removed** unbuildable stub modules from settings.gradle.kts:
  - zakum-bridge-mythiclib (no build.gradle.kts, empty stub)
  - orbis-stacker (no build.gradle.kts, empty stub)
- **Final Module Count:** 27 modules (corrected from 29)
- **Status:** Ready for build execution with no API boundary violations

#### Session ZAKUM-DEV-2026-02-18-001 (Earlier)
- **Completed Step 8:** Updated Wave A planning documentation
- **Updated** `docs/26-E2E-FEATURE-BOARD.md` with current completion status (138→160/200 points)
- **Updated** `docs/28-NEXT-100-POINT-SPRINT-BOARD.md` with status tracking reference
- **Fixed** `settings.gradle.kts` to include all 29 modules (previously 22)
- **Discovered** 6 undocumented modules:
  - zakum-teams (feature module)
  - zakum-bridge-mythiclib (bridge module)
  - zakum-bridge-rosestacker (bridge module)
  - zakum-bridge-worldguard (bridge module)
  - zakum-bridge-fawe (bridge module)
  - orbis-stacker (orbis module)
- **Created** BUILD_VERIFICATION_REPORT.md (build tracking matrix)
- **Created** EXECUTION_LOG_2026-02-18.md (session logging)
- **Created** DEVELOPMENT_SESSION_PROGRESS_2026-02-18.md (session summary)
- **Updated** EXECUTION_STATUS.md with current module inventory and status
- **Prepared** for Steps 9-20 (build verification)

**Module Count:** Updated from 23 to 29 modules (+26% scope increase)  
**Feature Board Score:** 160/200 (+22 pts since 2026-02-15)  
**System Health:** 74/100 (stable)

### Repository Scan & Modernization (2026-02-18)
- Added comprehensive SYSTEM_STATUS_REPORT.md documenting all 23 modules
- Added CURRENT_ROADMAP.md with phased development plan (Phases 1-5)
- Added this CHANGELOG.md to track version history
- Verified tech stack compliance: Paper 1.21.11 ✓, Java 21 ✓, Gradle Kotlin DSL 9.3.1 ✓
- Documented API boundary enforcement and bridge patterns
- Identified incomplete implementations and prioritized completion roadmap

---

## [0.1.0-SNAPSHOT] - Current Development

### Core Infrastructure

#### zakum-api
- ✅ Added `ZakumApi` public interface
- ✅ Added `ActionBus` for event-driven architecture
- ✅ Added `Database` API for JDBC operations
- ✅ Added `Entitlements` API for player entitlements
- ✅ Added `Boosters` API for server-wide boosts
- ✅ Added `ControlPlane` client for external communication
- ✅ Added typed configuration snapshot system
- ✅ Enforced API boundary via Gradle verification task

#### zakum-core
- ✅ Implemented `ZakumApiProvider` for service discovery
- ✅ Implemented HikariCP connection pooling (25 connections, leak detection enabled)
- ✅ Implemented Flyway database migrations (12 migrations)
- ✅ Implemented async executor with thread pool
- ✅ Implemented Entitlements cache (75k entries, LRU eviction)
- ✅ Implemented movement sampling (100 ticks = 5 seconds)
- ✅ Implemented Prometheus metrics endpoint
- ✅ Implemented CircuitBreaker, Retry, Bulkhead, RateLimiter (Resilience4j)
- ✅ Shaded 18 dependencies to `net.orbis.zakum.shaded.*` namespace
- ✅ Added configuration validation at startup
- ✅ Added graceful shutdown handlers

#### zakum-packets
- ✅ Implemented PacketEvents integration (v2.5.0)
- ✅ Implemented entity culling system
- ✅ Implemented TextDisplay LOD (level-of-detail) system
- ✅ Implemented async packet handling with main thread hopping
- ✅ Added packet safety checks

### Feature Modules

#### zakum-battlepass
- ✅ Implemented full quest system with multi-step objectives
- ✅ Implemented seasonal, daily, and weekly cadence
- ✅ Implemented premium scope (SERVER/GLOBAL)
- ✅ Implemented point tiers with rewards
- ✅ Implemented PlaceholderAPI expansion
- ✅ Implemented flush interval (30 seconds)
- ✅ Implemented leaderboard optimization
- ✅ Added 5 database migrations
- 🎯 Status: **100% feature complete**

#### zakum-crates
- ✅ Implemented key management (PhysicalKeyFactory, KeyManager, VirtualKeyStore)
- ✅ Implemented block placement tracking (CrateBlockListener, CrateBlockStore)
- ✅ Implemented basic animation framework (CrateAnimator, CrateSession)
- ✅ Implemented database schema (3 migrations)
- ✅ Implemented default configuration (25+ animation types defined)
- ⏰ Partial: Animation type implementations (roulette, explosion, spiral, etc.)
- ⏰ Partial: Reward execution system (CommandReward, ItemReward, EffectReward)
- ⏰ Partial: GUI interactions (CrateGuiHolder handlers)
- ⏰ Partial: Preview system
- 🎯 Status: **~60% complete**

#### zakum-pets
- ✅ Implemented plugin bootstrap and database schema (2 migrations)
- ✅ Implemented PetDef model and YAML loader
- ✅ Implemented PetEntityManager spawning framework
- ✅ Implemented player state management
- ✅ Implemented follow modes (WANDER, FOLLOW, STAY)
- ⏰ Partial: 60+ ability classes (only scaffolded)
- ⏰ Partial: Leveling system implementation
- ⏰ Partial: GUI menus (inventory holders missing)
- ⏰ Partial: Combat and interaction mechanics
- ⏰ Partial: Pet storage system
- 🎯 Status: **~40% complete**

#### zakum-miniaturepets
- ✅ Implemented basic pet spawning
- ✅ Implemented player attachment
- ✅ Implemented movement following
- ✅ Added 2 database migrations
- ⏰ Needs: Chunk handling optimization
- ⏰ Needs: Performance profiling for 200-500 players
- 🎯 Status: **~80% complete, needs optimization**

#### orbis-essentials
- ✅ Implemented homes system with database persistence
- ✅ Implemented warps system (global waypoints)
- ✅ Implemented spawn command with cooldown
- ✅ Implemented TPA (teleport request) system
- ✅ Implemented back command (death/teleport return)
- ✅ Implemented permission checks for all commands
- ✅ Added configuration validation
- 🎯 Status: **Production ready**

#### orbis-gui
- ✅ Implemented YAML-driven GUI runtime
- ✅ Implemented SystemMenus support (12 YAML files)
- ✅ Implemented CustomGuis support (user-defined)
- ✅ Implemented dynamic menu loading
- ✅ Implemented `/gui` command with menu selector
- ✅ Implemented `/guireload` command
- ✅ Implemented clean service API for other modules
- 🎯 Status: **Production ready**

#### orbis-hud
- ✅ Implemented actionbar display system
- ✅ Implemented bossbar display system
- ✅ Implemented packet-based rendering
- ✅ Implemented `/hud toggle` command
- ✅ Implemented `/hud reload` command
- ✅ Implemented configuration validation
- ⏰ Needs: Production hardening and optimization
- 🎯 Status: **v1 runtime active, ~80% complete**

#### orbis-holograms
- ✅ Designed service interface
- ✅ Implemented command handler stubs
- ✅ Created configuration specification
- ✅ Created parity matrix (vs DecentHolograms)
- ⏰ Needs: TextDisplay packet integration
- ⏰ Needs: Line management and updating
- ⏰ Needs: Animation support
- ⏰ Needs: Placeholder integration
- ⏰ Needs: Per-player visibility
- 🎯 Status: **~30% complete**

#### orbis-worlds
- ✅ Created parity matrix (vs Multiverse-Core)
- ✅ Created configuration specification
- ✅ Created commands/permissions documentation
- ✅ Created test plan
- ⏰ Needs: WorldService API implementation
- ⏰ Needs: World creation/deletion
- ⏰ Needs: World import/export
- ⏰ Needs: World templates
- ⏰ Needs: Per-world game rules
- 🎯 Status: **Planning complete, implementation not started**

#### orbis-loot
- ✅ Created parity matrix (vs ExcellentCrates)
- ✅ Created configuration specification
- ✅ Created commands/permissions documentation
- ✅ Created test plan
- ⏰ Needs: Implementation (all features)
- 🎯 Status: **Planning complete, implementation not started**

### Bridge Modules (All Production Ready)

#### zakum-bridge-placeholderapi
- ✅ Implemented PlaceholderAPI expansion registration
- ✅ Implemented placeholder resolvers for all modules
- ✅ Implemented runtime detection
- ✅ Implemented reload-safe registration

#### zakum-bridge-vault
- ✅ Implemented economy service provider
- ✅ Implemented balance operations (deposit, withdraw, check)
- ✅ Implemented runtime detection
- ✅ Implemented reload-safe registration

#### zakum-bridge-luckperms
- ✅ Implemented permission check integration
- ✅ Implemented group query integration
- ✅ Implemented runtime detection
- ✅ Implemented reload-safe registration

#### zakum-bridge-votifier
- ✅ Implemented vote event listener
- ✅ Implemented ActionBus integration (emits vote events)
- ✅ Implemented runtime detection
- ✅ Implemented reload-safe registration

#### zakum-bridge-citizens
- ✅ Implemented NPC event listener
- ✅ Implemented NPC interaction handlers
- ✅ Implemented runtime detection
- ✅ Implemented reload-safe registration

#### zakum-bridge-essentialsx
- ✅ Implemented command compatibility layer
- ✅ Implemented data migration utilities
- ✅ Implemented runtime detection
- ✅ Implemented reload-safe registration

#### zakum-bridge-commandapi
- ✅ Implemented typed command tree for `/zakum`
- ✅ Implemented tab completion
- ✅ Implemented argument validation
- ✅ Implemented runtime detection
- ✅ Implemented reload-safe registration

#### zakum-bridge-mythicmobs
- ✅ Implemented MythicMobs event listener
- ✅ Implemented `custom_mob_kill` action emission
- ✅ Implemented mob name extraction
- ✅ Implemented runtime detection
- ✅ Implemented reload-safe registration

#### zakum-bridge-jobs
- ✅ Implemented Jobs event listener
- ✅ Implemented `jobs_action` action emission (job type + action)
- ✅ Implemented `jobs_money` action emission (with decimal scaling)
- ✅ Implemented `jobs_exp` action emission (with decimal scaling)
- ✅ Implemented runtime detection
- ✅ Implemented reload-safe registration

#### zakum-bridge-superiorskyblock2
- ✅ Implemented SuperiorSkyblock2 event listener
- ✅ Implemented `skyblock_island_create` action emission
- ✅ Implemented schematic name extraction
- ✅ Implemented runtime detection
- ✅ Implemented reload-safe registration

### Build System

- ✅ Configured Gradle 9.3.1 with Kotlin DSL
- ✅ Configured Java 21 toolchain
- ✅ Configured Shadow plugin (9.3.1) for JAR shading
- ✅ Configured version catalog (libs.versions.toml)
- ✅ Implemented API boundary verification task
- ✅ Implemented plugin descriptor verification task
- ✅ Implemented module build conventions verification task
- ✅ Implemented shadow JAR relocation audit task
- ✅ Implemented unified platform verification task
- ✅ Configured Lombok for boilerplate reduction
- ✅ Configured JUnit 5 (Jupiter) for testing
- ✅ Configured test logging (full stack traces)

### Documentation

- ✅ Added 21 core architecture docs (00-21)
- ✅ Added 5 directive/governance docs (22-25)
- ✅ Added 13 config reference docs
- ✅ Added 16 Wave A planning docs
- ✅ Added 7 operational/diagnostic docs
- ✅ Added DEPENDENCY-MANIFEST.md (complete enumeration)
- ✅ Added DEVELOPMENT-GUIDE.md (IntelliJ setup)
- ✅ Added AUTOMATION_SYSTEM.md (CI/CD)
- ✅ Added DEV-STATUS-REPORT.txt (module status)
- ✅ Added README.md (quick start)
- ⏰ Needs: Documentation consolidation (remove redundancies)
- ⏰ Needs: Javadoc generation for public APIs
- ⏰ Needs: Complete command reference
- ⏰ Needs: Complete permission reference

### CI/CD

- ✅ Implemented 11 GitHub Actions workflows
- ✅ Implemented 24/7 task orchestration (every 10 minutes)
- ✅ Implemented automated PR creation
- ✅ Implemented quality gates (API boundaries, descriptors, conventions)
- ✅ Implemented automated testing (unit, integration, smoke)
- ✅ Implemented 24/7 soak testing
- ✅ Implemented analytics dashboard (daily)
- ✅ Implemented cost tracking (twice daily)
- ✅ Implemented budget management ($25/day limit)
- ✅ Implemented task registry (140 tasks)

### Security

- ✅ Implemented input validation (commands, configuration)
- ✅ Implemented SQL injection prevention (prepared statements)
- ✅ Implemented rate limiting (heavy actions)
- ✅ Implemented permission checks (per command)
- ✅ Implemented dependency shading (namespace isolation)
- ✅ Implemented thread safety (main/async split)
- ✅ Implemented leak detection (database connections)
- ⏰ Needs: CodeQL security scanning
- ⏰ Needs: Dependency vulnerability scanning (OWASP)
- ⏰ Needs: Security review before 1.0.0

### Performance

- ✅ Implemented HikariCP connection pooling (25 connections)
- ✅ Implemented Caffeine caching (75k entries)
- ✅ Implemented movement sampling (reduce action spam)
- ✅ Implemented async executors (off main thread)
- ✅ Implemented Prometheus metrics collection
- ✅ Implemented leak detection (database, memory)
- ⏰ Needs: 24/7 soak testing (200+ players)
- ⏰ Needs: Stress testing (500+ players)
- ⏰ Needs: Performance profiling (JProfiler/YourKit)

---

## [0.0.1] - 2025-12-01 (Initial Scaffold)

### Added
- Initial project structure with Gradle Kotlin DSL
- Basic zakum-api module with placeholder interfaces
- Basic zakum-core module with plugin bootstrap
- Basic zakum-battlepass module scaffold
- Initial build configuration
- Initial documentation structure

---

## Version Labels

- **[Unreleased]:** Changes that are committed but not yet released
- **[X.Y.Z-SNAPSHOT]:** Current development version (unstable)
- **[X.Y.Z]:** Released version (stable)

---

## Change Categories

- **Added:** New features
- **Changed:** Changes in existing functionality
- **Deprecated:** Soon-to-be removed features
- **Removed:** Removed features
- **Fixed:** Bug fixes
- **Security:** Vulnerability fixes

---

## Next Release: 0.2.0 (Target: 2026-Q2)

### Planned Changes

#### zakum-crates
- Complete animation system (6+ animation types)
- Complete reward execution system (5+ reward types)
- Complete GUI interactions
- Complete preview system
- Add 30+ tests

#### zakum-pets
- Complete ability system (60 abilities)
- Complete GUI system (3 interfaces)
- Complete leveling system
- Complete storage system
- Add 40+ tests

#### zakum-miniaturepets
- Optimize chunk handling for 200-500 players
- Add performance metrics
- Add benchmarks

#### Testing Infrastructure
- Add 50+ unit tests for zakum-core
- Add 10+ integration tests for database
- Add 25+ end-to-end tests
- Configure test coverage reporting (JaCoCo)

#### Documentation
- Generate CONFIG.md with all configuration keys
- Generate COMMANDS.md with all commands/permissions
- Create BRIDGE_INTEGRATION.md
- Create MIGRATION_GUIDE.md
- Generate Javadoc for all public APIs

#### Security
- Run CodeQL security scanning
- Add dependency vulnerability scanning (OWASP)
- Create SECURITY.md

---

## Notes

### Version Numbering
- **Major (X):** Breaking API changes
- **Minor (Y):** New features, backward compatible
- **Patch (Z):** Bug fixes, backward compatible
- **SNAPSHOT:** Development version (unstable)

### Compatibility
- **Paper API:** 1.21.11-R0.1-SNAPSHOT (minimum)
- **Java:** 21 (minimum)
- **Gradle:** 9.3.1 (minimum)

### Breaking Changes Policy
- Breaking changes will only be introduced in major version releases
- Deprecation warnings will be provided at least one minor version before removal
- Migration guides will be provided for all breaking changes

---

**Changelog Maintained By:** Development Team  
**Last Updated:** 2026-02-18  
**Format:** [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)  
**Versioning:** [Semantic Versioning](https://semver.org/spec/v2.0.0.html)
