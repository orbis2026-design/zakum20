# Platform Infrastructure Verification

This package contains validators for ensuring platform-wide compliance with architectural standards, threading safety, and Folia compatibility.

## Components

### Core Validators

- **ApiBoundaryValidator**: Enforces API-only imports for feature modules
- **AsyncSafetyChecker**: Detects blocking operations and validates thread safety
- **ConfigSnapshotGenerator**: Validates configuration immutability patterns
- **ServiceResolutionValidator**: Verifies plugin lifecycle and service dependencies
- **FoliaCompatibilityValidator**: Ensures Folia regional threading compliance
- **DataHealthProbe**: Validates SQL schemas and Flyway migrations

### Infrastructure

- **ValidationResult**: Standard result format for all validators
- **PlatformInfrastructureReport**: Consolidated reporting across all validators

## Usage

### From Gradle

```bash
# Run all verification checks
./gradlew verifyPlatformInfrastructure

# Run individual checks
./gradlew verifyApiBoundaries
./gradlew verifyAsyncSafety
./gradlew verifyConfigImmutability
./gradlew verifyServiceResolution
./gradlew verifyFoliaCompat
./gradlew verifyDataSchemas
```

### Programmatic Usage

```java
// API Boundary validation
ApiBoundaryValidator validator = new ApiBoundaryValidator(
    projectRoot,
    List.of("module1", "module2")
);
ValidationResult result = validator.validate();
String report = validator.generateReport();

// Full platform report
PlatformInfrastructureReport platformReport = new PlatformInfrastructureReport(
    projectRoot,
    featureModules,
    allModules
);
String fullReport = platformReport.generateFullReport();
```

## Integration

These validators are automatically run as part of the `check` task:

```bash
./gradlew check
```

All checks must pass before code can be merged to main branches.

## Documentation

- [Platform Verification Guide](../../../docs/PLATFORM-VERIFICATION.md)
- [Async Safety Guide](../../../docs/ASYNC-SAFETY-GUIDE.md)
- [Folia Compatibility Guide](../../../docs/FOLIA-COMPATIBILITY.md)
