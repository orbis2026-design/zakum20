# Plugin Dev Kit

This doc defines the standard flow for creating new Zakum-compatible modules.

## 1) Generate a Module

Run from repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/new-plugin-module.ps1 `
  -ModuleId zakum-achievements `
  -PluginName OrbisAchievements
```

Dry-run preview:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/new-plugin-module.ps1 `
  -ModuleId zakum-achievements `
  -PluginName OrbisAchievements `
  -DryRun
```

Bridge-style package namespace:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/new-plugin-module.ps1 `
  -ModuleId zakum-bridge-example `
  -PluginName ZakumBridgeExample `
  -Kind bridge
```

## 2) What Gets Generated

- `build.gradle.kts` with `zakum-api` + Paper compile-only dependencies.
- `src/main/resources/plugin.yml` with `${version}` expansion and Zakum dependency.
- `src/main/resources/config.yml` starter config.
- `src/main/java/.../<MainClass>.java` starter plugin class.
- `README.md` module notes.
- `settings.gradle.kts` include entry (unless `-SkipSettingsUpdate` is used).

## 3) Runtime Contract

Generated modules use `ZakumPluginBase`:

- Zakum API service resolution is enforced at startup.
- Missing Zakum disables the module safely.
- Optional and required service helpers are available through:
  - `optionalService(...)`
  - `requiredService(...)`

## 4) Verification Gates

Run:

```bash
./gradlew verifyPlatformInfrastructure
```

This executes:

- `verifyApiBoundaries`: no `net.orbis.zakum.core` imports in feature modules.
- `verifyPluginDescriptors`: plugin descriptors contain required keys and Zakum dependency declaration.
- `verifyModuleBuildConventions`: plugin modules use shared Paper alias/version expansion conventions.

Shortcut script:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-process-gates.ps1 -IncludeApiTests -IncludeCoreCompile
```

## 5) Definition of Ready for New Modules

- Compiles with `./gradlew :<module>:build`
- Passes `./gradlew verifyPlatformInfrastructure`
- Uses `zakum-api` contracts for cross-module services
- Avoids direct core imports in feature module code
