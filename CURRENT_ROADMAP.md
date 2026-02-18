# Zakum Suite - Development Roadmap

**Version:** 0.1.0-SNAPSHOT → 1.0.0  
**Target Platform:** Paper 1.21.11 | Java 21  
**Last Updated:** 2026-02-18

---

## Vision Statement

Build a modular, production-grade Minecraft plugin ecosystem that provides network-level infrastructure (core), seamless third-party integrations (bridges), and feature-rich player experiences (modules) while maintaining strict architectural boundaries and deployment reliability.

---

## Release Strategy

### Version 0.1.0-SNAPSHOT (Current)
- **Status:** Development
- **Focus:** Core infrastructure and bridge completeness
- **Target Date:** 2026-Q1

### Version 0.2.0 (Milestone 1)
- **Status:** Planned
- **Focus:** Feature module completion (zakum-crates, zakum-pets)
- **Target Date:** 2026-Q2

### Version 0.3.0 (Milestone 2)
- **Status:** Planned
- **Focus:** Wave A completion (orbis-holograms, orbis-worlds, orbis-loot)
- **Target Date:** 2026-Q2

### Version 1.0.0 (General Availability)
- **Status:** Planned
- **Focus:** Production hardening, testing, documentation
- **Target Date:** 2026-Q3

---

## Roadmap Phases

---

## Phase 1: Foundation Hardening (Weeks 1-4)

**Goal:** Stabilize core infrastructure, complete documentation baseline, verify build system.

### Week 1: Documentation & Build Verification
- [x] ✅ Create SYSTEM_STATUS_REPORT.md
- [ ] Create CURRENT_ROADMAP.md (this document)
- [ ] Create CHANGELOG.md with version history
- [ ] Create DEVELOPMENT_PLAN.md (100+ steps)
- [ ] Consolidate planning documentation (remove 5+ redundant files)
- [ ] Verify `./gradlew clean build` succeeds for all modules
- [ ] Verify `./gradlew verifyPlatformInfrastructure` passes
- [ ] Document all API endpoints in zakum-api (Javadoc)

**Deliverables:**
- ✅ SYSTEM_STATUS_REPORT.md (complete)
- ⏰ CURRENT_ROADMAP.md (this file)
- ⏰ CHANGELOG.md
- ⏰ DEVELOPMENT_PLAN.md
- ⏰ Build verification report

### Week 2: Core Testing Infrastructure
- [ ] Add JUnit 5 test infrastructure for zakum-core
- [ ] Write unit tests for ZakumApi provider
- [ ] Write unit tests for ActionBus
- [ ] Write unit tests for Database connection pooling
- [ ] Write unit tests for Entitlements cache
- [ ] Add integration tests for Flyway migrations
- [ ] Add smoke tests for plugin startup
- [ ] Configure test coverage reporting (JaCoCo)

**Deliverables:**
- ⏰ 50+ unit tests for zakum-core
- ⏰ 10+ integration tests for database
- ⏰ Test coverage report (target: 60%+)

### Week 3: Configuration & Commands Documentation
- [ ] Generate CONFIG.md with all configuration keys
- [ ] Generate COMMANDS.md with all commands/permissions
- [ ] Create BRIDGE_INTEGRATION.md for all bridges
- [ ] Create MIGRATION_GUIDE.md for users
- [ ] Document zakum-api public interfaces (Javadoc)
- [ ] Create PLUGIN_DEVELOPMENT.md guide for extending system

**Deliverables:**
- ⏰ CONFIG.md (all 23 modules documented)
- ⏰ COMMANDS.md (all commands + permissions)
- ⏰ BRIDGE_INTEGRATION.md
- ⏰ MIGRATION_GUIDE.md
- ⏰ PLUGIN_DEVELOPMENT.md

