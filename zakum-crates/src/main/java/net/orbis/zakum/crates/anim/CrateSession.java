package net.orbis.zakum.crates.anim;

import net.orbis.zakum.crates.gui.CrateGuiHolder;
import net.orbis.zakum.crates.model.CrateDef;
import net.orbis.zakum.crates.model.RewardDef;
import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

final class CrateSession {

  final UUID opener;
  final CrateDef crate;

  final Location origin;
  final RewardDef finalReward;

  final CrateGuiHolder holder;
  final Inventory inv;

  final int steps;
  final int ticksPerStep;

  int tickCountdown;
  int stepIdx;

  boolean inventoryClosed = false;

  // row items (size 9)
  final ItemStack[] belt = new ItemStack[9];

  CrateSession(UUID opener, CrateDef crate, Location origin, RewardDef finalReward,
              CrateGuiHolder holder, Inventory inv, int steps, int ticksPerStep) {
    this.opener = opener;
    this.crate = crate;
    this.origin = origin;
    this.finalReward = finalReward;
    this.holder = holder;
    this.inv = inv;
    this.steps = steps;
    this.ticksPerStep = ticksPerStep;
    this.tickCountdown = ticksPerStep;
    this.stepIdx = 0;
  }
}
