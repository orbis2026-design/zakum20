package net.orbis.zakum.core.util;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class PdcStats {

  public static final NamespacedKey PLAYTIME = PdcKeys.PLAYTIME;
  public static final NamespacedKey DEATHS = PdcKeys.DEATHS;
  public static final NamespacedKey KILLS = PdcKeys.KILLS;

  private PdcStats() {}

  public static void increment(Player player, NamespacedKey key) {
    if (player == null || key == null) return;
    PersistentDataContainer pdc = player.getPersistentDataContainer();
    int current = pdc.getOrDefault(key, PersistentDataType.INTEGER, 0);
    pdc.set(key, PersistentDataType.INTEGER, current + 1);
  }
}
