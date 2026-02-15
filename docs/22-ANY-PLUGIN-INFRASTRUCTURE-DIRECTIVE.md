# Any-Plugin Infrastructure Directive (2026-02-15)

This directive is the execution plan for creating the infrastructure required to
ship new Zakum-compatible plugins quickly and safely.

## Source Directives

- `docs/15-REFRACTOR-DIRECTIVE-SPEC.md`
- `docs/14-CORE-API-FOUNDATION.md`
- `docs/18-CORE-BONES-DIMENSION.md`

## Overall Goal

Provide an end-to-end developer platform so a new gameplay/server plugin can be
created as a module, compile against stable contracts, and pass baseline
platform gates with minimal manual setup.

## Point Model (100 Total)

1. **20 pts** Plugin bootstrap SDK in `zakum-api`
   - standard Zakum service resolution
   - startup/shutdown lifecycle contract
   - optional/required service helpers
2. **25 pts** Module generation automation
   - one-command module scaffolding
   - generated build script, plugin descriptor, config, and main class
   - optional settings inclusion
3. **20 pts** Build-time descriptor gates
   - verify plugin descriptor keys (`name`, `version`, `main`, `api-version`)
   - enforce Zakum dependency declaration for runtime modules
4. **15 pts** Developer playbook/docs
   - exact generation commands
   - expectations for module boundaries and runtime service usage
5. **10 pts** Compatibility guard extension
   - API smoke test coverage for the bootstrap SDK contract
6. **10 pts** Validation
   - compile/test verification for touched modules
   - platform verification task execution

## Execution Status

- [x] (20/20) Added `ZakumPluginBase` to `zakum-api`.
- [x] (25/25) Added `tools/new-plugin-module.ps1`.
- [x] (20/20) Added `verifyPluginDescriptors` + `verifyPlatformInfrastructure` in root build.
- [x] (15/15) Added developer playbook in `docs/23-PLUGIN-DEVKIT.md`.
- [x] (10/10) Extended API smoke tests for plugin bootstrap contract.
- [x] (10/10) Validated with Gradle builds/tests/tasks.

**Directive score: 100 / 100**
