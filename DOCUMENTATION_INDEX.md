# 📖 Quick Documentation Reference

**Last Updated:** February 18, 2026  
**Purpose:** Fast navigation to key documents

---

## 🎯 Start Here

**New to the project?** Read these in order:

1. **[README.md](README.md)** - Project overview
2. **[MODULE_STATUS.md](MODULE_STATUS.md)** ⭐ - Current module status
3. **[ROADMAP.md](ROADMAP.md)** ⭐ - Development timeline
4. **[DEVELOPMENT_STANDARD.md](DEVELOPMENT_STANDARD.md)** ⭐ - How to develop

---

## 📊 Current Status

**Where are we?**
- [MODULE_STATUS.md](MODULE_STATUS.md) - Complete module inventory (27 modules)
  - Production ready: 15 modules (56%)
  - In development: 4 modules (15%)
  - Planned: 2 modules (7%)

**What's next?**
- [ROADMAP.md](ROADMAP.md) - Phase 3 ready to begin (steps 111-120)
  - Timeline: 9 months to 1.0.0 GA (October 2026)

---

## 🛠️ Development

**How do I develop?**
- [DEVELOPMENT_STANDARD.md](DEVELOPMENT_STANDARD.md) - Ultimate Vibe-Coding Prompt
  - Platform requirements (Paper 1.21.11, Java 21)
  - Anti-hallucination rules
  - Architecture requirements
  - Workflow steps

**How do I set up IntelliJ?**
- [DEVELOPMENT-GUIDE.md](DEVELOPMENT-GUIDE.md) - IDE setup and workflows

**How do I use the API?**
- [PLUGIN_DEVELOPMENT.md](PLUGIN_DEVELOPMENT.md) - API usage guide
  - ActionBus examples
  - Database access
  - Bridge integration

---

## 📝 Configuration & Usage

**Configuration:**
- [CONFIG.md](CONFIG.md) - All configuration keys documented
- [CONFIGURATION_EXAMPLES.md](CONFIGURATION_EXAMPLES.md) - Example configs

**Commands:**
- [COMMANDS.md](COMMANDS.md) - All commands and permissions

**Bridges:**
- [BRIDGE_INTEGRATION.md](BRIDGE_INTEGRATION.md) - How to use bridge modules

---

## 📚 Reference

**Version History:**
- [CHANGELOG.md](CHANGELOG.md) - What changed when

**Dependencies:**
- [DEPENDENCY-MANIFEST.md](DEPENDENCY-MANIFEST.md) - Complete dependency list

**Migration:**
- [MIGRATION_GUIDE.md](MIGRATION_GUIDE.md) - How to upgrade

**Security:**
- [SECURITY.md](SECURITY.md) - Security policy and reporting

**Releases:**
- [RELEASE_NOTES.md](RELEASE_NOTES.md) - Release documentation

---

## 🏛️ Architecture

**Deep Dives:**
- [docs/00-OVERVIEW.md](docs/00-OVERVIEW.md) - Architecture overview
- [docs/01-MODULES.md](docs/01-MODULES.md) - Module structure
- [docs/02-THREADING.md](docs/02-THREADING.md) - Threading model
- [docs/03-CONFIG.md](docs/03-CONFIG.md) - Configuration system
- [docs/04-ACTIONS.md](docs/04-ACTIONS.md) - ActionBus details
- [docs/05-BRIDGES.md](docs/05-BRIDGES.md) - Bridge architecture
- [docs/06-DATABASE.md](docs/06-DATABASE.md) - Database layer
- [docs/07-OBSERVABILITY.md](docs/07-OBSERVABILITY.md) - Metrics and monitoring
- [docs/08-PACKETS.md](docs/08-PACKETS.md) - Packet manipulation

**More:** See [docs/](docs/) directory for 29 architecture documents

---

## 🎯 By Task

### I want to...

**...understand the project structure**
→ [README.md](README.md) + [MODULE_STATUS.md](MODULE_STATUS.md)

**...know what modules exist and their status**
→ [MODULE_STATUS.md](MODULE_STATUS.md)

**...see the development timeline**
→ [ROADMAP.md](ROADMAP.md)

**...implement a new feature**
→ [DEVELOPMENT_STANDARD.md](DEVELOPMENT_STANDARD.md)

**...set up my development environment**
→ [DEVELOPMENT-GUIDE.md](DEVELOPMENT-GUIDE.md)

**...use the zakum-api**
→ [PLUGIN_DEVELOPMENT.md](PLUGIN_DEVELOPMENT.md)

**...configure a module**
→ [CONFIG.md](CONFIG.md) + [CONFIGURATION_EXAMPLES.md](CONFIGURATION_EXAMPLES.md)

**...use a command**
→ [COMMANDS.md](COMMANDS.md)

**...integrate with a third-party plugin**
→ [BRIDGE_INTEGRATION.md](BRIDGE_INTEGRATION.md)

**...upgrade from an older version**
→ [MIGRATION_GUIDE.md](MIGRATION_GUIDE.md)

**...report a security issue**
→ [SECURITY.md](SECURITY.md)

**...understand the architecture**
→ [docs/00-OVERVIEW.md](docs/00-OVERVIEW.md)

**...see what changed**
→ [CHANGELOG.md](CHANGELOG.md)

**...know what dependencies are used**
→ [DEPENDENCY-MANIFEST.md](DEPENDENCY-MANIFEST.md)

---

## 🔥 Most Important Documents

