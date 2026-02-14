package net.orbis.zakum.core.economy;

import net.orbis.zakum.api.vault.EconomyResult;
import net.orbis.zakum.api.vault.EconomyService;
import net.orbis.zakum.core.metrics.MetricsMonitor;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Redis-backed global economy capability.
 *
 * Balance is stored in fixed-point integer units to avoid floating-point drift.
 */
public final class RedisGlobalEconomyService implements EconomyService, AutoCloseable {

  private static final String WITHDRAW_SCRIPT = """
    local key = KEYS[1]
    local field = ARGV[1]
    local delta = tonumber(ARGV[2])
    local current = tonumber(redis.call('HGET', key, field) or '0')
    if current < delta then
      return {-1, current}
    end
    local next = current - delta
    redis.call('HSET', key, field, next)
    return {1, next}
    """;

  private final JedisPool jedisPool;
  private final String balancesKey;
  private final String updatesChannel;
  private final long scale;
  private final MetricsMonitor metrics;
  private final Logger logger;

  public RedisGlobalEconomyService(
    JedisPool jedisPool,
    String keyPrefix,
    int scale,
    String updatesChannel,
    MetricsMonitor metrics,
    Logger logger
  ) {
    this.jedisPool = Objects.requireNonNull(jedisPool, "jedisPool");
    String prefix = Objects.requireNonNullElse(keyPrefix, "zakum:economy").trim();
    if (prefix.isBlank()) prefix = "zakum:economy";
    this.balancesKey = prefix + ":balances";
    this.updatesChannel = updatesChannel == null ? "" : updatesChannel.trim();
    this.scale = Math.max(1, scale);
    this.metrics = metrics;
    this.logger = logger;
  }

  @Override
  public boolean available() {
    try (Jedis jedis = jedisPool.getResource()) {
      return "PONG".equalsIgnoreCase(jedis.ping());
    } catch (Throwable ex) {
      return false;
    }
  }

  @Override
  public EconomyResult deposit(UUID playerId, double amount) {
    if (playerId == null) return EconomyResult.fail("playerId is required");
    long units = toUnits(amount);
    if (units <= 0L) return EconomyResult.fail("amount must be > 0");

    try (Jedis jedis = jedisPool.getResource()) {
      long next = jedis.hincrBy(balancesKey, playerId.toString(), units);
      double balance = fromUnits(next);
      publishBalance(jedis, playerId, balance);
      if (metrics != null) metrics.recordAction("economy_deposit");
      return EconomyResult.ok(balance);
    } catch (Throwable ex) {
      logWarn("deposit", ex);
      return EconomyResult.fail("economy backend unavailable");
    }
  }

  @Override
  public EconomyResult withdraw(UUID playerId, double amount) {
    if (playerId == null) return EconomyResult.fail("playerId is required");
    long units = toUnits(amount);
    if (units <= 0L) return EconomyResult.fail("amount must be > 0");

    try (Jedis jedis = jedisPool.getResource()) {
      Object raw = jedis.eval(WITHDRAW_SCRIPT, List.of(balancesKey), List.of(playerId.toString(), String.valueOf(units)));
      if (!(raw instanceof List<?> result) || result.size() < 2) {
        return EconomyResult.fail("invalid redis response");
      }
      long status = toLong(result.get(0));
      long nextUnits = toLong(result.get(1));
      if (status < 0) {
        return EconomyResult.fail("insufficient funds");
      }
      double balance = fromUnits(nextUnits);
      publishBalance(jedis, playerId, balance);
      if (metrics != null) metrics.recordAction("economy_withdraw");
      return EconomyResult.ok(balance);
    } catch (Throwable ex) {
      logWarn("withdraw", ex);
      return EconomyResult.fail("economy backend unavailable");
    }
  }

  @Override
  public double balance(UUID playerId) {
    if (playerId == null) return 0.0d;

    try (Jedis jedis = jedisPool.getResource()) {
      String raw = jedis.hget(balancesKey, playerId.toString());
      if (raw == null || raw.isBlank()) return 0.0d;
      return fromUnits(Long.parseLong(raw));
    } catch (Throwable ex) {
      logWarn("balance", ex);
      return 0.0d;
    }
  }

  @Override
  public void close() {
    jedisPool.close();
  }

  private long toUnits(double amount) {
    if (!Double.isFinite(amount) || amount <= 0.0d) return 0L;
    double scaled = amount * (double) scale;
    if (scaled >= Long.MAX_VALUE) return Long.MAX_VALUE;
    long value = Math.round(scaled);
    return Math.max(0L, value);
  }

  private double fromUnits(long units) {
    return (double) units / (double) scale;
  }

  private void publishBalance(Jedis jedis, UUID playerId, double balance) {
    if (updatesChannel.isBlank()) return;
    jedis.publish(updatesChannel, playerId + ":" + balance);
  }

  private long toLong(Object value) {
    if (value instanceof Number number) return number.longValue();
    if (value instanceof byte[] bytes) return Long.parseLong(new String(bytes));
    return Long.parseLong(String.valueOf(value));
  }

  private void logWarn(String operation, Throwable ex) {
    if (logger == null) return;
    logger.warning("Global economy " + operation + " failed: " + ex.getMessage());
  }
}