### Week 4: Security & Code Quality
- [ ] Run CodeQL security scanning
- [ ] Fix any high/critical security vulnerabilities
- [ ] Add dependency vulnerability scanning (OWASP)
- [ ] Configure Spotless or Checkstyle
- [ ] Apply code formatting to all modules
- [ ] Add Gradle task for security report generation
- [ ] Document security posture in SECURITY.md

**Deliverables:**
- ⏰ CodeQL scan results (0 high/critical issues)
- ⏰ OWASP dependency report
- ⏰ SECURITY.md document
- ⏰ Code formatting applied consistently

---

## Phase 2: Feature Completion - Crates & Pets (Weeks 5-12)

**Goal:** Complete zakum-crates and zakum-pets to production readiness.

### Week 5-6: zakum-crates - Animation System
- [ ] Implement RouletteAnimation class
- [ ] Implement ExplosionAnimation class
- [ ] Implement SpiralAnimation class
- [ ] Implement CascadeAnimation class
- [ ] Implement InstantAnimation class
- [ ] Implement WheelAnimation class
- [ ] Add animation configuration validation
- [ ] Add animation preview command
- [ ] Write unit tests for each animation type
- [ ] Write integration tests for animation engine

**Deliverables:**
- ⏰ 6+ animation types fully implemented
- ⏰ Animation configuration validator
- ⏰ 30+ tests for animation system

### Week 7-8: zakum-crates - Reward System
- [ ] Implement CommandReward executor
- [ ] Implement ItemReward executor
- [ ] Implement EffectReward executor
- [ ] Implement MoneyReward executor (Vault integration)
- [ ] Implement PermissionReward executor (LuckPerms integration)
- [ ] Add reward weight calculations
- [ ] Add reward probability engine
- [ ] Add reward history tracking
- [ ] Write unit tests for reward executors
- [ ] Write integration tests for reward system

**Deliverables:**
- ⏰ 5+ reward types fully implemented
- ⏰ Reward probability engine
- ⏰ 25+ tests for reward system

### Week 9-10: zakum-pets - Ability System (Part 1)
- [ ] Design ability framework (base classes)
- [ ] Implement 20 combat abilities (damage, healing, buffs)
- [ ] Implement 15 utility abilities (speed, flight, water breathing)
- [ ] Implement 10 passive abilities (experience boost, item pickup)
- [ ] Add ability cooldown system
- [ ] Add ability energy/mana system
- [ ] Write unit tests for ability framework
- [ ] Write integration tests for combat abilities

**Deliverables:**
- ⏰ Ability framework complete
- ⏰ 45/60 abilities implemented
- ⏰ 30+ tests for ability system

### Week 11-12: zakum-pets - Ability System (Part 2) & GUI
- [ ] Implement remaining 15 special abilities
- [ ] Implement PetInventoryGUI
- [ ] Implement PetStatsGUI
- [ ] Implement PetAbilityGUI
- [ ] Add ability upgrade system
- [ ] Add pet storage system
- [ ] Add pet trading system (if planned)
- [ ] Write unit tests for GUI interactions
- [ ] Write integration tests for pet lifecycle

**Deliverables:**
- ⏰ 60/60 abilities complete
- ⏰ 3 GUI interfaces implemented
- ⏰ 40+ tests for pet system

---

## Phase 3: Wave A Completion (Weeks 13-20)

**Goal:** Complete orbis-holograms, orbis-worlds, orbis-loot to DecentHolograms/Multiverse-Core/ExcellentCrates parity.

### Week 13-14: zakum-miniaturepets Optimization
- [ ] Profile chunk handling performance
- [ ] Implement chunk-aware spawning
- [ ] Implement despawn on chunk unload
- [ ] Implement respawn on chunk load
- [ ] Add performance metrics collection
- [ ] Test with 200+ players online
- [ ] Test with 500+ players online
- [ ] Write performance benchmarks
- [ ] Document optimization techniques

