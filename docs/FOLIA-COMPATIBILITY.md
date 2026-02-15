# Folia Compatibility Guide

## What is Folia?

Folia is Paper's experimental fork that brings multi-threaded region-based ticking to Minecraft servers. Unlike traditional Paper/Spigot servers that run everything on a single main thread, Folia splits the world into independent regions that tick in parallel on separate threads.

## Why Folia Matters

**Performance Benefits**:
- Scales across multiple CPU cores
- Better performance for high player counts (500+)
- Reduced TPS lag from localized activity
- More efficient resource utilization

**Architectural Changes**:
- No single "main thread" - each region has its own thread
- Entities and chunks belong to specific regions
- Cross-region operations require careful coordination
- Traditional scheduler APIs don't work the same way

## Core Concepts

### 1. Regions

The world is divided into regions (typically 8x8 chunks each). Each region:
- Runs on its own virtual thread
- Ticks independently
- Has its own scheduler
- Cannot directly access entities/blocks in other regions

### 2. Thread Ownership

Every entity and chunk belongs to exactly one region thread:

```java
// ✅ GOOD: Entity operations on entity's thread
entity.getScheduler().run(plugin, task -> {
  entity.setHealth(20.0);
  entity.teleport(location);
}, null);

// ❌ BAD: Direct access from wrong thread
entity.setHealth(20.0); // May crash if called from wrong region!
```

### 3. Schedulers

Folia provides different scheduler types:

- **Entity Scheduler**: For entity-specific operations
- **Region Scheduler**: For location-specific operations
- **Global Scheduler**: For truly global operations (rare)
- **Async Scheduler**: For I/O and computation (unchanged)

## Zakum Scheduler Abstraction

The `ZakumScheduler` interface provides a compatibility layer:

```java
public interface ZakumScheduler {
  // Async operations (unchanged from Bukkit)
  void runAsync(Runnable task);
  Executor asyncExecutor();
  
  // Folia-compatible regional operations
  void runAtEntity(Entity entity, Runnable task);
  void runAtLocation(Location loc, Runnable task);
  
  // Global operations
  void runGlobal(Runnable task);
  
  // Legacy compatibility (avoid for new code)
  int runTask(Plugin owner, Runnable task);
  int runTaskLater(Plugin owner, Runnable task, long delayTicks);
}
```

## Migration Patterns

### Entity Operations

```java
// ❌ OLD: Bukkit global scheduler
Bukkit.getScheduler().runTask(plugin, () -> {
  player.setHealth(20.0);
});

// ✅ NEW: Entity scheduler
zakumScheduler.runAtEntity(player, () -> {
  player.setHealth(20.0);
});

// Or using Paper's entity scheduler directly:
player.getScheduler().run(plugin, task -> {
  player.setHealth(20.0);
}, null);
```

### World/Block Operations

```java
// ❌ OLD: Bukkit global scheduler
Bukkit.getScheduler().runTask(plugin, () -> {
  world.setBlockData(location, blockData);
});

// ✅ NEW: Region scheduler
zakumScheduler.runAtLocation(location, () -> {
  world.setBlockData(location, blockData);
});

// Or using Paper's region scheduler directly:
Bukkit.getRegionScheduler().run(plugin, location, task -> {
  world.setBlockData(location, blockData);
});
```

### Async Operations (Unchanged)

```java
// ✅ GOOD: Async operations work the same
zakumScheduler.runAsync(() -> {
  PlayerData data = database.loadPlayer(uuid);
  
  // Then switch to entity thread
  zakumScheduler.runAtEntity(player, () -> {
    applyData(player, data);
  });
});
```

## Common Patterns

### 1. Entity Interaction

```java
// ✅ GOOD: Both entities on their respective threads
zakumScheduler.runAtEntity(attacker, () -> {
  // Attacker is safe to access here
  
  zakumScheduler.runAtEntity(target, () -> {
    // Target is safe to access here
    target.damage(5.0, attacker);
  });
});
```

### 2. Particle Effects

```java
// ✅ GOOD: Particles at location
zakumScheduler.runAtLocation(location, () -> {
  world.spawnParticle(Particle.FLAME, location, 50);
});
```

### 3. Multiple Entity Updates

```java
// ✅ GOOD: Update each entity on its thread
for (Entity entity : nearbyEntities) {
  zakumScheduler.runAtEntity(entity, () -> {
    entity.setGlowing(true);
  });
}
```

### 4. Teleportation

```java
// ✅ GOOD: Schedule teleport on entity thread
zakumScheduler.runAtEntity(player, () -> {
  player.teleport(destination);
});
```

### 5. Block Breaking

```java
// ✅ GOOD: Block operations at location
zakumScheduler.runAtLocation(blockLocation, () -> {
  block.setType(Material.AIR);
  world.dropItemNaturally(blockLocation, itemStack);
});
```

## Anti-Patterns to Avoid

### 1. Global Loops Over Entities

```java
// ❌ BAD: Assumes all entities can be accessed together
for (Player player : Bukkit.getOnlinePlayers()) {
  player.setHealth(20.0); // CRASHES on Folia!
}

// ✅ GOOD: Schedule each entity separately
for (Player player : Bukkit.getOnlinePlayers()) {
  zakumScheduler.runAtEntity(player, () -> {
    player.setHealth(20.0);
  });
}
```

### 2. Synchronous World Queries

```java
// ❌ BAD: Blocking world access
List<Entity> entities = world.getNearbyEntities(location, 10, 10, 10);
for (Entity entity : entities) {
  entity.remove(); // Wrong thread context!
}

// ✅ GOOD: Query and process on region thread
zakumScheduler.runAtLocation(location, () -> {
  List<Entity> entities = world.getNearbyEntities(location, 10, 10, 10);
  for (Entity entity : entities) {
    zakumScheduler.runAtEntity(entity, () -> {
      entity.remove();
    });
  }
});
```

