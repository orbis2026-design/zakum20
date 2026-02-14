package net.orbis.zakum.core.actions.emitters;

import net.orbis.zakum.api.actions.ActionBus;
import net.orbis.zakum.api.actions.ActionEvent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public final class BlockPlaceEmitter implements Listener {

  private final ActionBus bus;

  public BlockPlaceEmitter(ActionBus bus) {
    this.bus = bus;
  }

  @EventHandler(ignoreCancelled = true)
  public void onPlace(BlockPlaceEvent e) {
    Player p = e.getPlayer();
    Material m = e.getBlockPlaced().getType();

    bus.publish(new ActionEvent(
      "block_place",
      p.getUniqueId(),
      1,
      "material",
      m.name()
    ));
  }
}
