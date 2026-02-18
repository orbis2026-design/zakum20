# Zakum Suite - Copilot Instructions

## Project Overview
Zakum is a comprehensive Minecraft plugin suite built with Java 21, Paper API (1.20.4+), and Gradle. This is a network-core library plugin ecosystem with multiple feature modules and bridge integrations.

## Tech Stack
- **Language**: Java 21 (Temurin/Adoptium recommended)
- **Build Tool**: Gradle 9.3.1 with Kotlin DSL
- **IDE**: IntelliJ IDEA 2024.1+ (REQUIRED - CLI builds are deprecated)
- **Platform**: Paper/Spigot 1.20.4+
- **Dependencies**: Paper API, PacketEvents, Micrometer, HikariCP, OkHttp, CommandAPI (optional)

## Architecture
- **zakum-api**: Public API boundary - feature modules MUST ONLY import from `net.orbis.zakum.api.*`
- **zakum-core**: Core implementation with database, actions, ACE system, observability
- **Feature Modules**: battlepass, crates, pets, miniaturepets, orbis-essentials, orbis-gui, orbis-hud, orbis-worlds, orbis-holograms, orbis-loot
- **Bridge Modules**: Integration with external plugins (MythicMobs, Jobs, SuperiorSkyblock2, LuckPerms, Vault, Votifier, Citizens, PlaceholderAPI, CommandAPI, EssentialsX)

## Critical Rules

### API Boundary
**NEVER** allow feature modules to import `net.orbis.zakum.core.*` directly.
- Feature modules MUST only use `net.orbis.zakum.api.*`
- Run `./gradlew verifyApiBoundaries` to check violations
- This is enforced by the build system

### Development Workflow
- **ALWAYS use IntelliJ IDEA** for development
- **DO NOT** create shell scripts or CLI-based build automation
- **DO** use Gradle tasks from the IDE's Gradle tab
- **DO** run "Reload All Gradle Projects" after dependency changes
- **DO** use Alt+Enter in IntelliJ to resolve missing imports

### Code Style
- Follow existing patterns in the codebase
- Use Java 21 features appropriately
- Avoid adding comments unless they explain complex logic
- Match the existing code structure and conventions
- Use the Paper API modern event system and component API

### Build Commands
- Build: `./gradlew build` (from IDE Gradle tab preferred)
- Test: `./gradlew test` (if tests exist)
- Verify API boundaries: `./gradlew verifyApiBoundaries`
- Verify platform infrastructure: `./gradlew verifyPlatformInfrastructure`

### Testing
- Tests are located in `src/test/java` within each module
- Use JUnit for unit tests when adding new test infrastructure
- Existing test infrastructure should be used as reference

### Dependencies
- All dependencies are managed in individual module `build.gradle.kts` files
- Common dependencies: Paper API, PacketEvents, Micrometer
- **ALWAYS** check for security vulnerabilities before adding dependencies
- Follow existing dependency patterns

### Database
- Core uses HikariCP for connection pooling
- Flyway for database migrations in `zakum-core/src/main/resources/db/migrations/`
- Config: `plugins/Zakum/config.yml` with database section

### Configuration
- Main config: `plugins/Zakum/config.yml`
- Each module has its own config: `plugins/<ModuleName>/config.yml`
- See `docs/13-CONFIG-FOLDERS.md` for complete config structure
- See `docs/03-CONFIG.md` for configuration options

### Documentation
- Keep documentation in the `docs/` directory
- Follow existing documentation structure
- Update relevant docs when making changes to features
- Documentation style: Clear, concise, with code examples where helpful

### Modules to Know
- **Actions System**: Event-driven architecture for tracking player actions
- **ACE System**: Action-Condition-Effect scripting engine
- **Observability**: Micrometer-based metrics and monitoring
- **Bridges**: Plugin integrations for ecosystem compatibility
- **OrbisGUI**: Menu system with YAML-defined menus

### File Structure
```
zakum20/
├── .github/           # GitHub workflows and automation
├── docs/              # Comprehensive documentation
├── zakum-api/         # Public API (feature modules import from here)
├── zakum-core/        # Core implementation
├── zakum-battlepass/  # BattlePass feature
├── zakum-crates/      # Crates feature
├── zakum-pets/        # Pets feature
├── zakum-miniaturepets/ # Miniature pets feature
├── orbis-essentials/  # Essential commands (homes, warps, tpa, etc.)
├── orbis-gui/         # GUI menu system
├── orbis-hud/         # HUD and scoreboard system
├── orbis-worlds/      # World management
├── orbis-holograms/   # Hologram system
├── orbis-loot/        # Loot table system
└── zakum-bridge-*/    # Bridge modules for third-party integrations
```

### When Modifying Code
1. **Minimal Changes**: Make the smallest possible changes to achieve the goal
2. **Verify Builds**: Always build the affected modules after changes
3. **Check Boundaries**: Run `verifyApiBoundaries` if touching API or feature modules
4. **Update Docs**: Update documentation if changing public APIs or features
5. **Test**: Test your changes manually or with existing tests

### Common Pitfalls to Avoid
- Don't break the API boundary between zakum-api and zakum-core
- Don't use CLI builds - always use IntelliJ
- Don't add dependencies without checking security advisories
- Don't modify working code unnecessarily
- Don't change configuration file locations or structures
- Don't remove or modify existing tests without understanding their purpose

## Resources
- Main documentation: `docs/00-OVERVIEW.md`
- Module list: `docs/01-MODULES.md`
- Configuration guide: `docs/03-CONFIG.md`
- Development guide: `DEVELOPMENT-GUIDE.md`
- API documentation: `docs/14-CORE-API-FOUNDATION.md`
