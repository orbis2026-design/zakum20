package net.orbis.zakum.crates.model;

import net.orbis.zakum.api.util.WeightedTable;
import org.bukkit.inventory.ItemStack;

/**
 * Crate definition with animation configuration.
 */
public record CrateDef(
  String id,
  String name,
  boolean publicOpen,
  int publicRadius,
  ItemStack keyItem,
  WeightedTable<RewardDef> rewards,
  String animationType
) {
  /**
   * Constructor with default animation type.
   */
  public CrateDef(String id, String name, boolean publicOpen, int publicRadius, 
                  ItemStack keyItem, WeightedTable<RewardDef> rewards) {
    this(id, name, publicOpen, publicRadius, keyItem, rewards, "roulette");
  }
  
  /**
   * Get animation type, defaulting to "roulette" if not specified.
   */
  public String animationType() {
    return animationType == null || animationType.isBlank() ? "roulette" : animationType;
  }
}
