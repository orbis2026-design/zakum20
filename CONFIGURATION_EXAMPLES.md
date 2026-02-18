# Configuration Examples - zakum-crates

**Module:** zakum-crates  
**Version:** 0.1.0-SNAPSHOT  
**Last Updated:** February 18, 2026

---

## Basic Configuration

### Minimal Setup

```yaml
# config.yml (minimal)
crates:
  basic_crate:
    name: "&6Basic Crate"
    animation: "roulette"
    public-open: false
    rewards:
      - weight: 50
        items:
          - type: DIAMOND
            amount: 5
        messages:
          - "&aYou received 5 diamonds!"
      
      - weight: 30
        items:
          - type: GOLD_INGOT
            amount: 10
        commands:
          - "eco give %player% 100"
      
      - weight: 20
        items:
          - type: IRON_INGOT
            amount: 20
        effects:
          - "SPEED:600:1"
```

---

## Animation Configuration

### All Animation Types

```yaml
crates:
  # Roulette - Classic belt scroll
  roulette_crate:
    name: "&eRoulette Crate"
    animation: "roulette"
    rewards: [...]
  
  # Explosion - Dramatic fireworks
  explosion_crate:
    name: "&cExplosion Crate"
    animation: "explosion"
    rewards: [...]
  
  # Spiral - Elegant helix
  spiral_crate:
    name: "&bSpiral Crate"
    animation: "spiral"
    rewards: [...]
  
  # Cascade - Waterfall effect
  cascade_crate:
    name: "&9Cascade Crate"
    animation: "cascade"
    rewards: [...]
  
  # Wheel - Spinning segments
  wheel_crate:
    name: "&6Wheel Crate"
    animation: "wheel"
    rewards: [...]
  
  # Instant - No animation
  instant_crate:
    name: "&7Quick Crate"
    animation: "instant"
    rewards: [...]
```

---

## Reward Types

### Item Rewards

```yaml
rewards:
  - weight: 40
    id: "diamond_reward"
    name: "&bDiamond Bundle"
    items:
      - type: DIAMOND
        amount: 10
      - type: DIAMOND_BLOCK
        amount: 1
    messages:
      - "&aYou received diamonds!"
```

### Command Rewards

```yaml
rewards:
  - weight: 30
    id: "rank_upgrade"
    name: "&6VIP Rank"
    commands:
      - "[console]lp user %player% parent set vip"
      - "[console]broadcast %player% got VIP!"
    messages:
      - "&6Congratulations on VIP rank!"
```

### Money Rewards

```yaml
rewards:
  - weight: 50
    id: "money_reward"
    name: "&e$1000"
    commands:
      - "eco give %player% 1000"
    messages:
      - "&aYou received $1000!"
```

### Effect Rewards

```yaml
rewards:
  - weight: 35
    id: "potion_bundle"
    name: "&dPotion Effects"
    effects:
      - "SPEED:600:1"          # Speed II for 30s
      - "STRENGTH:1200:0"      # Strength I for 60s
      - "REGENERATION:400:1"   # Regen II for 20s
    messages:
      - "&dYou feel empowered!"
```

### Permission Rewards

```yaml
rewards:
  - weight: 10
    id: "temp_fly"
    name: "&bTemporary Fly"
    commands:
      - "lp user %player% permission set essentials.fly 1h"
    messages:
      - "&bYou can fly for 1 hour!"
```

---

## Complete Example

### Premium Crate Configuration

