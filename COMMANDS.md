# Zakum Commands & Permissions Reference

**Version:** 0.1.0-SNAPSHOT  
**Last Updated:** February 18, 2026  
**Module Coverage:** All 27 modules

---

## Overview

This document provides a comprehensive reference for all commands and permissions across the Zakum plugin ecosystem.

---

## Table of Contents

1. [zakum-core Commands](#zakum-core-commands)
2. [zakum-battlepass Commands](#zakum-battlepass-commands)
3. [zakum-crates Commands](#zakum-crates-commands)
4. [zakum-pets Commands](#zakum-pets-commands)
5. [zakum-teams Commands](#zakum-teams-commands)
6. [orbis-essentials Commands](#orbis-essentials-commands)
7. [Permission Hierarchy](#permission-hierarchy)

---

## zakum-core Commands

### `/zakum`

Main administrative command for Zakum core functions.

**Usage:** `/zakum <subcommand> [args]`

**Permission:** `zakum.admin` (default: op)

#### Subcommands

| Command | Description | Permission | Default |
|---------|-------------|------------|---------|
| `/zakum reload` | Reload configuration | `zakum.admin.reload` | op |
| `/zakum info` | Display system information | `zakum.admin.info` | op |
| `/zakum status` | Show system status | `zakum.admin.status` | op |
| `/zakum metrics` | Display performance metrics | `zakum.admin.metrics` | op |
| `/zakum debug <on\|off>` | Toggle debug mode | `zakum.admin.debug` | op |
| `/zakum version` | Show plugin version | `zakum.admin.version` | op |

**Examples:**
```
/zakum reload
/zakum info
/zakum debug on
```

---

## zakum-battlepass Commands

### `/battlepass` (alias: `/bp`)

Manage and view battle pass progress.

**Usage:** `/battlepass <subcommand> [args]`

#### Player Commands

| Command | Description | Permission | Default |
|---------|-------------|------------|---------|
| `/battlepass view` | Open battle pass GUI | `zakum.battlepass.view` | true |
| `/battlepass progress` | View current progress | `zakum.battlepass.progress` | true |
| `/battlepass claim [tier]` | Claim reward(s) | `zakum.battlepass.claim` | true |
| `/battlepass info` | View pass information | `zakum.battlepass.info` | true |

#### Admin Commands

| Command | Description | Permission | Default |
|---------|-------------|------------|---------|
| `/battlepass admin reload` | Reload configuration | `zakum.battlepass.admin.reload` | op |
| `/battlepass admin give <player> <xp>` | Give XP to player | `zakum.battlepass.admin.give` | op |
| `/battlepass admin set <player> <tier>` | Set player tier | `zakum.battlepass.admin.set` | op |
| `/battlepass admin reset <player>` | Reset player progress | `zakum.battlepass.admin.reset` | op |
| `/battlepass admin create <name>` | Create new season | `zakum.battlepass.admin.create` | op |

**Examples:**
```
/bp view
/bp claim
/bp admin give Notch 1000
```

---

## zakum-crates Commands

### `/crates` (alias: `/crate`)

Manage loot crates and keys.

**Usage:** `/crates <subcommand> [args]`

#### Player Commands

| Command | Description | Permission | Default |
|---------|-------------|------------|---------|
| `/crates list` | List available crates | `zakum.crates.list` | true |
| `/crates preview <crate>` | Preview crate contents | `zakum.crates.preview` | true |
| `/crates keys` | View your keys | `zakum.crates.keys` | true |

#### Admin Commands

| Command | Description | Permission | Default |
|---------|-------------|------------|---------|
| `/crates give <player> <crate> [amount]` | Give crate to player | `zakum.crates.admin.give` | op |
| `/crates givekey <player> <crate> [amount]` | Give key to player | `zakum.crates.admin.givekey` | op |
| `/crates reload` | Reload configuration | `zakum.crates.admin.reload` | op |
| `/crates create <name>` | Create new crate type | `zakum.crates.admin.create` | op |
| `/crates delete <name>` | Delete crate type | `zakum.crates.admin.delete` | op |
| `/crates setlocation <crate>` | Set crate placement | `zakum.crates.admin.setlocation` | op |

**Examples:**
```
/crates list
/crates preview legendary
/crates give Notch legendary 5
```

---

## zakum-pets Commands

### `/pets` (alias: `/pet`)

Manage your pets collection.

**Usage:** `/pets <subcommand> [args]`

#### Player Commands

| Command | Description | Permission | Default |
|---------|-------------|------------|---------|
| `/pets menu` | Open pets menu | `zakum.pets.menu` | true |
| `/pets summon <pet>` | Summon a pet | `zakum.pets.summon` | true |
| `/pets unsummon` | Unsummon current pet | `zakum.pets.unsummon` | true |
| `/pets list` | List owned pets | `zakum.pets.list` | true |
| `/pets info <pet>` | View pet information | `zakum.pets.info` | true |
| `/pets rename <name>` | Rename active pet | `zakum.pets.rename` | true |

#### Admin Commands

| Command | Description | Permission | Default |
|---------|-------------|------------|---------|
| `/pets give <player> <pet>` | Give pet to player | `zakum.pets.admin.give` | op |
| `/pets remove <player> <pet>` | Remove pet from player | `zakum.pets.admin.remove` | op |
| `/pets reload` | Reload configuration | `zakum.pets.admin.reload` | op |

**Examples:**
```
/pets menu
/pets summon dragon
/pets rename "Fluffy"
```

---

## zakum-teams Commands

### `/team`

Manage teams and invitations.

**Usage:** `/team <subcommand> [args]`

#### Player Commands

| Command | Description | Permission | Default |
|---------|-------------|------------|---------|
| `/team create <name>` | Create a new team | `zakum.teams.create` | true |
| `/team disband` | Disband your team | `zakum.teams.delete` | true |
| `/team invite <player>` | Invite a player | `zakum.teams.invite` | true |
| `/team join <team>` | Join a team | `zakum.teams.join` | true |
| `/team leave` | Leave your team | `zakum.teams.leave` | true |
| `/team info` | View team information | `zakum.teams.info` | true |
| `/team list` | List all teams | `zakum.teams.list` | true |
| `/team kick <player>` | Kick a member | `zakum.teams.kick` | true |

#### Admin Commands

| Command | Description | Permission | Default |
|---------|-------------|------------|---------|
| `/team admin delete <team>` | Delete any team | `zakum.teams.admin` | op |
| `/team admin teleport <team>` | Teleport to team | `zakum.teams.admin` | op |

**Examples:**
```
/team create Warriors
/team invite Notch
/team info
```

---

## orbis-essentials Commands

### `/home`

Manage personal homes.

**Usage:** `/home [name] | /sethome [name] | /delhome [name]`

| Command | Description | Permission | Default |
|---------|-------------|------------|---------|
| `/home` | Teleport to default home | `orbis.essentials.home` | true |
| `/home <name>` | Teleport to named home | `orbis.essentials.home` | true |
| `/sethome [name]` | Set a home | `orbis.essentials.sethome` | true |
| `/delhome <name>` | Delete a home | `orbis.essentials.delhome` | true |
| `/homes` | List your homes | `orbis.essentials.homes` | true |

---

### `/warp`

Manage server warps.

**Usage:** `/warp <name> | /setwarp <name> | /delwarp <name>`

| Command | Description | Permission | Default |
|---------|-------------|------------|---------|
| `/warp <name>` | Teleport to warp | `orbis.essentials.warp` | true |
| `/warps` | List all warps | `orbis.essentials.warps` | true |
| `/setwarp <name>` | Create a warp | `orbis.essentials.setwarp` | op |
| `/delwarp <name>` | Delete a warp | `orbis.essentials.delwarp` | op |

---

### `/tpa`, `/tpahere`

Teleport requests.

**Usage:** `/tpa <player> | /tpahere <player> | /tpaccept | /tpdeny`

| Command | Description | Permission | Default |
|---------|-------------|------------|---------|
| `/tpa <player>` | Request to teleport to player | `orbis.essentials.tpa` | true |
| `/tpahere <player>` | Request player teleport to you | `orbis.essentials.tpahere` | true |
| `/tpaccept` | Accept teleport request | `orbis.essentials.tpaccept` | true |
| `/tpdeny` | Deny teleport request | `orbis.essentials.tpdeny` | true |

---

## orbis-gui Commands

### `/gui`

Open GUI menus.

**Usage:** `/gui [menu]`

| Command | Description | Permission | Default |
|---------|-------------|------------|---------|
| `/gui` | Open main menu | `orbis.gui.open` | true |
| `/gui <menu>` | Open specific menu | `orbis.gui.open.<menu>` | true |

---

## orbis-hud Commands

### `/hud`

Manage HUD display.

**Usage:** `/hud <subcommand>`

| Command | Description | Permission | Default |
|---------|-------------|------------|---------|
| `/hud toggle` | Toggle HUD on/off | `orbis.hud.toggle` | true |
| `/hud reload` | Reload HUD config | `orbis.hud.admin.reload` | op |

---

## Permission Hierarchy

### Wildcard Permissions

Grant all permissions for a module:

- `zakum.*` - All zakum-core permissions
- `zakum.battlepass.*` - All battle pass permissions
- `zakum.crates.*` - All crates permissions
- `zakum.pets.*` - All pets permissions
- `zakum.teams.*` - All teams permissions
- `orbis.essentials.*` - All essentials permissions
- `orbis.gui.*` - All GUI permissions
- `orbis.hud.*` - All HUD permissions

### Admin Permissions

Grant all admin commands:

- `zakum.admin` - All zakum-core admin commands
- `zakum.battlepass.admin` - All battle pass admin commands
- `zakum.crates.admin` - All crates admin commands
- `zakum.pets.admin` - All pets admin commands
- `zakum.teams.admin` - All teams admin commands

### Default Permissions

These permissions are granted by default to all players:

- `zakum.battlepass.view`
- `zakum.battlepass.progress`
- `zakum.battlepass.claim`
- `zakum.crates.list`
- `zakum.crates.preview`
- `zakum.pets.menu`
- `zakum.pets.summon`
- `zakum.teams.create`
- `orbis.essentials.home`
- `orbis.essentials.warp`
- `orbis.essentials.tpa`
- `orbis.gui.open`
- `orbis.hud.toggle`

---

## Permission Groups Example

### LuckPerms Configuration

```yaml
# Default player group
group:
  default:
    permissions:
      - zakum.battlepass.view
      - zakum.crates.list
      - zakum.pets.menu
      - orbis.essentials.home
      - orbis.essentials.warp
      - orbis.essentials.tpa

# VIP group
group:
  vip:
    inheritance:
      - default
    permissions:
      - zakum.battlepass.boost.1.5x
      - zakum.crates.preview
      - orbis.essentials.sethome.3  # 3 homes

# Admin group
group:
  admin:
    permissions:
      - zakum.*
      - orbis.*
```

---

## Command Aliases

| Command | Aliases |
|---------|---------|
| `/battlepass` | `/bp`, `/pass` |
| `/crates` | `/crate`, `/cr` |
| `/pets` | `/pet` |
| `/team` | `/t` |
| `/home` | `/h` |
| `/sethome` | `/sh` |

---

## Tab Completion

All commands support tab completion for:
- Subcommands
- Player names
- Crate/pet/team names
- Configuration values

---

**Last Updated:** February 18, 2026  
**Module Version:** 0.1.0-SNAPSHOT  
**Related:** [CONFIG.md](CONFIG.md) - Configuration Reference

