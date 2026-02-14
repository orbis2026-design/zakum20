# InfiniteGUI Mapping Notes (AdvancedPlugins -> OrbisGUI)

Purpose: track functional parity targets without coupling runtime code to upstream internals.

## Core model mapping

- Upstream custom/system menus -> `CustomGuis/*.yml`, `SystemMenus/*.yml`
- Upstream menu ids -> `codeid` and shared constants in `GuiIds`
- Upstream `/gui` open flows -> `OrbisGUI` command + `GuiService#open(...)`

## Event mapping (current)

- `open-gui` -> supported
- `close-inventory` -> supported
- `message` -> supported

## Event mapping (planned)

- chainable conditional click pipelines
- chat-fetcher multi-step prompts
- money/set/remove/give actions with economy bridge
- title/actionbar/sound/particle helpers

## Conditions mapping (current)

- Minimal/basic checks through current runtime subset.

## Conditions mapping (planned)

- permission gates
- economy thresholds
- placeholder-driven boolean conditions
- typed input validators (integer/number ranges)

## UX roadmap anchors

- keep all UI definitions data-driven (YAML first)
- do not hardcode product-specific menus inside feature plugins
- preserve ServicesManager boundaries (`GuiService` only)
- ensure graceful degradation when GUI runtime is absent
