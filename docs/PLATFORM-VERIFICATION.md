# Platform Infrastructure Verification Guide

## Overview

The Zakum20 platform includes a comprehensive verification system to ensure all plugin modules comply with architectural standards, async safety patterns, and Folia compatibility requirements.

## Running Verification

### All Checks

Run all platform verification checks:

```bash
./gradlew verifyPlatformInfrastructure
```

This meta-task runs:
- API boundary validation
- Async/threading safety checks
- Configuration immutability validation
- Service resolution verification
- Folia compatibility checks
- Data schema health validation
- Plugin descriptor validation
- Module build conventions
- Shaded dependency collision audit

### Individual Checks

Run specific verification tasks:

```bash
# API boundaries only
./gradlew verifyApiBoundaries

# Async safety patterns
./gradlew verifyAsyncSafety

# Configuration immutability
./gradlew verifyConfigImmutability

# Service resolution
./gradlew verifyServiceResolution

# Folia compatibility
./gradlew verifyFoliaCompat

# Data schemas
./gradlew verifyDataSchemas
```

## Verification Components

### 1. API Boundary Compliance

**Purpose**: Ensures feature modules only import from `zakum-api` package, preventing tight coupling to core implementation.

**Checks**:
- Feature modules must not import `net.orbis.zakum.core.*`
- All dependencies must be through `zakum-api` interfaces

**Fix violations**:
- Replace core imports with API interfaces
- Move shared code to `zakum-api` if needed
- Use service lookup instead of direct class references

### 2. Async Safety

**Purpose**: Validates threading patterns for safe async operations and Folia compatibility.

**Checks**:
- Detects blocking I/O on main thread
- Identifies legacy BukkitScheduler usage
- Validates async context for operations

**Best practices**:
- Use `ZakumScheduler.runAsync()` for I/O
- Use `runAtEntity()` for entity operations
- Use `runAtLocation()` for world modifications
- Avoid `Thread.sleep()`, blocking calls on main thread

### 3. Configuration Immutability

**Purpose**: Ensures configuration classes are thread-safe and immutable.

**Checks**:
- Config classes should use Java records
- No mutable public fields
- No setter methods
- Final classes with final fields

**Best practices**:
```java
// Good: Using records
public record ServerConfig(String host, int port, boolean ssl) {}

// Good: Immutable class
public final class DatabaseConfig {
  private final String url;
  public DatabaseConfig(String url) { this.url = url; }
  public String url() { return url; }
}

// Bad: Mutable fields
public class BadConfig {
  public String setting; // mutable!
}
```

### 4. Service Resolution

**Purpose**: Validates plugins properly implement lifecycle contracts and handle dependencies.

**Checks**:
- Plugins extend `ZakumPluginBase`
- Service resolution uses null checks
- Optional vs required services handled correctly

**Best practices**:
```java
public class MyPlugin extends ZakumPluginBase {
  @Override
  protected void onZakumEnable(ZakumApi zakum) {
    // ZakumApi is guaranteed available here
    
    // Optional service
    optionalService(SomeService.class).ifPresent(service -> {
      // use service
    });
    
    // Required service
    OtherService required = requiredService(
      OtherService.class, 
      "Needed for XYZ feature"
    );
  }
}
```

### 5. Folia Compatibility

**Purpose**: Ensures code works with Folia's regional threading model.

**Checks**:
- No global scheduler usage
- Entity operations use entity schedulers
- World operations use location schedulers
- Regional operations stay within region

**Best practices**:
```java
// Good: Entity-scoped
scheduler.runAtEntity(entity, () -> {
  entity.setHealth(20.0);
});

// Good: Location-scoped
scheduler.runAtLocation(location, () -> {
  world.setBlockData(location, data);
});

// Bad: Global scheduler
Bukkit.getScheduler().runTask(plugin, () -> {
  // This won't work properly on Folia!
});
```

### 6. Data Schema Health

**Purpose**: Validates SQL schema migrations and database setup.

**Checks**:
- Flyway migration naming: `V<version>__<description>.sql`
- Migration files are valid SQL
- HikariCP configuration present
- Schema versioning consistent

**Migration naming**:
```
✓ V1__initial_schema.sql
✓ V2__add_users_table.sql
✓ V3__add_indexes.sql
✗ migration.sql (invalid)
✗ 001_schema.sql (invalid)
```

## Report Locations

After running verification, reports are generated in:

```
build/reports/platform-verification/
├── summary.txt                  # Overall summary
├── api-boundaries.txt           # API boundary results
├── async-safety.txt             # Threading safety results
├── config-immutability.txt      # Config validation results
├── service-resolution.txt       # Service resolution results
├── folia-compat.txt            # Folia compatibility results
└── data-schemas.txt            # Schema health results
```

## CI/CD Integration

The verification tasks are automatically run as part of the `check` task:

```bash
./gradlew check
```

All checks must pass before code can be merged.

## Platform Readiness Score

The verification system calculates an overall platform readiness score (0-100):

- **90-100**: Excellent - Production ready
- **75-89**: Good - Minor issues to address
- **50-74**: Fair - Significant issues require attention
- **0-49**: Poor - Critical issues must be resolved

## Troubleshooting

### Build fails on verification

1. Check specific task output for details
2. Review individual report files
3. Fix violations according to guidelines
4. Re-run verification

### False positives

Some patterns may trigger warnings incorrectly. Review the specific line mentioned and verify it's actually a problem. If it's a false positive:

- Add suppression comment if needed
- Ensure async context is clear in code
- Consider refactoring for clarity

### Module not scanned

Ensure your module:
- Has `build.gradle.kts` file
- Has `src/main/java` directory
- Is listed in `settings.gradle.kts`

## Contributing

When adding new modules:

1. Follow existing patterns
2. Run verification before committing
3. Fix all violations
4. Add appropriate tests
5. Update documentation if patterns change

## References

- [Async Safety Guide](ASYNC-SAFETY-GUIDE.md)
- [Folia Compatibility](FOLIA-COMPATIBILITY.md)
- [Paper API Documentation](https://docs.papermc.io/)
