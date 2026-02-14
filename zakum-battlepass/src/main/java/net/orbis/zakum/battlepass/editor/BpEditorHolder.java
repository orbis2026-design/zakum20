package net.orbis.zakum.battlepass.editor;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Inventory holder used to identify OrbisBattlePass editor menus.
 *
 * contextA/contextB are lightweight strings to carry a questId/tier/rewardId/track, etc.
 */
public final class BpEditorHolder implements InventoryHolder {

  private final UUID viewer;
  private final EditorMenuType type;
  private final int page;
  private final String contextA;
  private final String contextB;

  private Inventory inv;

  public BpEditorHolder(UUID viewer, EditorMenuType type, int page, String contextA, String contextB) {
    this.viewer = viewer;
    this.type = type;
    this.page = page;
    this.contextA = contextA;
    this.contextB = contextB;
  }

  public UUID viewer() { return viewer; }
  public EditorMenuType type() { return type; }
  public int page() { return page; }
  public String a() { return contextA; }
  public String b() { return contextB; }

  public void bind(Inventory inv) { this.inv = inv; }

  @Override
  public Inventory getInventory() {
    return inv;
  }
}
