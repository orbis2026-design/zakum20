# Async Safety Guide

## Overview

Async safety is critical for maintaining server performance and ensuring Folia compatibility. This guide outlines patterns and practices for safe asynchronous operations in the Zakum20 platform.

## Core Principles

### 1. Never Block the Main Thread

The Minecraft main thread handles:
- World ticking
- Entity updates
- Player interactions
- Event processing

**Never do on main thread**:
- Database queries
- HTTP requests
- File I/O
- Thread.sleep()
- Waiting on futures/locks
- Heavy computation

### 2. Use Appropriate Schedulers

#### ZakumScheduler API

```java
ZakumScheduler scheduler = zakum.getScheduler();

// For I/O and computation
scheduler.runAsync(() -> {
  String data = fetchFromDatabase();
  processData(data);
});

// For entity operations
scheduler.runAtEntity(entity, () -> {
  entity.setHealth(20.0);
  entity.setVelocity(new Vector(0, 1, 0));
});

// For world operations
scheduler.runAtLocation(location, () -> {
  world.setBlockData(location, blockData);
  world.spawnParticle(Particle.FLAME, location, 10);
});

// For async with result
CompletableFuture<PlayerData> future = scheduler.supplyAsync(() -> {
  return database.loadPlayerData(uuid);
});
```

## Common Patterns

### Database Operations

```java
// ✅ GOOD: Async I/O, sync world update
scheduler.runAsync(() -> {
  PlayerData data = database.loadPlayer(uuid);
  
  scheduler.runAtEntity(player, () -> {
    player.setHealth(data.health());
    player.sendMessage("Data loaded!");
  });
});

// ❌ BAD: Database on main thread
PlayerData data = database.loadPlayer(uuid); // BLOCKS!
player.setHealth(data.health());
```

### HTTP Requests

```java
// ✅ GOOD: Async HTTP
scheduler.runAsync(() -> {
  HttpResponse response = httpClient.get(url);
  String result = response.body();
  
  scheduler.runAtEntity(player, () -> {
    player.sendMessage(result);
  });
});

// ❌ BAD: HTTP on main thread
HttpResponse response = httpClient.get(url); // BLOCKS!
```

### Configuration Loading

```java
// ✅ GOOD: Load config async at startup
scheduler.runAsync(() -> {
  Config config = loadConfigFromDisk();
  
  scheduler.runGlobal(() -> {
    applyConfig(config);
  });
});

// ❌ BAD: Synchronous file I/O on enable
Config config = loadConfigFromDisk(); // BLOCKS on startup!
```

### Bulk Operations

```java
// ✅ GOOD: Process in batches
List<UUID> playerIds = getAllPlayerIds();

scheduler.runAsync(() -> {
  for (UUID id : playerIds) {
    PlayerData data = database.loadPlayer(id);
    
    // Apply changes on main thread
    scheduler.runGlobal(() -> {
      processPlayerData(id, data);
    });
  }
});

// ❌ BAD: Loop with blocking I/O on main thread
for (UUID id : playerIds) {
  PlayerData data = database.loadPlayer(id); // BLOCKS each iteration!
  processPlayerData(id, data);
}
```

## CompletableFuture Patterns

### Chaining Operations

```java
scheduler.supplyAsync(() -> {
  return database.loadPlayer(uuid);
})
.thenApplyAsync(data -> {
  return enrichPlayerData(data);
})
.thenAccept(enrichedData -> {
  scheduler.runAtEntity(player, () -> {
    applyToPlayer(player, enrichedData);
  });
});
```

### Combining Multiple Async Operations

```java
CompletableFuture<PlayerData> playerFuture = 
  scheduler.supplyAsync(() -> database.loadPlayer(uuid));

CompletableFuture<GuildData> guildFuture = 
  scheduler.supplyAsync(() -> database.loadGuild(guildId));

CompletableFuture.allOf(playerFuture, guildFuture)
  .thenRun(() -> {
    PlayerData player = playerFuture.join();
    GuildData guild = guildFuture.join();
    
    scheduler.runGlobal(() -> {
      processData(player, guild);
    });
  });
```

### Error Handling

```java
scheduler.supplyAsync(() -> {
  return database.loadPlayer(uuid);
})
.exceptionally(ex -> {
  logger.error("Failed to load player", ex);
  return getDefaultPlayerData();
})
.thenAccept(data -> {
  scheduler.runAtEntity(player, () -> {
    applyData(player, data);
  });
});
```

## Thread Safety Checklist

### Data Structures

- ✅ Use `ConcurrentHashMap` for shared state
- ✅ Use `CopyOnWriteArrayList` for rarely-modified lists
- ✅ Use `AtomicInteger/Long/Reference` for counters
- ❌ Avoid plain `HashMap`, `ArrayList` across threads
- ❌ Never share mutable objects without synchronization

### Entity/World Access

