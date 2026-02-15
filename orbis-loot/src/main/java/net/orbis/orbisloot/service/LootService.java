package net.orbis.orbisloot.service;

import net.orbis.orbisloot.config.LootConfig;

import java.util.Map;
import java.util.Set;

public interface LootService {

  void start();

  void stop();

  void reload(LootConfig config);

  LootStatus snapshot();

  Map<String, Integer> simulate(String crateId, int rolls);

  Set<String> crateIds();
}