```yaml
crates:
  premium_crate:
    id: "premium"
    name: "&6&lPREMIUM CRATE"
    animation: "explosion"
    public-open: true
    public-radius: 10
    
    key:
      type: TRIPWIRE_HOOK
      name: "&6Premium Key"
      lore:
        - "&7Use on a Premium Crate"
        - "&7to receive a reward!"
    
    rewards:
      # Common (50% chance)
      - weight: 50
        id: "common_money"
        name: "&7$500"
        commands:
          - "eco give %player% 500"
        messages:
          - "&7You received &e$500&7!"
      
      - weight: 50
        id: "common_items"
        name: "&7Iron Bundle"
        items:
          - type: IRON_INGOT
            amount: 32
          - type: IRON_BLOCK
            amount: 8
        messages:
          - "&7You received iron!"
      
      # Uncommon (30% chance)
      - weight: 20
        id: "uncommon_diamonds"
        name: "&aDiamond Bundle"
        items:
          - type: DIAMOND
            amount: 16
        commands:
          - "eco give %player% 1000"
        messages:
          - "&aYou received diamonds and $1000!"
      
      - weight: 10
        id: "uncommon_enchants"
        name: "&aEnchanted Tools"
        items:
          - type: DIAMOND_PICKAXE
            enchantments:
              - "EFFICIENCY:5"
              - "FORTUNE:3"
              - "UNBREAKING:3"
        messages:
          - "&aYou received an enchanted pickaxe!"
      
      # Rare (15% chance)
      - weight: 10
        id: "rare_armor"
        name: "&9Full Diamond Armor"
        items:
          - type: DIAMOND_HELMET
            enchantments:
              - "PROTECTION:4"
              - "UNBREAKING:3"
          - type: DIAMOND_CHESTPLATE
            enchantments:
              - "PROTECTION:4"
              - "UNBREAKING:3"
          - type: DIAMOND_LEGGINGS
            enchantments:
              - "PROTECTION:4"
              - "UNBREAKING:3"
          - type: DIAMOND_BOOTS
            enchantments:
              - "PROTECTION:4"
              - "UNBREAKING:3"
        effects:
          - "RESISTANCE:1200:1"
        messages:
          - "&9You received full diamond armor!"
      
      - weight: 5
        id: "rare_rank"
        name: "&9VIP Rank (1 Month)"
        commands:
          - "lp user %player% parent add vip 30d"
          - "broadcast &9%player% &7won &9VIP rank &7from a crate!"
        messages:
          - "&9Congratulations on VIP rank!"
          - "&7Valid for 30 days"
      
      # Epic (4% chance)
      - weight: 3
        id: "epic_money"
        name: "&d$10,000"
        commands:
          - "eco give %player% 10000"
          - "broadcast &d%player% &7won &d$10,000 &7from a crate!"
        effects:
          - "GLOWING:200:0"
        messages:
          - "&d&lYou won $10,000!"
      
      - weight: 1
        id: "epic_spawner"
        name: "&dMob Spawner"
        items:
          - type: SPAWNER
            displayName: "&dIron Golem Spawner"
            lore:
              - "&7Place to spawn Iron Golems"
        messages:
          - "&d&lYou received a spawner!"
      
      # Legendary (1% chance)
      - weight: 1
        id: "legendary_reward"
        name: "&6&l&nLEGENDARY PRIZE"
        commands:
          - "eco give %player% 50000"
          - "lp user %player% parent set mvp 90d"
          - "broadcast &6&l[!!!] %player% WON THE LEGENDARY PRIZE! [!!!]"
        items:
          - type: NETHERITE_SWORD
            displayName: "&6&lLegendary Blade"
            enchantments:
              - "SHARPNESS:5"
              - "LOOTING:3"
              - "UNBREAKING:3"
              - "MENDING:1"
        effects:
          - "GLOWING:600:0"
          - "REGENERATION:600:2"
        messages:
          - "&6&l&nCONGRATULATIONS!"
          - "&6You won the LEGENDARY prize!"
          - "&e- $50,000"
          - "&e- MVP Rank (90 days)"
          - "&e- Legendary Blade"
```

---

## Probability Breakdown

For the Premium Crate example above:

| Tier | Rewards | Total Weight | Probability |
|------|---------|--------------|-------------|
| Common | 2 | 100 | 50% |
| Uncommon | 2 | 30 | 15% |
| Rare | 2 | 15 | 7.5% |
| Epic | 2 | 4 | 2% |
| Legendary | 1 | 1 | 0.5% |

---

## Command Placeholders

Available placeholders in reward commands:

- `%player%` - Player name
- `%uuid%` - Player UUID
- `%world%` - World name
- `%x%` - X coordinate
- `%y%` - Y coordinate
- `%z%` - Z coordinate

---

## Effect Format

Format: `EFFECT_TYPE:duration:amplifier`

Examples:
- `SPEED:600:1` = Speed II for 30 seconds (600 ticks)
- `STRENGTH:1200:0` = Strength I for 60 seconds
- `REGENERATION:200:2` = Regeneration III for 10 seconds

Duration: 1-72000 ticks (0.05s - 1 hour)  
Amplifier: 0-255 (Level = amplifier + 1)

---

## Best Practices

### Reward Weights

- Use round numbers (1, 5, 10, 50, 100)
- Higher weight = more common
- Total weight should sum to meaningful number
- Rare rewards should be <5% probability

### Commands

- Use `[console]` prefix for console commands
- Use `[player]` prefix for player commands
- Default is console if no prefix
- Always include player placeholders

### Messages

- Use color codes (&a, &b, etc.)
- Include clear reward description
- Mention expiration for temporary rewards
- Keep messages concise

### Animation Selection

- **roulette**: Best for classic feel
- **explosion**: Best for dramatic reveals
- **spiral**: Best for elegant effect
- **cascade**: Best for unique visual
- **wheel**: Best for game show feel
- **instant**: Best for speed/efficiency

---

**Last Updated:** February 18, 2026  
**Module Version:** 0.1.0-SNAPSHOT

