package net.orbis.zakum.crates.listener;

import net.orbis.zakum.crates.db.CrateBlockStore;
import net.orbis.zakum.crates.util.ItemBuilder;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.Iterator;
import java.util.List;

/**
 * Keeps crate block mappings consistent and protected from accidental world edits.
 */
public final class CrateBlockListener implements Listener {

  private final CrateBlockStore blocks;

  public CrateBlockListener(CrateBlockStore blocks) {
    this.blocks = blocks;
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
  public void onBreakProtect(BlockBreakEvent event) {
    Block block = event.getBlock();
    String crateId = blocks.get(block.getWorld(), block.getX(), block.getY(), block.getZ());
    if (crateId == null) return;

    if (!event.getPlayer().hasPermission("orbis.crates.admin")) {
      event.setCancelled(true);
      event.getPlayer().sendMessage(ItemBuilder.color("&cThis crate block is protected."));
    }
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
  public void onBreakCommit(BlockBreakEvent event) {
    String removed = blocks.unset(event.getBlock());
    if (removed == null) return;
    event.getPlayer().sendMessage(ItemBuilder.color("&aRemoved crate block mapping for &f" + removed + "&a."));
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
  public void onBlockExplode(BlockExplodeEvent event) {
    keepCrateBlocksIntact(event.blockList());
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
  public void onEntityExplode(EntityExplodeEvent event) {
    keepCrateBlocksIntact(event.blockList());
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
  public void onPistonExtend(BlockPistonExtendEvent event) {
    if (containsCrateBlock(event.getBlocks())) {
      event.setCancelled(true);
    }
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
  public void onPistonRetract(BlockPistonRetractEvent event) {
    if (containsCrateBlock(event.getBlocks())) {
      event.setCancelled(true);
    }
  }

  private void keepCrateBlocksIntact(List<Block> blocksToExplode) {
    Iterator<Block> it = blocksToExplode.iterator();
    while (it.hasNext()) {
      Block block = it.next();
      String crateId = blocks.get(block.getWorld(), block.getX(), block.getY(), block.getZ());
      if (crateId != null) {
        it.remove();
      }
    }
  }

  private boolean containsCrateBlock(List<Block> moved) {
    for (Block block : moved) {
      String crateId = blocks.get(block.getWorld(), block.getX(), block.getY(), block.getZ());
      if (crateId != null) return true;
    }
    return false;
  }
}
