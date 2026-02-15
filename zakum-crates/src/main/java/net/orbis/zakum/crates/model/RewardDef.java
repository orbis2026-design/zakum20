package net.orbis.zakum.crates.model;

import org.bukkit.inventory.ItemStack;

import java.util.List;

public record RewardDef(
  double weight,
  double economyAmount,
  List<String> messages,
  List<String> commands,
  List<String> script,
  List<ItemStack> items
) {
  public RewardDef {
    messages = messages == null ? List.of() : List.copyOf(messages);
    commands = commands == null ? List.of() : List.copyOf(commands);
    script = script == null ? List.of() : List.copyOf(script);
    items = items == null ? List.of() : List.copyOf(items);
  }
}
