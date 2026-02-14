package net.orbis.zakum.crates.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Marker holder to identify crate GUI inventories and bind them to a session owner.
 */
public final class CrateGuiHolder implements InventoryHolder {

  private final UUID owner;
  private Inventory inv;

  public CrateGuiHolder(UUID owner) {
    this.owner = owner;
  }

  public UUID owner() {
    return owner;
  }

  public void bind(Inventory inv) {
    this.inv = inv;
  }

  @Override
  public Inventory getInventory() {
    return inv;
  }
}
