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
- `commandAlias` support for friendly menu ids
- basic click events:
  - `open-gui`
  - `open-context` (open GUI with extra context variables)
  - `message`
  - `close-inventory`
- advanced click events:
  - `command` (player/console execution)
  - `ace-trigger` (fire ACE scripts)
- layout helpers:
  - `empty-fill` (background fill)
  - `item-flags` (per-item ItemFlag support)
- multi-scene menus:
  - `scenes` with `delay` (tick-based transitions)
- seeded system menus:
  - `system.root`
  - `battlepass.main`
  - `battlepass.rewards`
  - `crates.main`
  - `crates.preview`
  - `pets.main`
  - `pets.mini`
  - `profile.main`
  - `social.main`
  - `economy.main`
  - `settings.main`
  - `network.status`
  - `ace.lab`
  - `cosmetics.main`

Wired flows (end-to-end):

- BattlePass menus dispatch `/battlepass` actions (menu, quests, rewards, claim).
- Crates preview dispatches `/ocrates open <crateId>`.
- Pets menus dispatch `/opets` and `/ominipets` actions.

Known planned gaps for full InfiniteGUI parity:

- richer click-event chain model
- chat fetcher workflow graph
- player/offline selectors
- in-game editor surface
- advanced item rendering (textures/enchants/custom fill modes)

## Context placeholders (dynamic)

OrbisGUI injects common placeholders automatically; these can be used in titles,
item names, lore, and click-event context values:

- `%player%`, `%player_name%`, `%player_uuid%`
- `%server_id%`, `%online%`, `%tps%`
- `%world%`, `%x%`, `%y%`, `%z%`, `%ping%`
- `%rank%`, `%discord_linked%`, `%discord_id%`
- `%balance%`
- `%friends%`, `%allies%`, `%rivals%`
- `%level%`, `%health%`, `%food%`
- any `open-context` keys passed between menus (ex: `%crate_id%`, `%crate_name%`, `%crate_key%`)

## Recommendation

Treat OrbisGUI as a **Tier-1 platform plugin** in deployment (load with Zakum on all gameplay servers), while keeping it modular in code.
