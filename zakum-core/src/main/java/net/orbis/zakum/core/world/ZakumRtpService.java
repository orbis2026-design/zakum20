package net.orbis.zakum.core.world;

import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.api.concurrent.ZakumScheduler;
import net.orbis.zakum.api.world.RtpService;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public final class ZakumRtpService implements RtpService {

  private static final int MAX_ATTEMPTS = 14;

  private final ZakumScheduler scheduler;

  public ZakumRtpService() {
    this(ZakumApi.get().getScheduler());
  }

  public ZakumRtpService(ZakumScheduler scheduler) {
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
  }

  public CompletableFuture<Boolean> searchAndTeleport(Player player, int range) {
    int r = Math.max(1, range);
    return searchAndTeleport(player, r / 2, r);
  }

  @Override
  public CompletableFuture<Boolean> searchAndTeleport(Player player, int minRange, int maxRange) {
    if (player == null || !player.isOnline()) return CompletableFuture.completedFuture(false);

    World world = player.getWorld();
    if (world == null) return CompletableFuture.completedFuture(false);

    int min = Math.max(1, Math.min(minRange, maxRange));
    int max = Math.max(min, Math.max(minRange, maxRange));
    Location origin = player.getLocation();

    return findSafeRandomLocation(world, origin, min, max, MAX_ATTEMPTS).thenCompose(candidate -> {
      if (candidate == null) return CompletableFuture.completedFuture(false);

      CompletableFuture<Boolean> teleported = new CompletableFuture<>();
      scheduler.runAtEntity(player, () -> {
        if (!player.isOnline()) {
          teleported.complete(false);
          return;
        }
        teleported.complete(player.teleport(candidate));
      });
      return teleported;
    });
  }

  private CompletableFuture<Location> findSafeRandomLocation(World world, Location origin, int min, int max, int attempts) {
    if (attempts <= 0) return CompletableFuture.completedFuture(null);

    int x = randomCoordinate(origin.getBlockX(), min, max);
    int z = randomCoordinate(origin.getBlockZ(), min, max);
    int chunkX = x >> 4;
    int chunkZ = z >> 4;

    return world.getChunkAtAsync(chunkX, chunkZ, true).handle((chunk, err) -> {
      if (err != null || chunk == null) return null;
      Location safe = findSafeLocation(world, chunk, x, z);
      if (safe != null && !world.getWorldBorder().isInside(safe)) return null;
      return safe;
    }).thenCompose(safe -> {
      if (safe != null) return CompletableFuture.completedFuture(safe);
      return findSafeRandomLocation(world, origin, min, max, attempts - 1);
    });
  }

  private static Location findSafeLocation(World world, Chunk chunk, int x, int z) {
    ChunkSnapshot snapshot = chunk.getChunkSnapshot(false, false, false);
    int localX = Math.floorMod(x, 16);
    int localZ = Math.floorMod(z, 16);
    int minY = world.getMinHeight();
    int maxY = world.getMaxHeight() - 2;

    for (int y = maxY; y > minY + 1; y--) {
      Material feet = snapshot.getBlockType(localX, y, localZ);
      Material head = snapshot.getBlockType(localX, y + 1, localZ);
      Material below = snapshot.getBlockType(localX, y - 1, localZ);
      if (!isSafeColumn(below, feet, head)) continue;
      return new Location(world, x + 0.5d, y, z + 0.5d);
    }
    return null;
  }

  private static boolean isSafeColumn(Material below, Material feet, Material head) {
    if (!below.isSolid()) return false;
    if (below == Material.MAGMA_BLOCK || below == Material.CACTUS || below == Material.CAMPFIRE || below == Material.SOUL_CAMPFIRE) {
      return false;
    }
    return isPassable(feet) && isPassable(head);
  }

  private static boolean isPassable(Material material) {
    if (material.isSolid()) return false;
    if (material == Material.WATER || material == Material.KELP || material == Material.SEAGRASS || material == Material.TALL_SEAGRASS) {
      return false;
    }
    return material != Material.FIRE && material != Material.SOUL_FIRE && material != Material.LAVA;
  }

  private static int randomCoordinate(int origin, int min, int max) {
    int distance = ThreadLocalRandom.current().nextInt(min, max + 1);
    int offset = ThreadLocalRandom.current().nextBoolean() ? distance : -distance;
    return origin + offset;
  }
}
