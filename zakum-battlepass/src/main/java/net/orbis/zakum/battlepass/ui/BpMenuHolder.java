package net.orbis.zakum.battlepass.ui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Inventory holder used to identify OrbisBattlePass menus.
 */
public final class BpMenuHolder implements InventoryHolder {

  private final UUID viewer;
  private final MenuType type;
  private final int page;

  private Inventory inv;

  public BpMenuHolder(UUID viewer, MenuType type, int page) {
    this.viewer = viewer;
    this.type = type;
    this.page = page;
  }

  public UUID viewer() { return viewer; }
  public MenuType type() { return type; }
  public int page() { return page; }

  public void bind(Inventory inv) {
    this.inv = inv;
  }

  @Override
  public Inventory getInventory() {
    return inv;
  }
}
