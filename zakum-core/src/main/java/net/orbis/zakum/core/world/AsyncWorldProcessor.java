package net.orbis.zakum.core.world;

import net.orbis.zakum.api.ZakumApi;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;

import java.util.Map;

/**
 * Folia-safe regional world mutation helper.
 */
public final class AsyncWorldProcessor {

  public void setBlocks(Map<Location, BlockData> blocks) {
    if (blocks == null || blocks.isEmpty()) return;
    blocks.forEach((loc, data) -> {
      if (loc == null || data == null) return;
      ZakumApi.get().getScheduler().runAtLocation(loc, () -> {
        if (loc.getWorld() == null) return;
        loc.getBlock().setBlockData(data, false);
      });
    });
  }
}
