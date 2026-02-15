package net.orbis.zakum.core.net;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import net.orbis.zakum.api.config.ZakumSettings;
import net.orbis.zakum.api.net.ControlPlaneClient;
import net.orbis.zakum.api.net.ZakumHttpResponse;
import okhttp3.Dispatcher;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class HttpControlPlaneClient implements ControlPlaneClient {

  private static final MediaType JSON = MediaType.parse("application/json");

  private final OkHttpClient client;
  private final String baseUrl;
  private final String authToken;
  private final ExecutorService async;
  private final boolean resilienceEnabled;
  private final CircuitBreaker circuitBreaker;
  private final Retry retry;
  private final AtomicLong calls;
  private final AtomicLong successes;
  private final AtomicLong failures;
  private final AtomicLong retries;
  private final AtomicLong shortCircuits;
  private final AtomicInteger lastStatus;
  private final AtomicLong lastLatencyMs;
  private final AtomicLong lastFailureAtMs;
  private final AtomicReference<String> lastError;

  private HttpControlPlaneClient(String baseUrl, String authToken, ExecutorService async, ZakumSettings.Http http) {
    this.baseUrl = normalizeBaseUrl(baseUrl);
    this.authToken = authToken;
    this.async = async;

    Dispatcher dispatcher = new Dispatcher(async);
    dispatcher.setMaxRequests(http.maxRequests());
    dispatcher.setMaxRequestsPerHost(http.maxRequestsPerHost());

    this.client = new OkHttpClient.Builder()
      .dispatcher(dispatcher)
      .connectTimeout(Duration.ofMillis(http.connectTimeoutMs()))
      .callTimeout(Duration.ofMillis(http.callTimeoutMs()))
      .readTimeout(Duration.ofMillis(http.readTimeoutMs()))
      .writeTimeout(Duration.ofMillis(http.writeTimeoutMs()))
      .build();
    this.calls = new AtomicLong();
    this.successes = new AtomicLong();
    this.failures = new AtomicLong();
    this.retries = new AtomicLong();
    this.shortCircuits = new AtomicLong();
    this.lastStatus = new AtomicInteger(0);
    this.lastLatencyMs = new AtomicLong(0L);
    this.lastFailureAtMs = new AtomicLong(0L);
    this.lastError = new AtomicReference<>("");

    var resilience = http.resilience();
    this.resilienceEnabled = resilience != null && resilience.enabled();
    if (resilienceEnabled) {
      var cbCfg = resilience.circuitBreaker();
      var rtCfg = resilience.retry();
      this.circuitBreaker = CircuitBreaker.of(
        "zakum-control-plane",
        CircuitBreakerConfig.custom()
          .failureRateThreshold(cbCfg.failureRateThreshold())
          .slowCallRateThreshold(cbCfg.slowCallRateThreshold())
          .slowCallDurationThreshold(Duration.ofMillis(cbCfg.slowCallDurationMs()))
          .slidingWindowSize(cbCfg.slidingWindowSize())
          .minimumNumberOfCalls(cbCfg.minimumNumberOfCalls())
          .waitDurationInOpenState(Duration.ofMillis(cbCfg.waitDurationInOpenStateMs()))
          .build()
      );
      this.retry = Retry.of(
        "zakum-control-plane",
        RetryConfig.custom()
          .maxAttempts(rtCfg.maxAttempts())
          .waitDuration(Duration.ofMillis(rtCfg.waitDurationMs()))
          .retryExceptions(RuntimeException.class)
          .ignoreExceptions(CallNotPermittedException.class)
          .build()
      );
      this.retry.getEventPublisher().onRetry(event -> retries.incrementAndGet());
      this.circuitBreaker.getEventPublisher().onCallNotPermitted(event -> shortCircuits.incrementAndGet());
    } else {
      this.circuitBreaker = null;
      this.retry = null;
    }
  }

  public static Optional<ControlPlaneClient> fromSettings(ZakumSettings settings, ExecutorService async) {
    var cp = settings.controlPlane();
    if (!cp.enabled()) return Optional.empty();
    if (cp.baseUrl() == null || cp.baseUrl().isBlank()) return Optional.empty();
    if (cp.apiKey() == null || cp.apiKey().isBlank()) return Optional.empty();
    return Optional.of(new HttpControlPlaneClient(cp.baseUrl(), cp.apiKey(), async, settings.http()));
  }

  @Override
  public CompletableFuture<ZakumHttpResponse> get(String path, Map<String, String> headers) {
    return CompletableFuture.supplyAsync(() -> executeWithResilience(newRequest(path, headers).get().build()), async);
  }

  @Override
  public CompletableFuture<ZakumHttpResponse> postJson(String path, String json, Map<String, String> headers) {
    String payload = json == null ? "" : json;
    RequestBody body = RequestBody.create(payload, JSON);
    return CompletableFuture.supplyAsync(
      () -> executeWithResilience(newRequest(path, headers).post(body).build()),
      async
    );
  }

  private Request.Builder newRequest(String path, Map<String, String> headers) {
    Request.Builder builder = new Request.Builder()
      .url(resolveUrl(path))
      .header("Authorization", "Bearer " + authToken);

    if (headers != null) {
      for (Map.Entry<String, String> e : headers.entrySet()) {
        if (e.getKey() == null || e.getKey().isBlank()) continue;
        if (e.getValue() == null) continue;
        builder.header(e.getKey(), e.getValue());
      }
    }

    return builder;
  }

  private ZakumHttpResponse executeWithResilience(Request request) {
    calls.incrementAndGet();
    long started = System.currentTimeMillis();
    Supplier<ZakumHttpResponse> call = () -> execute(request);
    if (resilienceEnabled && circuitBreaker != null && retry != null) {
      call = Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(circuitBreaker, call));
    }
    try {
      ZakumHttpResponse response = call.get();
      successes.incrementAndGet();
      lastStatus.set(response.statusCode());
      lastError.set("");
      lastLatencyMs.set(Math.max(0L, System.currentTimeMillis() - started));
      return response;
    } catch (RuntimeException ex) {
      failures.incrementAndGet();
      lastStatus.set(0);
      lastFailureAtMs.set(System.currentTimeMillis());
      lastLatencyMs.set(Math.max(0L, System.currentTimeMillis() - started));
      String msg = ex.getMessage();
      lastError.set(msg == null ? ex.getClass().getSimpleName() : msg);
      throw ex;
    }
  }

  private ZakumHttpResponse execute(Request request) {
    try (Response response = client.newCall(request).execute()) {
      String body = response.body() != null ? response.body().string() : "";
      Map<String, List<String>> headers = response.headers().toMultimap();
      return new ZakumHttpResponse(response.code(), body, headers);
    } catch (IOException e) {
      throw new RuntimeException("Control plane request failed", e);
    }
  }

  private String resolveUrl(String path) {
    if (path == null || path.isBlank()) return baseUrl;
    if (path.startsWith("http://") || path.startsWith("https://")) return path;
    if (path.charAt(0) == '/') return baseUrl + path;
    return baseUrl + "/" + path;
  }

  private static String normalizeBaseUrl(String url) {
    String trimmed = url == null ? "" : url.trim();
    if (trimmed.endsWith("/")) {
      return trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed;
  }

  public String baseUrl() {
    return baseUrl;
  }

  public ResilienceSnapshot snapshot() {
    String state = resilienceEnabled && circuitBreaker != null
      ? circuitBreaker.getState().name()
      : "DISABLED";
    return new ResilienceSnapshot(
      resilienceEnabled,
      state,
      calls.get(),
      successes.get(),
      failures.get(),
      retries.get(),
      shortCircuits.get(),
      lastStatus.get(),
      lastLatencyMs.get(),
      lastFailureAtMs.get(),
      lastError.get()
    );
  }

  public record ResilienceSnapshot(
    boolean resilienceEnabled,
    String circuitState,
    long calls,
    long successes,
    long failures,
    long retries,
    long shortCircuits,
    int lastStatus,
    long lastLatencyMs,
    long lastFailureAtMs,
    String lastError
  ) {}
}
