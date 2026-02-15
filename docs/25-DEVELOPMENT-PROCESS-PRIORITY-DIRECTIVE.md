# Development Process Priority Directive (2026-02-15)

Purpose: prioritize required development processes that must be done first to
support all downstream plugin/module work.

## Overall Rule

Process reliability is treated as a product feature.
No new feature work should bypass these mandatory process gates.

## Point Board (Must-First Order)

1. **30 pts** Mandatory verification gates (required first)
   - API boundary checks
   - plugin descriptor checks
   - module build-script convention checks
2. **20 pts** Dependency/version convergence
   - no hardcoded Paper API coordinates in module scripts
   - `${version}` expansion for all plugin descriptors
3. **20 pts** Module bootstrap/scaffolding standard
   - one-command module generator
   - standard Zakum plugin bootstrap base class
4. **15 pts** Capability-first architecture policy
   - OSS absorption / non-replication directive
   - explicit integration strategy (bridge > rebuild)
5. **10 pts** Developer runbook + definition-of-ready
   - canonical verify command
   - module readiness checklist
6. **5 pts** Packaging resilience follow-up
   - investigate/mitigate intermittent `shadowJar` cache packaging failure

## Execution Status

- [x] 30/30 Mandatory verification gates:
  - `verifyApiBoundaries`
  - `verifyPluginDescriptors`
  - `verifyModuleBuildConventions`
  - aggregate: `verifyPlatformInfrastructure`
- [x] 20/20 Dependency/version convergence:
  - bridge + essentials module scripts moved to `libs.paper.api`
  - legacy plugin descriptors normalized to `version: ${version}`
- [x] 20/20 Module bootstrap/scaffolding standard:
  - `tools/new-plugin-module.ps1`
  - `ZakumPluginBase`
- [x] 15/15 Capability-first architecture policy:
  - `docs/24-OSS-ABSORPTION-DIRECTIVE.md`
- [x] 10/10 Developer runbook:
  - `docs/23-PLUGIN-DEVKIT.md`
- [ ] 0/5 Packaging resilience follow-up:
  - `:zakum-core:shadowJar` intermittently fails due local dependency zip-entry issue
  - tracked as infrastructure follow-up (non-blocking for compile/test/process gates)

**Current process score: 95 / 100**
