package net.orbis.zakum.api.vault;

import java.util.UUID;

/**
 * Optional Vault economy bridge.
 *
 * Consumers resolve via ServicesManager:
 *   EconomyService eco = Bukkit.getServicesManager().load(EconomyService.class);
 *
 * Notes:
 * - Many economy providers are sync.
 * - Keep calls off hot paths; prefer awarding in small batches when possible.
 */
public interface EconomyService {

  boolean available();

  EconomyResult deposit(UUID playerId, double amount);

  EconomyResult withdraw(UUID playerId, double amount);

  double balance(UUID playerId);
}
