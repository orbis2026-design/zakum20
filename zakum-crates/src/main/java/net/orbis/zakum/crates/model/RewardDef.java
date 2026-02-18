package net.orbis.zakum.crates.model;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Reward definition for crates.
 * 
 * Supports multiple reward types:
 * - Items
 * - Commands
 * - Economy money
 * - Potion effects
 * - Messages
 */
public record RewardDef(
  String id,
  String name,
  double weight,
  List<ItemStack> items,
  List<String> commands,
  List<String> effects,
  List<String> messages
) {
  public RewardDef {
    id = id == null ? "unknown" : id;
    name = name == null ? "Unknown Reward" : name;
    weight = Math.max(0.0, weight);
    items = items == null ? List.of() : List.copyOf(items);
    commands = commands == null ? List.of() : List.copyOf(commands);
    effects = effects == null ? List.of() : List.copyOf(effects);
    messages = messages == null ? List.of() : List.copyOf(messages);
  }
}
