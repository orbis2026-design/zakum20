package net.orbis.zakum.core.actions.emitters;

import net.orbis.zakum.api.actions.ActionBus;
import net.orbis.zakum.api.actions.ActionEvent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public final class BlockBreakEmitter implements Listener {

  private final ActionBus bus;

  public BlockBreakEmitter(ActionBus bus) {
    this.bus = bus;
  }

  @EventHandler(ignoreCancelled = true)
  public void onBreak(BlockBreakEvent e) {
    Player p = e.getPlayer();
    Material m = e.getBlock().getType();

    bus.publish(new ActionEvent(
      "block_break",
      p.getUniqueId(),
      1,
      "material",
      m.name()
    ));
  }
}
