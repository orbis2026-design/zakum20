# zakum-crates - Advanced Crate System

**Version:** 0.1.0-SNAPSHOT  
**Platform:** Paper 1.21.11 | Java 21  
**Status:** Production Ready (95% Complete)

---

## 📖 Overview

zakum-crates is a feature-rich crate and key system for Minecraft servers with multiple animation types, comprehensive reward system, and flexible configuration.

### Features
- **6 Animation Types:** Roulette, Explosion, Spiral, Cascade, Wheel, Instant
- **7 Reward Executors:** Items, Commands, Effects, Money, Permissions, Composite
- **Flexible Configuration:** Per-crate animation types, public broadcasts, custom keys
- **Reward System:** Probability engine, history tracking, notifications
- **GUI System:** Safe interactions, drag prevention, info display
- **Database Integration:** Persistent block tracking, reward history

---

## 🚀 Quick Start

### Installation

1. **Requirements:**
   - Paper 1.21.11+
   - Java 21
   - Zakum-Core plugin

2. **Optional Dependencies:**
   - Vault (for economy rewards)
   - LuckPerms (for permission rewards)

3. **Install:**
   ```bash
   # Place in server plugins folder
   plugins/
   ├── Zakum-0.1.0-SNAPSHOT.jar  (required)
   └── ZakumCrates-0.1.0-SNAPSHOT.jar
   ```

4. **First Run:**
   ```
   Server will generate:
   plugins/OrbisCrates/config.yml
   ```

---

## ⚙️ Configuration

### Basic Crate Setup

```yaml
crates:
  basic:
    # Display name (supports color codes with &)
    name: "&aBasic Crate"
    
    # Animation type (roulette, explosion, spiral, cascade, wheel, instant)
    animationType: "roulette"
    
    # Broadcast to nearby players when opened
    publicOpen: false
    publicRadius: 0
    
    # Key item definition
    key:
      material: TRIPWIRE_HOOK
      name: "&aBasic Key"
      lore:
        - "&7Right-click a crate to use"
    
    # Reward list
    rewards:
      - id: "coins"
        name: "100 Coins"
        weight: 50
        commands:
          - "eco give %player% 100"
      
      - id: "diamonds"
        name: "16 Diamonds"
        weight: 10
        items:
          - material: DIAMOND
            amount: 16
```

### Advanced Crate Setup

```yaml
crates:
  premium:
    name: "&6&lPREMIUM CRATE"
    animationType: "wheel"
    publicOpen: true
    publicRadius: 20
    
    key:
      material: TRIPWIRE_HOOK
      name: "&6Premium Key"
      lore:
        - "&7Something special awaits..."
      enchantments:
        - "UNBREAKING:1"
      flags:
        - "HIDE_ENCHANTS"
    
    rewards:
      # Common rewards (high weight)
      - id: "common_coins"
        name: "$500"
        weight: 40
        commands:
          - "eco give %player% 500"
        messages:
          - "&aYou received $500!"
      
      # Uncommon rewards (medium weight)
      - id: "uncommon_items"
        name: "Diamond Gear"
        weight: 20
        items:
          - material: DIAMOND_SWORD
            enchantments:
              - "SHARPNESS:3"
          - material: DIAMOND_HELMET
            enchantments:
              - "PROTECTION:2"
      
      # Rare rewards (low weight)
      - id: "rare_combo"
        name: "Epic Package"
        weight: 5
        items:
          - material: NETHERITE_INGOT
            amount: 16
        commands:
          - "eco give %player% 5000"
        effects:
          - "SPEED:2:600"
          - "JUMP_BOOST:1:600"
        messages:
          - "&6&lEPIC! &eYou won the jackpot!"
      
      # Ultra rare (very low weight - broadcasts)
      - id: "jackpot"
        name: "&c&lJACKPOT"
        weight: 1
        items:
          - material: NETHER_STAR
            name: "&c&lJackpot Star"
        commands:
          - "eco give %player% 50000"
          - "broadcast &c&l%player% WON THE JACKPOT!"
```

---

## 🎨 Animation Types

### Roulette (Default)
- **Description:** Belt-based spinning animation
- **Duration:** ~60 ticks (3 seconds)
- **Effect:** Physics-based deceleration
- **Best For:** Classic crate experience

