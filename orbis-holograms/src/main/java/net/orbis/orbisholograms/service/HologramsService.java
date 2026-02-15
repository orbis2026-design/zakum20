package net.orbis.orbisholograms.service;

import net.orbis.orbisholograms.config.HologramsConfig;

public interface HologramsService {

  void start();

  void stop();

  void reload(HologramsConfig config);

  HologramsStatus snapshot();
}
