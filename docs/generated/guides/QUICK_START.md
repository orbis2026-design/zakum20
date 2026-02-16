# Zakum Quick Start Guide

Auto-generated quick start guide for Zakum platform development.

## Prerequisites
- Java 21
- Gradle 9.3.1+
- IntelliJ IDEA (recommended)

## Building

```bash
./gradlew build
```

## Creating a New Module

```bash
pwsh -NoProfile -ExecutionPolicy Bypass -File tools/new-plugin-module.ps1 \
  -ModuleId zakum-mymodule \
  -PluginName OrbisMyModule
```

## Running Verification Gates

```bash
./gradlew verifyPlatformInfrastructure
```

## Testing

```bash
./gradlew test
```

## More Information

- [Plugin Dev Kit](../../23-PLUGIN-DEVKIT.md)
- [Core Primer](../../27-CORE-PRIMER.md)
- [Architecture Overview](../../00-OVERVIEW.md)
