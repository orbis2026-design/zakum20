package net.orbis.zakum.api.packet;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface AnimationService {

  void spawnDisplay(Player viewer, Location loc, ItemStack item);
}