**Deliverables:**
- ⏰ Optimized chunk handling
- ⏰ Performance benchmarks (200-500 players)
- ⏰ Performance documentation

### Week 15-16: orbis-holograms - Core Implementation
- [ ] Implement HologramService API
- [ ] Implement TextDisplay packet integration
- [ ] Implement line management system
- [ ] Implement hologram persistence (database)
- [ ] Implement hologram CRUD operations
- [ ] Add PlaceholderAPI integration
- [ ] Add per-player visibility
- [ ] Write unit tests for service layer
- [ ] Write integration tests for persistence

**Deliverables:**
- ⏰ HologramService complete
- ⏰ Persistence layer complete
- ⏰ 30+ tests for hologram system

### Week 17-18: orbis-holograms - Animation & Commands
- [ ] Implement hologram animations (rainbow, wave, scroll)
- [ ] Implement `/hologram create` command
- [ ] Implement `/hologram delete` command
- [ ] Implement `/hologram edit` command
- [ ] Implement `/hologram teleport` command
- [ ] Implement `/hologram list` command
- [ ] Add tab completion for all commands
- [ ] Add permission checks for all commands
- [ ] Write integration tests for commands

**Deliverables:**
- ⏰ 3+ animation types
- ⏰ 5+ commands fully functional
- ⏰ Tab completion + permissions

### Week 19-20: orbis-worlds - Core Implementation
- [ ] Implement WorldService API
- [ ] Implement world creation/deletion
- [ ] Implement world import/export
- [ ] Implement world templates
- [ ] Implement per-world game rules
- [ ] Implement world teleportation
- [ ] Implement world-specific permissions
- [ ] Add world backup system
- [ ] Write unit tests for service layer
- [ ] Write integration tests for world operations

**Deliverables:**
- ⏰ WorldService complete (Multiverse-Core parity)
- ⏰ 8+ world management features
- ⏰ 40+ tests for world system

---

## Phase 4: Production Hardening (Weeks 21-28)

**Goal:** Testing, optimization, documentation, and deployment preparation.

### Week 21-22: Integration Testing & Smoke Testing
- [ ] Write end-to-end tests for BattlePass progression
- [ ] Write end-to-end tests for Crates opening
- [ ] Write end-to-end tests for Pets lifecycle
- [ ] Write end-to-end tests for Holograms display
- [ ] Write end-to-end tests for Worlds management
- [ ] Set up automated smoke testing (plugin startup)
- [ ] Set up automated smoke testing (commands)
- [ ] Set up automated smoke testing (database migrations)

**Deliverables:**
- ⏰ 25+ end-to-end tests
- ⏰ Automated smoke testing suite

### Week 23-24: Performance Testing & Optimization
- [ ] Set up 24/7 soak test server (200 players)
- [ ] Run 7-day soak test with metrics collection
- [ ] Identify performance bottlenecks
- [ ] Optimize hot paths (profiling with JProfiler/YourKit)
- [ ] Optimize database queries (slow query log)
- [ ] Optimize cache eviction policies
- [ ] Run stress tests (500 players)
- [ ] Document performance characteristics

**Deliverables:**
- ⏰ 7-day soak test report
- ⏰ Performance optimization report
- ⏰ Stress test results (500 players)

### Week 25-26: Documentation Finalization
- [ ] Generate Javadoc for all public APIs
- [ ] Create user guides for each module
- [ ] Create admin guides for server setup
- [ ] Create developer guides for plugin extension
- [ ] Create troubleshooting guide
- [ ] Create FAQ document
- [ ] Create video tutorials (setup, basic usage)
- [ ] Publish documentation to wiki/website

**Deliverables:**
- ⏰ Complete Javadoc
- ⏰ 10+ user/admin guides
- ⏰ 3+ developer guides
- ⏰ FAQ + troubleshooting
- ⏰ Video tutorials