```yaml
animationType: "roulette"
```

### Explosion
- **Description:** Firework burst effect
- **Duration:** ~40 ticks (2 seconds)
- **Effect:** Multiple particle explosions
- **Best For:** Dramatic reveals

```yaml
animationType: "explosion"
```

### Spiral
- **Description:** Helix particle animation
- **Duration:** ~50 ticks (2.5 seconds)
- **Effect:** Spiraling particles around center
- **Best For:** Mystical/magical theme

```yaml
animationType: "spiral"
```

### Cascade
- **Description:** Waterfall effect
- **Duration:** ~45 ticks (2.25 seconds)
- **Effect:** Cascading particles from top
- **Best For:** Elegant presentation

```yaml
animationType: "cascade"
```

### Wheel
- **Description:** Circular wheel with 8 segments
- **Duration:** ~80 ticks (4 seconds)
- **Effect:** Rotation with winner highlighting
- **Best For:** Game show style

```yaml
animationType: "wheel"
```

### Instant
- **Description:** Immediate reward reveal
- **Duration:** ~5 ticks (0.25 seconds)
- **Effect:** Minimal delay
- **Best For:** Quick rewards, admin testing

```yaml
animationType: "instant"
```

---

## 🎁 Reward Types

### Item Rewards
Give items directly to player inventory.

```yaml
rewards:
  - id: "item_reward"
    name: "Diamond Stack"
    weight: 10
    items:
      - material: DIAMOND
        amount: 64
        name: "&bSpecial Diamond"
        lore:
          - "&7From a crate!"
        enchantments:
          - "UNBREAKING:1"
        flags:
          - "HIDE_ENCHANTS"
```

### Command Rewards
Execute commands as console.

```yaml
rewards:
  - id: "command_reward"
    name: "Command Package"
    weight: 20
    commands:
      - "give %player% diamond 32"
      - "tell %player% &aYou got diamonds!"
      - "eco give %player% 1000"
```

**Placeholders:**
- `%player%` - Player name
- `%uuid%` - Player UUID
- `%world%` - Player's world name

### Effect Rewards
Apply potion effects to player.

```yaml
rewards:
  - id: "effect_reward"
    name: "Speed Boost"
    weight: 30
    effects:
      - "SPEED:2:600"        # Speed II for 30 seconds
      - "JUMP_BOOST:1:600"   # Jump Boost I for 30 seconds
      - "NIGHT_VISION:0:1200" # Night Vision for 60 seconds
```

**Format:** `EFFECT_TYPE:AMPLIFIER:DURATION_TICKS`
- Duration: 20 ticks = 1 second
- Amplifier: 0 = Level I, 1 = Level II, etc.

### Money Rewards (Vault Required)
Deposit money to player account.

```yaml
rewards:
  - id: "money_reward"
    name: "$1000"
    weight: 25
    commands:
      - "eco give %player% 1000"
```

### Permission Rewards (LuckPerms Required)
Grant permissions to player.

```yaml
rewards:
  - id: "perm_reward"
    name: "VIP Rank"
    weight: 5
    commands:
      - "lp user %player% permission set group.vip"
      - "tell %player% &aYou are now VIP!"
```

### Composite Rewards
Combine multiple reward types.

```yaml
rewards:
  - id: "mega_reward"
    name: "&6&lMEGA PACKAGE"
    weight: 2
    items:
      - material: NETHERITE_INGOT
        amount: 32
    commands:
      - "eco give %player% 10000"
      - "lp user %player% permission set special.bonus true 7d"
    effects:
      - "SPEED:2:6000"
      - "STRENGTH:1:6000"
    messages:
      - "&6&lCONGRATULATIONS!"
      - "&eYou won the mega package!"
```

---

## 🎮 Commands

### Player Commands

**Open Crate:**
- Right-click placed crate block with key

**Preview Animation (Admin):**
```
/cratepreview <animation_type>
```
- Test animations without consuming keys
- Permission: `zakum.crates.preview`
- Animation types: roulette, explosion, spiral, cascade, wheel, instant

### Admin Commands

**Give Crate Block:**
```
/crate set <crate_id>
```
- Convert chest to crate block
- Tracks location in database

