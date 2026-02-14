package net.orbis.zakum.core.packet;

import net.orbis.zakum.api.concurrent.ZakumScheduler;
import org.bukkit.plugin.Plugin;

/**
 * Compatibility wrapper around the 1.21.11 implementation.
 */
public final class PacketAnimationService extends AnimationService1_21_11 {

  public PacketAnimationService(Plugin plugin, ZakumScheduler scheduler) {
    super(plugin, scheduler);
  }
}