**Top 5 Must-Read:**

1. **[MODULE_STATUS.md](MODULE_STATUS.md)** ⭐⭐⭐
   - Complete module inventory
   - Implementation status
   - Dependencies
   - Recommendations

2. **[ROADMAP.md](ROADMAP.md)** ⭐⭐⭐
   - 9-month timeline
   - Phase-by-phase plan
   - Success criteria

3. **[DEVELOPMENT_STANDARD.md](DEVELOPMENT_STANDARD.md)** ⭐⭐⭐
   - Development format
   - Anti-hallucination rules
   - Implementation workflow

4. **[README.md](README.md)** ⭐⭐
   - Project overview
   - Quick start
   - Module summary

5. **[DEVELOPMENT-GUIDE.md](DEVELOPMENT-GUIDE.md)** ⭐⭐
   - IDE setup
   - Build commands
   - Debugging

---

## 📂 Documentation Categories

### Primary (6 files)
Essential documents everyone should read:
- README.md
- MODULE_STATUS.md ⭐
- ROADMAP.md ⭐
- DEVELOPMENT_STANDARD.md ⭐
- CHANGELOG.md
- PROJECT_COMPLETE.md (historical)

### Configuration & Usage (4 files)
How to configure and use the system:
- CONFIG.md
- COMMANDS.md
- CONFIGURATION_EXAMPLES.md
- BRIDGE_INTEGRATION.md

### Developer Guides (4 files)
How to develop and extend:
- DEVELOPMENT-GUIDE.md
- PLUGIN_DEVELOPMENT.md
- DEPENDENCY-MANIFEST.md
- MIGRATION_GUIDE.md

### Operations (3 files)
Deployment and security:
- SECURITY.md
- RELEASE_NOTES.md
- AUTOMATION_SYSTEM.md

### Strategic (3 files)
Planning and reference:
- IRIDIUM-REPLICATION-SUMMARY.md
- IRIDIUM-REPLICATION-CHECKLIST.md
- PHASE3_TESTING_REPORT.md

### Architecture (30+ files)
Deep technical documentation:
- docs/00-OVERVIEW.md through docs/28-*.md
- Module-specific READMEs

---

## 🚀 Quick Start Commands

### Build
```bash
# Full build
./gradlew clean build

# Single module
./gradlew :zakum-core:build

# Verification
./gradlew verifyApiBoundaries
./gradlew verifyPluginDescriptors
```

### Test
```bash
# Run all tests
./gradlew test

# Run specific module tests
./gradlew :zakum-core:test
```

### Security
```bash
# Scan dependencies for CVEs
./gradlew dependencyCheckAnalyze
```

---

## 💡 Tips

**Finding Information:**
- Use Ctrl+F / Cmd+F to search within documents
- Check MODULE_STATUS.md for module-specific info
- Check ROADMAP.md for timeline and planning
- Check docs/ for deep architecture details

**Contributing:**
- Follow DEVELOPMENT_STANDARD.md format
- Update MODULE_STATUS.md if adding/changing modules
- Update ROADMAP.md if adjusting timeline
- Update CHANGELOG.md for all changes

**Staying Current:**
- MODULE_STATUS.md is the single source of truth for module status
- ROADMAP.md is the single source of truth for timeline
- Check "Last Updated" dates on documents

---

## 📌 Document Status

| Document | Status | Last Updated | Importance |
|----------|--------|--------------|------------|
| MODULE_STATUS.md | ⭐ Current | 2026-02-18 | Critical |
| ROADMAP.md | ⭐ Current | 2026-02-18 | Critical |
| DEVELOPMENT_STANDARD.md | ⭐ Current | 2026-02-18 | Critical |
| README.md | ✅ Current | 2026-02-18 | High |
| DEVELOPMENT-GUIDE.md | ✅ Current | Recent | High |
| PLUGIN_DEVELOPMENT.md | ✅ Current | Recent | High |
| CHANGELOG.md | ✅ Current | Active | High |
| CONFIG.md | ✅ Current | Recent | Medium |
| COMMANDS.md | ✅ Current | Recent | Medium |

---

## 🎓 Learning Path

**Day 1:** Understand the project
1. Read README.md
2. Read MODULE_STATUS.md
3. Browse ROADMAP.md

**Day 2:** Set up development
1. Follow DEVELOPMENT-GUIDE.md
2. Build the project
3. Run tests

**Day 3:** Learn the architecture
1. Read docs/00-OVERVIEW.md
2. Read docs/01-MODULES.md
3. Read docs/05-BRIDGES.md

**Day 4:** Start developing
1. Read DEVELOPMENT_STANDARD.md
2. Read PLUGIN_DEVELOPMENT.md
3. Pick a task from ROADMAP.md

**Week 2+:** Deep dive
- Read remaining docs/ files
- Explore module source code
- Contribute features

---

## 📞 Getting Help

**Documentation Issues:**
- Check if document is current (Last Updated date)
- Search for topic in multiple documents
- Check DOCUMENTATION_AUDIT_COMPLETE.md for recent changes

**Development Questions:**
- Check DEVELOPMENT_STANDARD.md for standards
- Check PLUGIN_DEVELOPMENT.md for API usage
- Check module-specific README files

**Status Questions:**
- Check MODULE_STATUS.md for module status
- Check ROADMAP.md for timeline
- Check CHANGELOG.md for recent changes

---

**Quick Tip:** Bookmark this file for fast access to all documentation! 🔖