### Week 27-28: Release Preparation
- [ ] Tag version 1.0.0-RC1 (Release Candidate)
- [ ] Deploy to beta test server
- [ ] Collect beta tester feedback
- [ ] Fix critical bugs from beta testing
- [ ] Update CHANGELOG.md with all changes
- [ ] Update README.md for 1.0.0 release
- [ ] Create release notes
- [ ] Tag version 1.0.0 (General Availability)
- [ ] Publish to SpigotMC/Bukkit/PaperMC resources
- [ ] Create GitHub release with binaries

**Deliverables:**
- ⏰ Version 1.0.0 released
- ⏰ Beta test report
- ⏰ Release notes
- ⏰ Published to plugin repositories

---

## Phase 5: Post-1.0 Enhancements (Weeks 29+)

**Goal:** Continue development based on community feedback and planned features.

### Future Modules (Backlog)
- [ ] zakum-achievements - Achievement system
- [ ] zakum-jobs - Custom jobs system (or expand bridge)
- [ ] zakum-enchantments - Custom enchantments
- [ ] zakum-kits - Kit management
- [ ] zakum-economy - Advanced economy features
- [ ] orbis-loot - Loot table system (ExcellentCrates parity)
- [ ] orbis-quests - Quest system beyond BattlePass
- [ ] orbis-shops - Shop management
- [ ] orbis-auctions - Auction house
- [ ] orbis-minigames - Minigame framework

### Community Features (Based on Feedback)
- [ ] Discord integration bridge
- [ ] Web dashboard for administration
- [ ] Mobile app for remote management
- [ ] Advanced analytics and reporting
- [ ] Multi-server support (BungeeCord/Velocity)
- [ ] Cross-server data synchronization

---

## Success Criteria

### Phase 1 Success (Foundation)
- ✅ All modules build successfully
- ✅ All verification tasks pass
- ✅ Documentation baseline complete
- ✅ Test infrastructure established

### Phase 2 Success (Feature Completion)
- ✅ zakum-crates: All animations + rewards working
- ✅ zakum-pets: All 60 abilities implemented
- ✅ 100+ tests written and passing
- ✅ No high/critical bugs

### Phase 3 Success (Wave A)
- ✅ orbis-holograms: DecentHolograms parity achieved
- ✅ orbis-worlds: Multiverse-Core parity achieved
- ✅ zakum-miniaturepets: 200-500 player optimization complete
- ✅ 150+ tests written and passing

### Phase 4 Success (Production Hardening)
- ✅ 7-day soak test passed (no crashes, no leaks)
- ✅ 500-player stress test passed
- ✅ Complete user/admin documentation
- ✅ Version 1.0.0 released publicly

---

## Risk Management

### High-Risk Items

| Risk | Impact | Mitigation | Owner |
|------|--------|------------|-------|
| Animation system complexity (zakum-crates) | Schedule delay | Break into smaller iterations, 1 animation at a time | Dev Team |
| Ability system scope (zakum-pets) | Schedule delay | Prioritize core abilities first, defer special abilities | Dev Team |
| Performance at scale (500 players) | Production blocker | Early soak testing, profiling, optimization sprints | Dev Team |
| Third-party API changes (bridges) | Compatibility break | Version pinning, automated compatibility testing | Dev Team |
| Database migration failures | Data loss risk | Extensive migration testing, backup strategies | Dev Team |

### Medium-Risk Items

| Risk | Impact | Mitigation | Owner |
|------|--------|------------|-------|
| Documentation lag | User adoption | Weekly documentation sprints, automation where possible | Dev Team |
| Test coverage gaps | Bug leakage | Enforce 60% coverage minimum, focus on critical paths | Dev Team |
| Configuration complexity | User errors | Validation at startup, sane defaults, clear error messages | Dev Team |

---

## Dependencies & Blockers

### External Dependencies
- **Paper 1.21.11:** No blocker (stable release)
- **Java 21:** No blocker (widely available)
- **Third-party plugins:** Maintained by communities (monitor for breaking changes)

