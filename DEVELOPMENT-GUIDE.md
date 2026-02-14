# ZAKUM SUITE - DEVELOPMENT GUIDE

## Project Structure

\\\
zakum/
├── zakum-api/              # Stable API interfaces
├── zakum-core/             # Core implementation
├── zakum-battlepass/       # ✅ 100% Complete
├── zakum-crates/           # 🚧 Generated stubs (needs completion)
├── zakum-pets/             # ⏰ Needs implementation
├── zakum-miniaturepets/    # ⚠️ Needs optimization
├── zakum-packets/          # ✅ Complete
├── zakum-bridge-*/         # ✅ All bridges complete
└── orbis-essentials/       # ✅ Complete
\\\

## Building the Project

### Using Gradle (Recommended)
\\\ash
# Build all modules
./gradlew build

# Build specific module
./gradlew :zakum-crates:build

# Run tests
./gradlew test

# Generate JAR without tests
./gradlew jar -x test
\\\

### Using Maven
\\\ash
# Build all
mvn clean package

# Skip tests
mvn clean package -DskipTests
\\\

## Development Workflow

### 1. Complete Crates Implementation
Priority files to implement:
- \CrateOpenListener.java\ - Handle crate block interactions
- \RewardExecutor.java\ - Execute reward commands/items
- \AnimationEngine.java\ - Crate opening animations
- \CrateGUI.java\ - Preview and opening menus

### 2. Implement Pets System
Structure:
\\\
zakum-pets/src/main/java/net/orbis/zakum/pets/
├── PetManager.java
├── PetInstance.java
├── abilities/
│   ├── AbilityRegistry.java
│   └── impl/  (60+ ability classes)
├── leveling/
│   └── PetXp.java
└── gui/
    └── PetInventoryGUI.java
\\\

### 3. Optimize MiniPets
Key changes needed:
- Add chunk load/unload listeners
- Implement entity count limits
- Optimize follow pathfinding

## Testing

### Local Test Server Setup
1. Copy built JARs to \	est-server/plugins/\
2. Start server: \java -jar paper.jar\
3. Monitor logs for errors
4. Test in-game with \/zakum status\

### Unit Tests
Run with: \./gradlew test\

Location: \*/src/test/java/\

## Database Setup for Development

\\\sql
CREATE DATABASE zakum_dev;
USE zakum_dev;

-- Run migrations
SOURCE zakum-core/src/main/resources/db/migration/V1__initial.sql;
SOURCE zakum-crates/src/main/resources/db/migration/V1__crates_initial_schema.sql;
\\\

## Configuration

All default configs are in:
\\\
*/src/main/resources/config.yml
\\\

Optimized defaults are now set for production use.

## Next Steps

1. ✅ Default configs optimized
2. ✅ Crates key system generated
3. ⏰ Complete remaining crates classes
4. ⏰ Implement pets system
5. ⏰ Optimize minipets
6. ⏰ Write unit tests
7. ⏰ Integration testing

## Code Standards

- Use try-with-resources for all JDBC
- Async for all DB/HTTP operations
- Sync for all Bukkit API calls
- Comment all public methods
- Keep methods under 50 lines
- Use immutable records where possible

## Support

See:
- \ZAKUM_STATUS_ANALYSIS.md\ - Current status
- \DEPLOYMENT_ROADMAP.md\ - Implementation plan
- \README_DEPLOYMENT.md\ - Production deployment guide
