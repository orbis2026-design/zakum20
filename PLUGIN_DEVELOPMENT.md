# Plugin Development Guide

**Version:** 0.1.0-SNAPSHOT  
**Last Updated:** February 18, 2026  
**Audience:** Plugin developers extending Zakum

---

## Overview

This guide shows how to extend Zakum's functionality by creating plugins that integrate with the Zakum API.

---

## Table of Contents

1. [Getting Started](#getting-started)
2. [API Overview](#api-overview)
3. [Creating a Bridge Module](#creating-a-bridge-module)
4. [Using the Action System](#using-the-action-system)
5. [Economy Integration](#economy-integration)
6. [Database Access](#database-access)
7. [Best Practices](#best-practices)
8. [Example Plugins](#example-plugins)

---

## Getting Started

### Prerequisites

- Java 21 JDK
- Gradle 8.5+
- IntelliJ IDEA 2024.1.2+ (recommended)
- Paper 1.21.11 server for testing

### Project Setup

#### build.gradle.kts

```kotlin
plugins {
    `java-library`
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly(files("libs/zakum-api-0.1.0-SNAPSHOT.jar"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
```

### Directory Structure

```
my-zakum-plugin/
├── build.gradle.kts
├── settings.gradle.kts
└── src/
    └── main/
        ├── java/
        │   └── com/example/myplugin/
        │       ├── MyPlugin.java
        │       └── commands/
        └── resources/
            └── plugin.yml
```

---

## API Overview

### Accessing the Zakum API

```java
import net.orbis.zakum.api.ZakumApi;

public class MyPlugin extends JavaPlugin {
    
    private ZakumApi zakumApi;
    
    @Override
    public void onEnable() {
        // Get Zakum API instance
        zakumApi = ZakumApi.get();
        
        if (zakumApi == null) {
            getLogger().severe("Zakum not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        getLogger().info("Successfully hooked into Zakum API v" + 
            zakumApi.getVersion());
    }
}
```

### Core API Interfaces

```java
// Main API entry point
ZakumApi api = ZakumApi.get();

// Sub-APIs
ActionBus actionBus = api.getActionBus();
EconomyService economy = api.getEconomy();
EntitlementService entitlements = api.getEntitlements();
StorageService storage = api.getStorage();
```

---

## Creating a Bridge Module

### Bridge Interface

```java
package com.example.mybridge;

import net.orbis.zakum.api.ZakumApi;
import org.bukkit.plugin.java.JavaPlugin;

public class MyPluginBridge extends JavaPlugin {
    
    private ZakumApi zakumApi;
    private boolean targetPluginPresent = false;
    
    @Override
    public void onLoad() {
        // Check if target plugin is present
        targetPluginPresent = getServer().getPluginManager()
            .getPlugin("TargetPlugin") != null;
    }
    
    @Override
    public void onEnable() {
        if (!targetPluginPresent) {
            getLogger().info("TargetPlugin not found. Bridge disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Get Zakum API
        zakumApi = ZakumApi.get();
        if (zakumApi == null) {
            getLogger().severe("Zakum API not available!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Initialize bridge
        initializeBridge();
        
        getLogger().info("Bridge initialized successfully");
    }
    
    private void initializeBridge() {
        // Register listeners
        getServer().getPluginManager().registerEvents(
            new MyBridgeListener(zakumApi), this);
        
        // Subscribe to Zakum actions
        zakumApi.getActionBus().subscribe(this::handleAction);
    }
    
    private void handleAction(ActionEvent event) {
        // React to Zakum action events
        if (event.type().equals("PLAYER_KILL")) {
            // Do something with target plugin
        }
    }
}
```

### plugin.yml for Bridge

```yaml
name: MyPluginBridge
version: 1.0.0
main: com.example.mybridge.MyPluginBridge
api-version: '1.21'
depend:
  - Zakum
  - TargetPlugin
description: Bridge between Zakum and TargetPlugin
```

---

## Using the Action System

### Subscribing to Actions

```java
import net.orbis.zakum.api.actions.ActionBus;
import net.orbis.zakum.api.actions.ActionEvent;
import net.orbis.zakum.api.actions.ActionSubscription;

public class MyActionListener {
    
    private final ActionSubscription subscription;
    
    public MyActionListener(ActionBus actionBus) {
        // Subscribe to all action events
        subscription = actionBus.subscribe(this::onAction);
    }
    
    private void onAction(ActionEvent event) {
        switch (event.type()) {
            case "BLOCK_BREAK" -> handleBlockBreak(event);
            case "MOB_KILL" -> handleMobKill(event);
            case "PLAYER_LEVEL_UP" -> handleLevelUp(event);
        }
    }
    
    private void handleBlockBreak(ActionEvent event) {
        UUID playerId = event.playerId();
        long amount = event.amount(); // Number of blocks
        String blockType = event.key(); // Block material
        
        // Your logic here
        getLogger().info(playerId + " broke " + amount + "x " + blockType);
    }
    
    public void cleanup() {
        // Unsubscribe when done
        subscription.close();
    }
}
```

### Publishing Actions

```java
import net.orbis.zakum.api.actions.ActionEvent;

public class MyActionPublisher {
    
    private final ActionBus actionBus;
    
    public void publishCustomAction(Player player, String actionType) {
        ActionEvent event = new ActionEvent(
            actionType,          // Action type
            player.getUniqueId(), // Player UUID
            1L,                   // Amount (must be > 0)
            "custom_data",        // Optional key
            "custom_value"        // Optional value
        );
        
        actionBus.publish(event);
    }
}
```

### Common Action Types

| Action Type | When Fired | Data |
|------------|------------|------|
| `JOIN` | Player joins server | - |
| `QUIT` | Player leaves server | - |
| `BLOCK_BREAK` | Block broken | `key`: block type |
| `BLOCK_PLACE` | Block placed | `key`: block type |
| `MOB_KILL` | Mob killed | `key`: mob type |
| `PLAYER_DEATH` | Player dies | - |
| `PLAYER_KILL` | Player kills player | `key`: victim UUID |
| `XP_GAIN` | XP gained | `amount`: XP amount |
| `LEVEL_CHANGE` | Level up | `amount`: new level |

---

## Economy Integration

### Using the Economy Service

```java
import net.orbis.zakum.api.economy.EconomyService;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class MyEconomyIntegration {
    
    private final EconomyService economy;
    
    public MyEconomyIntegration(ZakumApi api) {
        this.economy = api.getEconomy();
    }
    
    public void rewardPlayer(UUID playerId, double amount) {
        economy.deposit(playerId, amount, "Quest reward")
            .thenAccept(success -> {
                if (success) {
                    getLogger().info("Rewarded " + playerId + " with $" + amount);
                } else {
                    getLogger().warning("Failed to reward " + playerId);
                }
            });
    }
    
    public void checkBalance(UUID playerId) {
        economy.getBalance(playerId)
            .thenAccept(balance -> {
                getLogger().info(playerId + " has balance: $" + balance);
            });
    }
    
    public void chargePlayer(UUID playerId, double amount) {
        economy.withdraw(playerId, amount, "Shop purchase")
            .thenAccept(success -> {
                if (success) {
                    getLogger().info("Charged " + playerId + " $" + amount);
                } else {
                    getLogger().warning("Insufficient funds for " + playerId);
                }
            });
    }
}
```

---

## Database Access

### Using the Storage Service

```java
import net.orbis.zakum.api.storage.StorageService;
import java.util.UUID;

public class MyDataStorage {
    
    private final StorageService storage;
    
    public void savePlayerData(UUID playerId, String data) {
        storage.saveAsync("myplugin:player:" + playerId, data)
            .thenRun(() -> {
                getLogger().info("Saved data for " + playerId);
            })
            .exceptionally(ex -> {
                getLogger().severe("Failed to save: " + ex.getMessage());
                return null;
            });
    }
    
    public CompletableFuture<String> loadPlayerData(UUID playerId) {
        return storage.loadAsync("myplugin:player:" + playerId)
            .thenApply(data -> {
                return data.orElse("default_data");
            });
    }
}
```

### Direct Database Access (Advanced)

```java
import net.orbis.zakum.api.db.ZakumDatabase;
import net.orbis.zakum.api.db.Jdbc;

public class MyDatabaseAccess {
    
    private final Jdbc jdbc;
    
    public MyDatabaseAccess(ZakumApi api) {
        ZakumDatabase db = api.getDatabase();
        this.jdbc = db.jdbc();
    }
    
    public void queryData() {
        List<MyData> results = jdbc.query(
            "SELECT * FROM my_table WHERE user_id = ?",
            rs -> new MyData(
                rs.getInt("id"),
                rs.getString("data")
            ),
            playerId
        );
        
        results.forEach(data -> {
            getLogger().info("Found: " + data);
        });
    }
    
    public void insertData(String data) {
        int rowsAffected = jdbc.update(
            "INSERT INTO my_table (data) VALUES (?)",
            data
        );
        
        getLogger().info("Inserted " + rowsAffected + " rows");
    }
}
```

---

## Best Practices

### 1. Dependency Management

**Always check for Zakum presence:**

```java
@Override
public void onEnable() {
    if (!checkDependency()) {
        getServer().getPluginManager().disablePlugin(this);
        return;
    }
    // Continue with initialization
}

private boolean checkDependency() {
    if (ZakumApi.get() == null) {
        getLogger().severe("Zakum not found!");
        return false;
    }
    return true;
}
```

### 2. Async Operations

**Always use async for I/O:**

```java
// ✅ GOOD - Async database operation
economy.getBalance(playerId)
    .thenAccept(balance -> {
        // Handle result on async thread
        processBalance(balance);
    });

// ❌ BAD - Blocking main thread
double balance = economy.getBalance(playerId).join(); // BLOCKS!
```

### 3. Resource Cleanup

**Clean up subscriptions and resources:**

```java
private ActionSubscription subscription;

@Override
public void onEnable() {
    subscription = api.getActionBus().subscribe(this::handleAction);
}

@Override
public void onDisable() {
    if (subscription != null) {
        subscription.close();
    }
}
```

### 4. Error Handling

**Always handle failures:**

```java
economy.withdraw(playerId, amount, "Purchase")
    .thenAccept(success -> {
        if (success) {
            deliverItem(playerId);
        } else {
            notifyInsufficientFunds(playerId);
        }
    })
    .exceptionally(ex -> {
        getLogger().severe("Economy error: " + ex.getMessage());
        refundPlayer(playerId);
        return null;
    });
```

### 5. Version Compatibility

**Check API version:**

```java
private boolean checkVersion() {
    ZakumApi api = ZakumApi.get();
    String version = api.getVersion();
    
    if (!version.startsWith("0.1")) {
        getLogger().warning("Untested Zakum version: " + version);
    }
    
    return true; // Or implement version checks
}
```

---

## Example Plugins

### Example 1: Quest System

```java
public class MyQuestPlugin extends JavaPlugin {
    
    private ZakumApi zakumApi;
    private QuestManager questManager;
    
    @Override
    public void onEnable() {
        zakumApi = ZakumApi.get();
        questManager = new QuestManager(zakumApi);
        
        // Listen to actions for quest progress
        zakumApi.getActionBus().subscribe(event -> {
            questManager.handleAction(event);
        });
    }
}

class QuestManager {
    private final EconomyService economy;
    
    public void handleAction(ActionEvent event) {
        if (event.type().equals("MOB_KILL")) {
            UUID playerId = event.playerId();
            String mobType = event.key();
            
            // Check quest progress
            if (hasQuest(playerId, "kill_10_zombies")) {
                incrementProgress(playerId, "kill_10_zombies");
                
                if (isQuestComplete(playerId, "kill_10_zombies")) {
                    rewardQuest(playerId, 100.0);
                }
            }
        }
    }
    
    private void rewardQuest(UUID playerId, double reward) {
        economy.deposit(playerId, reward, "Quest completion")
            .thenAccept(success -> {
                if (success) {
                    notifyPlayer(playerId, "Quest complete! +$" + reward);
                }
            });
    }
}
```

### Example 2: Custom Currency

```java
public class MyCurrencyPlugin extends JavaPlugin {
    
    private ZakumApi zakumApi;
    private Map<UUID, Long> gems = new ConcurrentHashMap<>();
    
    @Override
    public void onEnable() {
        zakumApi = ZakumApi.get();
        
        // Give gems when players earn money
        zakumApi.getActionBus().subscribe(event -> {
            if (event.type().equals("ECONOMY_TRANSACTION")) {
                UUID playerId = event.playerId();
                long amount = event.amount();
                
                // 1 gem per $100
                long gemsToGive = amount / 100;
                addGems(playerId, gemsToGive);
            }
        });
    }
    
    private void addGems(UUID playerId, long amount) {
        gems.merge(playerId, amount, Long::sum);
        
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.sendMessage("§6+§e" + amount + " gems!");
        }
    }
}
```

---

## Debugging

### Enable Debug Logging

```java
@Override
public void onEnable() {
    if (getConfig().getBoolean("debug", false)) {
        getLogger().setLevel(Level.FINE);
    }
}
```

### Common Issues

**Issue: API returns null**
- Zakum may not be loaded yet
- Check plugin load order in plugin.yml (`depend: [Zakum]`)

**Issue: Actions not received**
- Verify subscription is active
- Check action type matches exactly
- Ensure event is actually being published

**Issue: Async operations hang**
- Check database connectivity
- Verify no deadlocks in code
- Review thread pool exhaustion

---

## Resources

- **Zakum API Javadoc:** See `zakum-api/build/docs/javadoc/`
- **Example Plugins:** Check `examples/` directory
- **Source Code:** Review bridge modules for patterns
- **Support:** Join Discord for help

---

## Contributing

Want to contribute a bridge or feature?

1. Fork the repository
2. Create a feature branch
3. Follow code style guidelines
4. Add tests for new functionality
5. Submit a pull request

See [CONTRIBUTING.md](CONTRIBUTING.md) for details.

---

**Last Updated:** February 18, 2026  
**API Version:** 0.1.0-SNAPSHOT  
**Related:** [CONFIG.md](CONFIG.md) | [BRIDGE_INTEGRATION.md](BRIDGE_INTEGRATION.md)

