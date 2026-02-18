package net.orbis.zakum.crates.anim;

import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.crates.anim.types.AnimationFactory;
import net.orbis.zakum.crates.anim.types.CrateAnimation;
import net.orbis.zakum.crates.gui.CrateGuiHolder;
import net.orbis.zakum.crates.model.CrateDef;
import net.orbis.zakum.crates.model.RewardDef;
import net.orbis.zakum.crates.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Manages crate opening animations with new modular animation system.
 * 
 * Handles session lifecycle, animation ticking, and reward execution.
 */
public final class CrateAnimatorV2 {

  private final Plugin plugin;
  private final ConcurrentHashMap<UUID, CrateSession> sessions = new ConcurrentHashMap<>();
  private int taskId = -1;
  private final BiConsumer<Player, RewardDef> rewardExecutor;

  public CrateAnimatorV2(Plugin plugin, BiConsumer<Player, RewardDef> rewardExecutor) {
    this.plugin = plugin;
    this.rewardExecutor = rewardExecutor;
  }

  public void start() {
    if (taskId != -1) return;
    taskId = ZakumApi.get().getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, 1L, 1L);
  }

  public void shutdown() {
    if (taskId != -1) {
      ZakumApi.get().getScheduler().cancelTask(taskId);
      taskId = -1;
    }

    // Cancel all active sessions
    for (CrateSession session : sessions.values()) {
      endSession(session, false);
    }
    sessions.clear();
  }

  public boolean isOpening(UUID opener) {
    return sessions.containsKey(opener);
  }

  /**
   * Begin a crate opening with specified animation type.
   * 
   * @param opener Player opening the crate
   * @param crate Crate definition
   * @param animationType Animation type (e.g., "roulette", "explosion")
   * @return true if opening started successfully
   */
  public boolean begin(Player opener, CrateDef crate, String animationType) {
    UUID id = opener.getUniqueId();
    if (sessions.containsKey(id)) return false;

    // Select final reward
    RewardDef finalReward = crate.rewards().pick(new Random());

    // Create animation
    CrateAnimation animation = AnimationFactory.create(animationType);
    animation.initialize(opener, crate, finalReward);

    // Create GUI
    CrateGuiHolder holder = new CrateGuiHolder(id);
    Inventory inv = Bukkit.createInventory(
        holder, 
        27, 
        ItemBuilder.color("&bOpening: &f" + crate.name())
    );
    holder.bind(inv);

    // Create session
    CrateSession session = new CrateSession(
        id, crate, opener.getLocation().clone(), 
        finalReward, holder, inv, animation
    );

    sessions.put(id, session);

    // Open GUI and play sound
    opener.openInventory(inv);
    opener.playSound(opener.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);
    opener.sendTitle(
        ItemBuilder.color("&bOpening..."), 
        ItemBuilder.color(crate.name()), 
        0, 20, 10
    );

    return true;
  }

  /**
   * Begin with default animation type.
   */
  public boolean begin(Player opener, CrateDef crate) {
    return begin(opener, crate, "roulette");
  }

  public void markClosed(UUID opener) {
    CrateSession session = sessions.get(opener);
    if (session != null) {
      session.inventoryClosed = true;
    }
  }

  private void tick() {
    if (sessions.isEmpty()) return;

    Iterator<Map.Entry<UUID, CrateSession>> iterator = sessions.entrySet().iterator();
    
    while (iterator.hasNext()) {
      Map.Entry<UUID, CrateSession> entry = iterator.next();
      CrateSession session = entry.getValue();
      
      // Check if player is still online
      Player player = Bukkit.getPlayer(session.opener);
      if (player == null || !player.isOnline()) {
        session.cancel();
        iterator.remove();
        continue;
      }
      
      // Tick animation
      boolean shouldContinue = session.tick();
      
      // Check completion
      if (!shouldContinue || session.isComplete()) {
        endSession(session, true);
        iterator.remove();
      }
    }
  }

  private void endSession(CrateSession session, boolean grantReward) {
    Player player = Bukkit.getPlayer(session.opener);
    
    if (player != null) {
      // Close inventory if still open
      if (!session.inventoryClosed) {
        player.closeInventory();
      }
      
      // Play completion sound
      if (grantReward) {
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.1f);
      }
    }

    // Grant reward if applicable
    if (grantReward && player != null) {
      rewardExecutor.accept(player, session.finalReward);
    }
    
    // Cleanup animation
    session.cancel();
  }
}
