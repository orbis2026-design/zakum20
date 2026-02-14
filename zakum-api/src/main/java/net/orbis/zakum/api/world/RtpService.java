package net.orbis.zakum.api.world;

import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

public interface RtpService {

  /**
   * Searches a safe random location and teleports the player on the correct region thread.
   */
  CompletableFuture<Boolean> searchAndTeleport(Player player, int minRange, int maxRange);
}
