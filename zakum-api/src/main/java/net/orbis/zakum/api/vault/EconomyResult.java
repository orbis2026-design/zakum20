package net.orbis.zakum.api.vault;

public record EconomyResult(
  boolean success,
  double newBalance,
  String error
) {
  public static EconomyResult ok(double newBalance) {
    return new EconomyResult(true, newBalance, null);
  }

  public static EconomyResult fail(String error) {
    return new EconomyResult(false, 0.0, error);
  }
}
