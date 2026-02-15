package net.orbis.orbisworlds.service;

import net.orbis.orbisworlds.config.WorldsConfig;

public interface WorldsService {

  void start();

  void stop();

  void reload(WorldsConfig config);

  WorldsStatus snapshot();
}
