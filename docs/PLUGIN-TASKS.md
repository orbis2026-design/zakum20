# Plugin Development Tasks

This document contains tasks for AI-driven development of Zakum plugins. Each task follows a structured format that enables automated parsing and processing.

## Task Format

```markdown
## Task: [Feature Name]
<!-- TASK_ID: unique_identifier -->
<!-- TASK_PRIORITY: high|medium|low -->
<!-- TASK_MODULE: zakum-core|orbis-gui|etc -->
<!-- TASK_LANGUAGE: java -->
<!-- TASK_STATUS: pending|in-progress|completed -->

Detailed description of what needs to be implemented, including:
- Functionality requirements
- Folia/Spigot compatibility notes
- Performance considerations
- API integration requirements
```

## Active Tasks

### Core Infrastructure

## Task: Implement Player Data Caching Layer
<!-- TASK_ID: core_001 -->
<!-- TASK_PRIORITY: high -->
<!-- TASK_MODULE: zakum-core -->
<!-- TASK_LANGUAGE: java -->
<!-- TASK_STATUS: pending -->

Create a thread-safe caching layer for player data to reduce database queries.

**Requirements:**
- Cache player data in memory with configurable TTL (default: 5 minutes)
- Support async loading and invalidation
- Thread-safe operations compatible with Folia's regionized threading
- Graceful degradation if cache is unavailable
- Metrics tracking for cache hits/misses

**Folia Compatibility:**
- Use regionized scheduler for per-player operations
- Ensure thread-safe data access patterns
- No shared mutable state across regions

**Performance:**
- Target <1ms cache lookup time
- Async loading should not block main thread
- Batch invalidation support for multiple players

---

### GUI System

## Task: Add Animation Support to OrbisGUI
<!-- TASK_ID: gui_001 -->
<!-- TASK_PRIORITY: medium -->
<!-- TASK_MODULE: orbis-gui -->
<!-- TASK_LANGUAGE: java -->
<!-- TASK_STATUS: pending -->

Implement item animation support for menu items in OrbisGUI.

**Requirements:**
- Support rotating through multiple item materials
- Configurable animation speed (ticks per frame)
- Efficient update mechanism that doesn't spam packets
- Compatible with existing menu definitions
- Add animation config section to menu YAML

**Configuration Example:**
```yaml
items:
  animated_item:
    slot: 10
    animation:
      enabled: true
      frames:
        - DIAMOND_SWORD
        - IRON_SWORD
        - STONE_SWORD
      speed: 20  # ticks per frame
```

**Folia/Spigot Compatibility:**
- Use appropriate scheduler (Folia regionized or Bukkit global)
- Batch updates when multiple animations in same view
- Cancel animation tasks on menu close

---

### Pet System

## Task: Add Pet Experience Sharing
<!-- TASK_ID: pets_001 -->
<!-- TASK_PRIORITY: low -->
<!-- TASK_MODULE: zakum-pets -->
<!-- TASK_LANGUAGE: java -->
<!-- TASK_STATUS: pending -->

Allow players to share experience between their pets.

**Requirements:**
- Command: `/pet sharexp <source-pet> <target-pet> <amount>`
- Validate pet ownership and existence
- Prevent negative experience transfers
- Emit ACE action event for experience transfer
- Update database atomically
- Add permission check: `zakum.pets.sharexp`

**Folia Compatibility:**
- Pet operations must run on owner's entity scheduler
- Database updates should be async
- Use thread-safe state management

**Performance:**
- Async database operations
- Validation checks before DB access
- Rate limiting to prevent spam

---

### World Management

## Task: Implement World Template System
<!-- TASK_ID: worlds_001 -->
<!-- TASK_PRIORITY: medium -->
<!-- TASK_MODULE: orbis-worlds -->
<!-- TASK_LANGUAGE: java -->
<!-- TASK_STATUS: pending -->

Add support for world templates that can be instantiated multiple times.

**Requirements:**
- Store world templates in `plugins/OrbisWorlds/templates/`
- Command: `/orbisworld template create <name> <source-world>`
- Command: `/orbisworld template instantiate <template> <new-world-name>`
- Support for template metadata (description, author, version)
- Efficient world copying mechanism
- Template validation before instantiation

**Template Metadata Format:**
```yaml
name: "PvP Arena"
description: "Standard PvP arena layout"
author: "Admin"
version: "1.0"
environment: NORMAL
requires_plugins: []
```

**Folia/Spigot Compatibility:**
- World operations must be executed on global region scheduler
- File I/O should be completely async
- Proper cleanup if instantiation fails

---

### Loot System

## Task: Add Conditional Loot Tables
<!-- TASK_ID: loot_001 -->
<!-- TASK_PRIORITY: high -->
<!-- TASK_MODULE: orbis-loot -->
<!-- TASK_LANGUAGE: java -->
<!-- TASK_STATUS: pending -->

Implement conditional requirements for loot table entries.

**Requirements:**
- Support multiple condition types:
  - Player permission
  - Player level/experience
  - World/region checks
  - Time-based conditions (day/night, real-world time)
  - Custom metadata checks
- Conditions use AND/OR logic
- Failed conditions should skip the loot entry
- Efficient condition evaluation

**Configuration Example:**
```yaml
loot_tables:
  rare_chest:
    entries:
      - item: DIAMOND
        weight: 10
        conditions:
          all:
            - type: permission
              value: "zakum.loot.rare"
            - type: player_level
              min: 50
      - item: GOLD_INGOT
        weight: 30
        conditions:
          any:
            - type: time
              value: "night"
            - type: world
              value: "nether"
```

**Folia/Spigot Compatibility:**
- Condition checks must be thread-safe
- No blocking operations during evaluation
- Cache permission checks when possible

---

## Completed Tasks

(Tasks will be moved here after successful merge)

---

## Task Guidelines

### For AI Processing:
1. Parse tasks using TASK_ID comments
2. Extract priority, module, and language metadata
3. Process high-priority tasks first
4. Ensure Folia compatibility for all implementations
5. Validate against project standards

### For Manual Addition:
1. Use the task format template above
2. Assign unique TASK_ID (module_NNN format)
3. Set appropriate priority based on project needs
4. Include detailed requirements and examples
5. Specify Folia/Spigot compatibility requirements
6. Add performance considerations

### Status Values:
- `pending` - Not yet started
- `in-progress` - Currently being processed by AI
- `completed` - Merged into codebase
- `blocked` - Waiting on dependencies
- `cancelled` - No longer needed
