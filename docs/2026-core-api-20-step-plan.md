# Zakum 2026 Core/API 20-Step Execute Plan

Objective: keep the project compile-stable while hardening the core/API for multi-plugin ecosystem growth on Paper 1.21.11 + Java 21.

## Scoring Model
- 5 pts: high impact compile/runtime stability tasks
- 4 pts: strong integration hardening
- 3 pts: compatibility and migration tasks
- 2 pts: verification/documentation tasks

## Steps (Ordered by Dependency + Practical Efficiency)
1. [x] (5 pts) Pin baseline toolchain/dependency versions in `gradle/libs.versions.toml` for Paper 1.21.11 + Java 21.
2. [x] (5 pts) Add required remote repositories in `build.gradle.kts` (Paper, CodeMC, PlaceholderAPI, Maven Central).
3. [x] (5 pts) Fix PacketEvents artifact coordinates to resolvable group/name for 2.5.0.
4. [x] (5 pts) Add/verify Shadow plugin usage in `zakum-core` and relocate heavy libraries.
5. [x] (4 pts) Ensure annotation processing flow is enabled in shared Gradle config (`compileOnly` extends `annotationProcessor`).
6. [x] (5 pts) Add `paper-plugin.yml` bootstrapper entry and `PluginBootstrap` implementation.
7. [x] (4 pts) Initialize early virtual-thread runtime before plugin enable (`EarlySchedulerRuntime`).
8. [x] (5 pts) Ensure scheduler abstraction is available from API and implemented Folia-safe in core.
9. [x] (4 pts) Remove direct Bukkit scheduler usage from runtime modules and route through Zakum scheduler.
10. [x] (5 pts) Harden ACE parser format to support `[EFFECT] value @TARGET {k=v}` syntax.
11. [x] (5 pts) Register baseline core effect library (message, command, title, sound, particle, money, etc.).
12. [x] (4 pts) Route battlepass reward command execution through ACE instead of hardcoded dispatch.
13. [x] (4 pts) Route crate reward command execution through ACE instead of hardcoded dispatch.
14. [x] (4 pts) Add 1.21.11 packet animation service implementation for display visuals.
15. [x] (3 pts) Add async world processor utility using scheduler region execution.
16. [x] (4 pts) Standardize item metadata helper around canonical `zakum:id` via `ZakumItem`.
17. [x] (4 pts) Update crate physical key factory to write canonical `zakum:id` + legacy compatibility metadata.
18. [x] (4 pts) Update crate key consumption to PDC-first matching and exact-key refund behavior.
19. [x] (3 pts) Ensure SQL migration resources are mapped in core resource processing.
20. [x] (2 pts) Validate full multi-module clean build (`./gradlew clean build`) and lock status.

Total executed points: 84 / 84 for this 20-step tranche.