- ✅ Always access entities via `runAtEntity`
- ✅ Always access world via `runAtLocation`
- ❌ Never access entity data from async threads
- ❌ Never modify world from async threads

### Configuration

- ✅ Load config async, cache immutable snapshots
- ✅ Use records for config data
- ❌ Don't mutate config objects after creation
- ❌ Don't access config files directly on demand

## Folia-Specific Considerations

### Regional Threading

Folia splits the world into regions that run independently:

```java
// ✅ GOOD: Operations stay in entity's region
scheduler.runAtEntity(entity1, () -> {
  entity1.setHealth(20.0);
  // This is safe - same region
  nearbyEntity.damage(5.0);
});

// ⚠️ CAREFUL: Cross-region operations need special handling
scheduler.runAtEntity(entity1, () -> {
  // entity2 might be in different region!
  // Use runAtEntity for entity2 operations
  scheduler.runAtEntity(entity2, () -> {
    entity2.setHealth(10.0);
  });
});
```

### Global Operations

```java
// ❌ BAD: Global tick doesn't exist in Folia
Bukkit.getScheduler().runTask(plugin, task);

// ✅ GOOD: Use runGlobal for truly global operations
scheduler.runGlobal(() -> {
  // Runs on global region scheduler
  updateGlobalState();
});
```

## Performance Tips

### 1. Batch Operations

```java
// ✅ GOOD: Batch database operations
scheduler.runAsync(() -> {
  List<PlayerData> batch = database.loadPlayerBatch(uuids);
  
  for (PlayerData data : batch) {
    scheduler.runGlobal(() -> processData(data));
  }
});

// ❌ BAD: Individual queries
for (UUID uuid : uuids) {
  scheduler.runAsync(() -> {
    PlayerData data = database.loadPlayer(uuid);
    // ...
  });
}
```

### 2. Connection Pooling

Use HikariCP for database connections:

```java
HikariConfig config = new HikariConfig();
config.setMaximumPoolSize(10);
config.setMinimumIdle(5);
HikariDataSource dataSource = new HikariDataSource(config);
```

### 3. Circuit Breakers

Use Resilience4j for external services:

```java
CircuitBreaker breaker = CircuitBreaker.ofDefaults("http-service");

Supplier<HttpResponse> decorated = 
  CircuitBreaker.decorateSupplier(breaker, () -> httpClient.get(url));

CompletableFuture.supplyAsync(decorated, scheduler.asyncExecutor())
  .thenAccept(response -> { /* ... */ });
```

## Common Mistakes

### 1. Forgetting Context Switch

```java
// ❌ BAD: Trying to modify entity from async context
scheduler.runAsync(() -> {
  PlayerData data = database.loadPlayer(uuid);
  player.setHealth(data.health()); // CRASH! Wrong thread!
});

// ✅ GOOD: Switch back to entity thread
scheduler.runAsync(() -> {
  PlayerData data = database.loadPlayer(uuid);
  
  scheduler.runAtEntity(player, () -> {
    player.setHealth(data.health());
  });
});
```

### 2. Blocking on Futures

```java
// ❌ BAD: Blocking get() on main thread
CompletableFuture<Data> future = scheduler.supplyAsync(() -> loadData());
Data data = future.get(); // BLOCKS MAIN THREAD!

// ✅ GOOD: Use callbacks
scheduler.supplyAsync(() -> loadData())
  .thenAccept(data -> {
    // Process async
  });
```

### 3. Shared Mutable State

```java
// ❌ BAD: Shared mutable list
List<String> names = new ArrayList<>();
scheduler.runAsync(() -> names.add("Alice")); // Race condition!

// ✅ GOOD: Thread-safe collection
List<String> names = new CopyOnWriteArrayList<>();
scheduler.runAsync(() -> names.add("Alice"));
```

## Testing Async Code

```java
@Test
void testAsyncOperation() {
  CompletableFuture<Boolean> result = scheduler.supplyAsync(() -> {
    return performOperation();
  });
  
  // Wait for completion in test
  assertTrue(result.join());
}
```

## Debugging

### Enable async debugging

```java
// Log thread information
logger.info("Running on thread: {}", Thread.currentThread().getName());

// Detect main thread
if (Bukkit.isPrimaryThread()) {
  logger.warn("Unexpected main thread access");
}
```

### Thread dumps

Use `/timings` or thread dumps to identify blocking operations.

## Summary

✅ **DO**:
- Use `runAsync` for I/O and computation
- Use `runAtEntity` for entity operations
- Use `runAtLocation` for world operations
- Use `CompletableFuture` for async composition
- Use thread-safe collections
- Handle errors gracefully

❌ **DON'T**:
- Block the main thread
- Access entities/world from async threads
- Use `Thread.sleep()` or blocking waits
- Share mutable state without synchronization
- Use `.get()` on main thread
- Use legacy `BukkitScheduler` for Folia compatibility
