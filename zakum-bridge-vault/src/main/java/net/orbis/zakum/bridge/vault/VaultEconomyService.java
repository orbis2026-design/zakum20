package net.orbis.zakum.bridge.vault;

import net.milkbowl.vault.economy.Economy;
import net.orbis.zakum.api.vault.EconomyResult;
import net.orbis.zakum.api.vault.EconomyService;
import org.bukkit.Bukkit;

import java.util.Objects;
import java.util.UUID;

final class VaultEconomyService implements EconomyService {

  private final Economy economy;

  VaultEconomyService(Economy economy) {
    this.economy = Objects.requireNonNull(economy, "economy");
  }

  @Override
  public boolean available() {
    return economy.isEnabled();
  }

  @Override
  public EconomyResult deposit(UUID playerId, double amount) {
    if (!available()) return EconomyResult.fail("Economy disabled");
    if (amount <= 0) return EconomyResult.fail("amount must be > 0");

    var off = Bukkit.getOfflinePlayer(playerId);
    var r = economy.depositPlayer(off, amount);
    if (!r.transactionSuccess()) return EconomyResult.fail(r.errorMessage);

    return EconomyResult.ok(economy.getBalance(off));
  }

  @Override
  public EconomyResult withdraw(UUID playerId, double amount) {
    if (!available()) return EconomyResult.fail("Economy disabled");
    if (amount <= 0) return EconomyResult.fail("amount must be > 0");

    var off = Bukkit.getOfflinePlayer(playerId);
    var r = economy.withdrawPlayer(off, amount);
    if (!r.transactionSuccess()) return EconomyResult.fail(r.errorMessage);

    return EconomyResult.ok(economy.getBalance(off));
  }

  @Override
  public double balance(UUID playerId) {
    if (!available()) return 0.0;
    var off = Bukkit.getOfflinePlayer(playerId);
    return economy.getBalance(off);
  }
}