### 3. Cross-Region Chunk Loading

```java
// ❌ BAD: Synchronous chunk loading
Chunk chunk = world.getChunkAt(x, z); // May not be loaded!

// ✅ GOOD: Async chunk loading
world.getChunkAtAsync(x, z).thenAccept(chunk -> {
  zakumScheduler.runAtLocation(chunk.getBlock(0, 64, 0).getLocation(), () -> {
    // Process chunk data
  });
});
```

### 4. Entity Collection Mutation

```java
// ❌ BAD: Collecting entities across regions
Map<UUID, Entity> entityCache = new HashMap<>();
for (Entity entity : world.getEntities()) {
  entityCache.put(entity.getUniqueId(), entity); // Entities from different threads!
}

// ✅ GOOD: Store UUIDs, access entities on-demand
Set<UUID> entityIds = new ConcurrentHashMap<>().newKeySet();
for (Entity entity : world.getEntities()) {
  entityIds.add(entity.getUniqueId());
}
```

## Plugin Lifecycle

### Startup

```java
@Override
public void onZakumEnable(ZakumApi zakum) {
  // Plugin startup is on global thread
  // Schedule region-specific tasks as needed
  
  zakumScheduler.runGlobal(() -> {
    loadGlobalConfiguration();
  });
}
```

### Shutdown

```java
@Override
public void onZakumDisable(ZakumApi zakum) {
  // Clean up per-region resources
  // Cancel all scheduled tasks
  
  for (Player player : Bukkit.getOnlinePlayers()) {
    zakumScheduler.runAtEntity(player, () -> {
      savePlayerData(player);
    });
  }
}
```

## Event Handling

Events in Folia fire on the region thread where the event occurred:

```java
@EventHandler
public void onPlayerMove(PlayerMoveEvent event) {
  // This event fires on player's region thread
  Player player = event.getPlayer();
  
  // Safe to access player here
  player.sendMessage("You moved!");
  
  // For other entities, schedule on their thread
  for (Entity nearby : player.getNearbyEntities(5, 5, 5)) {
    zakumScheduler.runAtEntity(nearby, () -> {
      nearby.setGlowing(true);
    });
  }
}
```

## Performance Considerations

### Task Ownership

Track scheduled tasks for cleanup:

```java
private final Map<UUID, ScheduledTask> playerTasks = new ConcurrentHashMap<>();

public void startPlayerTask(Player player) {
  ScheduledTask task = player.getScheduler().runAtFixedRate(
    plugin,
    scheduledTask -> {
      // Repeating task
    },
    null,
    1L, 20L
  );
  
  playerTasks.put(player.getUniqueId(), task);
}

public void stopPlayerTask(Player player) {
  ScheduledTask task = playerTasks.remove(player.getUniqueId());
  if (task != null) {
    task.cancel();
  }
}
```

### Batching Regional Operations

```java
// ✅ GOOD: Batch operations by region
Map<Location, List<Runnable>> regionTasks = new HashMap<>();

for (Location loc : locations) {
  regionTasks.computeIfAbsent(loc, k -> new ArrayList<>())
    .add(() -> processLocation(loc));
}

for (Map.Entry<Location, List<Runnable>> entry : regionTasks.entrySet()) {
  zakumScheduler.runAtLocation(entry.getKey(), () -> {
    for (Runnable task : entry.getValue()) {
      task.run();
    }
  });
}
```

## Testing Folia Compatibility

### Local Testing

1. Build your plugin
2. Run on Folia server locally
3. Monitor console for threading errors
4. Test with multiple players in different regions

### Verification Checklist

- [ ] No direct entity/world access from async threads
- [ ] All entity operations use entity scheduler
- [ ] All world operations use region scheduler
- [ ] No shared mutable state across regions
- [ ] Tasks are cancelled on plugin disable
- [ ] Events handle cross-region interactions correctly

## Common Errors

### "Entity not in this thread's region"

```
Attempted to access entity from wrong thread
```

**Fix**: Use `runAtEntity()` to access the entity on its thread.

### "Chunk not loaded"

```
Chunk not loaded exception
```

**Fix**: Use async chunk loading with `getChunkAtAsync()`.

### "Concurrent modification"

```
ConcurrentModificationException in entity loop
```

**Fix**: Use `CopyOnWriteArrayList` or schedule each entity separately.

## Bukkit API Compatibility

### What Still Works

- Async operations
- Event system (fires on correct thread)
- Service manager
- Configuration API
- Permissions
- Logging

### What Doesn't Work

- `Bukkit.getScheduler().runTask()` (use region schedulers)
- Synchronous chunk loading in some contexts
- Assumptions about single main thread
- Direct entity access from arbitrary threads

## Summary

✅ **Folia-Safe Patterns**:
- Use `runAtEntity()` for entity operations
- Use `runAtLocation()` for world/block operations
- Use `runAsync()` for I/O (unchanged)
- Schedule cross-region operations correctly
- Use thread-safe collections
- Clean up tasks on disable

❌ **Folia-Unsafe Patterns**:
- Global scheduler for entity/world operations
- Direct entity access from wrong thread
- Synchronous cross-region operations
- Shared mutable state without synchronization
- Assuming single main thread

## Resources

- [Paper Folia Documentation](https://docs.papermc.io/folia)
- [ZakumScheduler API](../zakum-api/src/main/java/net/orbis/zakum/api/concurrent/ZakumScheduler.java)
- [Async Safety Guide](ASYNC-SAFETY-GUIDE.md)
