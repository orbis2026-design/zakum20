# Bridge Integration Guide

**Version:** 0.1.0-SNAPSHOT  
**Last Updated:** February 18, 2026  
**Supported Bridges:** 13 modules

---

## Overview

Zakum provides bridge modules for integrating with popular third-party plugins. Each bridge is optional and activates automatically when the target plugin is detected.

---

## Table of Contents

1. [PlaceholderAPI Bridge](#placeholderapi-bridge)
2. [Vault Bridge](#vault-bridge)
3. [LuckPerms Bridge](#luckperms-bridge)
4. [Citizens Bridge](#citizens-bridge)
5. [EssentialsX Bridge](#essentialsx-bridge)
6. [CommandAPI Bridge](#commandapi-bridge)
7. [MythicMobs Bridge](#mythicmobs-bridge)
8. [Jobs Reborn Bridge](#jobs-reborn-bridge)
9. [SuperiorSkyblock2 Bridge](#superiorskyblock2-bridge)
10. [RoseStacker Bridge](#rosestacker-bridge)
11. [WorldGuard Bridge](#worldguard-bridge)
12. [FastAsyncWorldEdit Bridge](#fastasyncworldedit-bridge)
13. [Votifier Bridge](#votifier-bridge)

---

## PlaceholderAPI Bridge

**Target Plugin:** [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)  
**Bridge Module:** `zakum-bridge-placeholderapi`  
**Auto-Enable:** Yes

### Setup

1. Install PlaceholderAPI
2. Install Zakum (bridge auto-loads)
3. Use placeholders in any PAPI-compatible plugin

### Available Placeholders

#### Player Placeholders

| Placeholder | Description | Example Output |
|-------------|-------------|----------------|
| `%zakum_player_level%` | Player's current level | `42` |
| `%zakum_player_xp%` | Current XP | `12450` |
| `%zakum_player_rank%` | Player's rank | `VIP` |
| `%zakum_player_balance%` | Currency balance | `10000.50` |
| `%zakum_player_souls%` | Soul currency | `350` |

#### Battle Pass Placeholders

| Placeholder | Description | Example Output |
|-------------|-------------|----------------|
| `%zakum_bp_tier%` | Current battle pass tier | `15` |
| `%zakum_bp_xp%` | Battle pass XP | `4500` |
| `%zakum_bp_progress%` | Progress to next tier | `75%` |

#### Team Placeholders

| Placeholder | Description | Example Output |
|-------------|-------------|----------------|
| `%zakum_team_name%` | Player's team name | `Warriors` |
| `%zakum_team_members%` | Team member count | `5` |
| `%zakum_team_leader%` | Team leader name | `Notch` |

### Configuration

No configuration required - bridge activates automatically.

---

## Vault Bridge

**Target Plugin:** [Vault](https://www.spigotmc.org/resources/vault.34315/)  
**Bridge Module:** `zakum-bridge-vault`  
**Auto-Enable:** Yes

### Setup

1. Install Vault
2. Install Zakum (bridge registers as economy provider)
3. Other plugins can now use Zakum's economy

### Features

- **Economy Provider:** Zakum registers as Vault economy
- **Permission Provider:** Integrates with Zakum permissions
- **Chat Provider:** Provides prefix/suffix support

### Economy Commands

When Vault bridge is active, Zakum becomes the economy backend:

```
/bal - Check balance (provided by your economy plugin)
/pay <player> <amount> - Pay another player
/eco give <player> <amount> - Admin: Give money
/eco take <player> <amount> - Admin: Take money
/eco set <player> <amount> - Admin: Set balance
```

### Developer API

```java
// Get Vault economy
Economy economy = getServer().getServicesManager()
    .getRegistration(Economy.class).getProvider();

// Use economy
economy.depositPlayer(player, 100.0);
economy.withdrawPlayer(player, 50.0);
double balance = economy.getBalance(player);
```

---

## LuckPerms Bridge

**Target Plugin:** [LuckPerms](https://luckperms.net/)  
**Bridge Module:** `zakum-bridge-luckperms`  
**Auto-Enable:** Yes

### Setup

1. Install LuckPerms
2. Install Zakum (bridge syncs automatically)
3. Permissions sync in real-time

### Features

- **Real-time sync:** Permission changes apply immediately
- **Group integration:** Zakum recognizes LP groups
- **Meta data:** Supports LP meta keys
- **Context support:** Per-world and per-server permissions

### Integration

Zakum automatically respects LuckPerms permissions:

```yaml
# LuckPerms config - use Zakum permissions
group.vip:
  permissions:
    - zakum.battlepass.boost.2x
    - zakum.crates.preview
    - zakum.pets.summon.*
```

---

## Citizens Bridge

**Target Plugin:** [Citizens](https://citizensnpcs.co/)  
**Bridge Module:** `zakum-bridge-citizens`  
**Auto-Enable:** Yes

### Setup

1. Install Citizens
2. Install Zakum (bridge enables NPC integration)
3. Create NPCs and assign Zakum traits

### Features

- **Quest NPCs:** NPCs can give quests
- **Shop NPCs:** NPCs can open shops
- **Battle Pass NPCs:** NPCs show battle pass progress
- **Crate NPCs:** NPCs manage crate openings

### Usage

```
/npc create ShopKeeper
/npc trait ZakumShopTrait
/npc trait configure shop:main
```

---

## EssentialsX Bridge

**Target Plugin:** [EssentialsX](https://essentialsx.net/)  
**Bridge Module:** `zakum-bridge-essentialsx`  
**Auto-Enable:** Yes

### Setup

1. Install EssentialsX
2. Install Zakum (bridge coordinates features)
3. Features integrate automatically

### Features

- **Economy sync:** Zakum and Essentials economies coordinate
- **Home integration:** Zakum respects Essentials homes
- **Teleport integration:** Coordinates teleport cooldowns
- **AFK detection:** Zakum pauses features for AFK players

---

## CommandAPI Bridge

**Target Plugin:** [CommandAPI](https://commandapi.jorel.dev/)  
**Bridge Module:** `zakum-bridge-commandapi`  
**Auto-Enable:** Yes

### Setup

1. Install CommandAPI
2. Install Zakum (enhanced commands activate)
3. Commands get brigadier support automatically

### Features

- **Enhanced tab completion:** Better argument suggestions
- **Type-safe commands:** Compile-time command validation
- **Better error messages:** Clear command usage hints
- **Brigadier support:** Native Minecraft command system

---

## MythicMobs Bridge

**Target Plugin:** [MythicMobs](https://www.mythicmobs.net/)  
**Bridge Module:** `zakum-bridge-mythicmobs`  
**Auto-Enable:** Yes

### Setup

1. Install MythicMobs
2. Install Zakum (mob integration activates)
3. Zakum detects MythicMobs kills

### Features

- **Kill tracking:** Zakum tracks MythicMobs kills
- **Action events:** MythicMobs kills emit actions
- **Quest integration:** MythicMobs kills count for quests
- **Battle pass XP:** Killing MythicMobs grants BP XP

### Configuration

```yaml
# In Zakum config.yml
mythicmobs:
  trackKills: true
  grantBattlePassXp: true
  xpMultiplier: 1.5
```

---

## Jobs Reborn Bridge

**Target Plugin:** [Jobs Reborn](https://www.spigotmc.org/resources/jobs-reborn.4216/)  
**Bridge Module:** `zakum-bridge-jobs`  
**Auto-Enable:** Yes

### Setup

1. Install Jobs Reborn
2. Install Zakum (job tracking activates)
3. Job actions integrate with Zakum

### Features

- **Action tracking:** Job actions emit Zakum events
- **Dual rewards:** Players get both Jobs and Zakum rewards
- **Quest integration:** Job milestones count for quests
- **Statistics:** Jobs stats tracked by Zakum

---

## SuperiorSkyblock2 Bridge

**Target Plugin:** [SuperiorSkyblock2](https://www.spigotmc.org/resources/superiorskyblock2.63905/)  
**Bridge Module:** `zakum-bridge-superiorskyblock2`  
**Auto-Enable:** Yes

### Setup

1. Install SuperiorSkyblock2
2. Install Zakum (island integration activates)
3. Island features work with Zakum

### Features

- **Island quests:** Zakum quests integrate with islands
- **Island levels:** Island upgrades grant bonuses
- **Team integration:** Zakum teams sync with islands
- **Economy integration:** Island bank coordinates with Zakum

---

## RoseStacker Bridge

**Target Plugin:** [RoseStacker](https://www.spigotmc.org/resources/rosestacker.82729/)  
**Bridge Module:** `zakum-bridge-rosestacker`  
**Auto-Enable:** Yes

### Setup

1. Install RoseStacker
2. Install Zakum (stacking coordination activates)
3. Stacked entities work correctly with Zakum

### Features

- **Kill counting:** Stacked mob kills count correctly
- **Item stacking:** Crate rewards stack properly
- **Spawner integration:** Stacked spawners work with Zakum
- **Performance:** Optimized for stacked entities

---

## WorldGuard Bridge

**Target Plugin:** [WorldGuard](https://enginehub.org/worldguard/)  
**Bridge Module:** `zakum-bridge-worldguard`  
**Auto-Enable:** Yes

### Setup

1. Install WorldGuard
2. Install Zakum (region protection activates)
3. Zakum respects WorldGuard regions

### Features

- **Region respect:** Zakum features respect WG regions
- **Flag integration:** Custom Zakum flags available
- **Protection:** Crates and pets respect protection
- **PvP handling:** Combat features respect PvP flags

### Custom Flags

```
/rg flag <region> zakum-crates allow
/rg flag <region> zakum-pets deny
/rg flag <region> zakum-trading allow
```

---

## FastAsyncWorldEdit Bridge

**Target Plugin:** [FastAsyncWorldEdit](https://www.spigotmc.org/resources/fastasyncworldedit.13932/)  
**Bridge Module:** `zakum-bridge-fawe`  
**Auto-Enable:** Yes

### Setup

1. Install FastAsyncWorldEdit
2. Install Zakum (async coordination activates)
3. Features integrate automatically

### Features

- **Async safety:** Zakum operations coordinate with FAWE
- **Undo integration:** Zakum actions can be undone
- **Performance:** Bulk operations use FAWE engine
- **Region support:** Mass operations respect selections

---

## Votifier Bridge

**Target Plugin:** [Votifier](https://www.spigotmc.org/resources/votifier.15358/)  
**Bridge Module:** `zakum-bridge-votifier`  
**Auto-Enable:** Yes

### Setup

1. Install Votifier
2. Install Zakum (vote rewards activate)
3. Configure vote rewards

### Features

- **Vote tracking:** All votes tracked by Zakum
- **Automated rewards:** Give crates/keys on vote
- **Streak system:** Bonus rewards for vote streaks
- **Top voters:** Leaderboard for voters

### Configuration

```yaml
# In Zakum config.yml
votifier:
  rewards:
    enabled: true
    onVote:
      - "crate give %player% vote 1"
      - "bp give %player% 100"
    streak:
      7: "crate give %player% legendary 1"
      30: "crate give %player% mythic 1"
```

---

## Bridge Development

Want to create your own bridge? See [PLUGIN_DEVELOPMENT.md](PLUGIN_DEVELOPMENT.md) for details.

### Bridge API

```java
public class MyPluginBridge implements ZakumBridge {
    @Override
    public String getTargetPlugin() {
        return "MyPlugin";
    }
    
    @Override
    public void onEnable(ZakumApi api) {
        // Initialize integration
    }
    
    @Override
    public void onDisable() {
        // Cleanup
    }
}
```

---

## Troubleshooting

### Bridge Not Loading

**Problem:** Bridge doesn't activate  
**Solution:** 
1. Verify target plugin is installed
2. Check plugin load order in server log
3. Ensure both plugins are latest versions

### Conflicts

**Problem:** Features conflict with target plugin  
**Solution:**
1. Check configuration for disable options
2. Adjust priority in Zakum config
3. Report incompatibility on GitHub

### Performance

**Problem:** Bridge causes lag  
**Solution:**
1. Check if issue exists without bridge
2. Disable specific bridge features in config
3. Report performance issue with profiler data

---

## Support

- **Discord:** [Zakum Discord](https://discord.gg/example)
- **GitHub:** [Zakum Issues](https://github.com/example/zakum)
- **Wiki:** [Zakum Wiki](https://wiki.example.com)

---

**Last Updated:** February 18, 2026  
**Module Version:** 0.1.0-SNAPSHOT  
**Related:** [COMMANDS.md](COMMANDS.md) | [CONFIG.md](CONFIG.md)

