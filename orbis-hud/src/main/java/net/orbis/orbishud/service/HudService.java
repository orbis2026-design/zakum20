package net.orbis.orbishud.service;

import net.orbis.orbishud.config.HudConfig;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

public interface HudService {

  void start();

  void stop();

  HudStatus snapshot();

  void reload(HudConfig newConfig);

  boolean setProfile(UUID playerId, String profileId);

  boolean clearProfile(UUID playerId);

  Set<String> availableProfiles();

  HudConfig config();

  void onPlayerJoin(Player player);

  void onPlayerQuit(Player player);

  void refreshPlayer(Player player);
}
