package net.orbis.zakum.crates.model;

import org.bukkit.inventory.ItemStack;

import java.util.List;

public record RewardDef(
  double weight,
  double economyAmount,
  List<String> messages,
  List<String> commands,
  List<ItemStack> items
) {}
