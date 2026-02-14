# OrbisGUI Integration (Platform UX Layer)

## Decision

GUI is a **platform service**, not a hard core runtime.

- `zakum-api` owns contracts (`GuiService`, `GuiIds`, `GuiBridge` access via `ZakumApi#getGui()`).
- `zakum-core` owns adapter wiring (`GuiBridge` -> optional `GuiService`).
- `orbis-gui` is the concrete product/runtime plugin that renders YAML menus.

This keeps core stable and lightweight while allowing UI engine upgrades without breaking feature plugins.

## Build and module shape

- Module included in `settings.gradle.kts`: `orbis-gui`
- `orbis-gui` depends on:
  - `compileOnly(project(":zakum-api"))`
  - `compileOnly(libs.paper.api)`
- Runtime assumption:
  - `Zakum` shades `zakum-api` into the core jar, so `OrbisGUI` can resolve API types at runtime.

## Integration seam in core

Core now uses `ServiceBackedGuiBridge`:

- `ZakumApi.get().getGui().openLayout(player, id, context)` tries `GuiService` first.
- If `OrbisGUI` is absent/unavailable, it falls back to `NoopGuiBridge`.

This gives immediate compatibility for ACE effects like `[OPEN_GUI]`.

## Product plugin usage pattern

Feature plugins should resolve `GuiService` via `ServicesManager` and open by id:

```java
GuiService gui = Bukkit.getServicesManager().load(GuiService.class);
if (gui != null && gui.available()) {
  gui.open(player, GuiIds.CRATES_MAIN);
}
```

No feature plugin should depend on `orbis-gui` classes directly.

## Current OrbisGUI capability level

Implemented now:

- `/gui`, `/gui open <id>`, `/gui list`, `/gui reload`
- YAML loading from `SystemMenus/` and `CustomGuis/`
- basic click events:
  - `open-gui`
  - `message`
  - `close-inventory`
- seeded system menus:
  - `system.root`
  - `battlepass.main`
  - `crates.main`

Known planned gaps for full InfiniteGUI parity:

- richer click-event chain model
- chat fetcher workflow graph
- player/offline selectors
- in-game editor surface
- advanced item rendering (textures/enchants/custom fill modes)

## Recommendation

Treat OrbisGUI as a **Tier-1 platform plugin** in deployment (load with Zakum on all gameplay servers), while keeping it modular in code.