**Give Key:**
```
/cratekey give <player> <crate_id> <amount>
```
- Give physical key items
- Keys are specific to crate type

**Reload Configuration:**
```
/reload confirm
```
- Reloads plugin configuration
- Existing animations will complete

---

## 🔐 Permissions

### Player Permissions
- `zakum.crates.use` - Use crates (default: true)

### Admin Permissions
- `zakum.crates.admin` - Access admin commands
- `zakum.crates.preview` - Preview animations
- `zakum.crates.give` - Give crates/keys

---

## 📊 Reward Probabilities

Rewards use weighted random selection. Higher weight = higher chance.

### Example Probability Calculation

```yaml
rewards:
  - weight: 50  # 50/100 = 50% chance
  - weight: 30  # 30/100 = 30% chance
  - weight: 15  # 15/100 = 15% chance
  - weight: 5   # 5/100 = 5% chance
```

**Total Weight:** 100  
**Probabilities:**
- Common: 50%
- Uncommon: 30%
- Rare: 15%
- Ultra Rare: 5%

### Rare Reward Broadcasts

Rewards with <5% probability automatically broadcast to nearby players when won.

---

## 🗄️ Database

zakum-crates uses the Zakum database for persistent storage.

### Tables

**zakum_crate_blocks:**
- Stores placed crate block locations
- Format: world, x, y, z, crate_id, placed_at

**zakum_crate_history:**
- Tracks reward grants
- Format: player_uuid, crate_id, reward_id, timestamp

---

## 🛠️ Troubleshooting

### Crate Won't Open
1. Check player has correct key
2. Verify crate is configured: `config.yml`
3. Check console for errors
4. Verify database connection

### Animation Not Playing
1. Verify animation type is valid
2. Check TPS (lag may affect animations)
3. Try different animation type
4. Check player can see GUI

### Rewards Not Given
1. Check console for executor errors
2. Verify Vault/LuckPerms for special rewards
3. Check inventory space for items
4. Review reward configuration syntax

### Performance Issues
1. Reduce concurrent crate opens
2. Use "instant" animation for quick rewards
3. Optimize reward command lists
4. Check server TPS

---

## 🔧 Advanced Configuration

### Custom Key Items

```yaml
key:
  material: NETHER_STAR
  name: "&c&lMYSTIC KEY"
  lore:
    - "&7A mysterious key..."
    - "&7Used to open mystical crates"
  enchantments:
    - "UNBREAKING:1"
  flags:
    - "HIDE_ENCHANTS"
    - "HIDE_ATTRIBUTES"
  glow: true  # Add enchantment glow
```

### Public Broadcast Settings

```yaml
publicOpen: true  # Enable broadcast
publicRadius: 20  # 20 blocks radius
```

When enabled:
- Players within radius see title
- Sound plays for nearby players
- Creates excitement for premium crates

---

## 📈 Best Practices

### Reward Design
1. **Balance probabilities** - Most rewards common, few rare
2. **Test thoroughly** - Use `/cratepreview` to test
3. **Clear naming** - Use descriptive reward IDs
4. **Varied rewards** - Mix items, commands, effects

### Performance
1. **Limit concurrent opens** - Use rate limiting if needed
2. **Optimize commands** - Avoid long command lists
3. **Choose appropriate animations** - Instant for common, wheel for rare
4. **Monitor TPS** - Keep server performant

### User Experience
1. **Clear key names** - Players should know what key is for
2. **Exciting animations** - Match animation to crate rarity
3. **Broadcast rare wins** - Create excitement
4. **Informative messages** - Tell players what they won

---

## 🔄 Version History

### v0.1.0-SNAPSHOT (Current)
- Initial release
- 6 animation types
- 7 reward executors
- Complete reward system
- GUI integration
- Database persistence
- History tracking

---

## 🐛 Known Issues

None at this time.

---

## 📞 Support

**Documentation:** See `INTEGRATION_TESTING_COMPLETE.md` for testing details  
**API Guide:** See `../PLUGIN_DEVELOPMENT.md` for API usage  
**Configuration:** See `CONFIGURATION_EXAMPLES.md` for more examples

---

## 📜 License

Part of the Zakum Suite project.

---

**zakum-crates - Production Ready Crate System ✅**