### Internal Dependencies
- **zakum-api stability:** Required for all feature modules (stable ✅)
- **zakum-core stability:** Required for all feature modules (stable ✅)
- **Database schema:** Required for persistence (stable ✅)
- **ActionBus:** Required for bridge integrations (stable ✅)

### Known Blockers
- None currently identified

---

## Resource Allocation

### Development Team (Recommended)
- **2-3 Core Developers:** zakum-core, zakum-api, infrastructure
- **2-3 Feature Developers:** zakum-crates, zakum-pets, Wave A modules
- **1 DevOps Engineer:** CI/CD, testing, automation
- **1 Documentation Writer:** User guides, admin guides, tutorials
- **1 QA Tester:** Manual testing, beta coordination

### Time Commitment
- **Phase 1 (4 weeks):** 2 FTE (full-time equivalent)
- **Phase 2 (8 weeks):** 4 FTE
- **Phase 3 (8 weeks):** 4 FTE
- **Phase 4 (8 weeks):** 3 FTE
- **Total:** ~28 weeks with 3-4 FTE average

### Budget
- **Development:** Time-based (FTE allocation)
- **Infrastructure:** ~$200/month (CI/CD, test servers)
- **Third-party Services:** Minimal (free tiers for most services)

---

## Communication Plan

### Weekly Status Updates
- **Format:** CHANGELOG.md updates + GitHub Discussions post
- **Content:** Completed work, blockers, next week priorities
- **Audience:** Development team, stakeholders, community

### Monthly Milestone Reviews
- **Format:** Video meeting + written summary
- **Content:** Phase progress, risk assessment, schedule adjustments
- **Audience:** Project leads, stakeholders

### Community Updates
- **Format:** Discord announcements + GitHub releases
- **Frequency:** Bi-weekly during development, weekly during beta
- **Content:** Feature previews, breaking changes, migration guides

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-02-18 | Initial roadmap creation |

---

## Appendix: Module Priority Matrix

| Module | Priority | Effort | Value | Status | Target Phase |
|--------|----------|--------|-------|--------|--------------|
| zakum-core | P0 | High | Critical | ✅ Complete | Phase 1 |
| zakum-api | P0 | Medium | Critical | ✅ Complete | Phase 1 |
| zakum-packets | P0 | Medium | High | ✅ Complete | Phase 1 |
| All 10 bridges | P0 | Medium | High | ✅ Complete | Phase 1 |
| zakum-battlepass | P1 | High | High | ✅ Complete | Phase 1 |
| orbis-essentials | P1 | Medium | Medium | ✅ Complete | Phase 1 |
| orbis-gui | P1 | Medium | High | ✅ Complete | Phase 1 |
| zakum-crates | P1 | High | High | 🚧 60% | Phase 2 (Weeks 5-8) |
| zakum-pets | P1 | Very High | High | 🚧 40% | Phase 2 (Weeks 9-12) |
| zakum-miniaturepets | P2 | Medium | Medium | 🚧 80% | Phase 3 (Weeks 13-14) |
| orbis-holograms | P2 | High | High | 🚧 30% | Phase 3 (Weeks 15-18) |
| orbis-hud | P2 | Medium | Medium | 🚧 80% | Phase 3 (Week 13) |
| orbis-worlds | P2 | Very High | High | ⏰ Planned | Phase 3 (Weeks 19-20) |
| orbis-loot | P3 | High | Medium | ⏰ Planned | Phase 5 (Backlog) |

**Priority Legend:**
- **P0:** Critical (blocker for other work)
- **P1:** High (required for 1.0.0)
- **P2:** Medium (nice to have for 1.0.0, required for 1.1.0)
- **P3:** Low (future enhancement)

---

**Roadmap Maintained By:** Development Team  
**Last Review:** 2026-02-18  
**Next Review:** Weekly (automated updates via CI/CD)
