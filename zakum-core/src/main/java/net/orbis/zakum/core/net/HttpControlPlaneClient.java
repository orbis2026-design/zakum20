package net.orbis.zakum.core.net;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import net.orbis.zakum.api.config.ZakumSettings;
import net.orbis.zakum.api.net.ControlPlaneClient;
import net.orbis.zakum.api.net.ZakumHttpResponse;
import okhttp3.*;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * OkHttp-based ControlPlane client with optional Resilience4j.
 *
 * Why OkHttp:
 * - battle-tested connection pooling
 * - clean async callbacks
 *
 * Why Resilience4j:
 * - prevents join-bursts / feature spikes from amplifying partial outages
 * - explicit circuit breaker + retry knobs via config.yml
 */
public final class HttpControlPlaneClient implements ControlPlaneClient {

  private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

  private final HttpUrl base;
  private final String apiKey;
  private final OkHttpClient client;

  private final CircuitBreaker cb; // nullable
  private final Retry retry; // nullable

  private HttpControlPlaneClient(HttpUrl base, String apiKey, OkHttpClient client, CircuitBreaker cb, Retry retry) {
    this.base = base;
    this.apiKey = apiKey;
    this.client = client;
    this.cb = cb;
    this.retry = retry;
  }

  public static Optional<ControlPlaneClient> fromSettings(ZakumSettings settings, Executor async) {
    var cp = settings.controlPlane();
    if (!cp.enabled()) return Optional.empty();

    String baseUrl = cp.baseUrl() == null ? "" : cp.baseUrl().trim();
    if (baseUrl.isBlank()) return Optional.empty();

    HttpUrl base = HttpUrl.parse(baseUrl);
    if (base == null) return Optional.empty();

    var http = settings.http();

    var dispatcher = new Dispatcher(r -> async.execute(r));
    dispatcher.setMaxRequests(http.maxRequests());
    dispatcher.setMaxRequestsPerHost(http.maxRequestsPerHost());

    OkHttpClient.Builder b = new OkHttpClient.Builder()
      .dispatcher(dispatcher)
      .connectTimeout(Duration.ofMillis(http.connectTimeoutMs()))
      .callTimeout(Duration.ofMillis(http.callTimeoutMs()))
      .readTimeout(Duration.ofMillis(http.readTimeoutMs()))
      .writeTimeout(Duration.ofMillis(http.writeTimeoutMs()));

    var res = http.resilience();
    CircuitBreaker cb = null;
    Retry retry = null;

    if (res != null && res.enabled()) {
      var cbc = res.circuitBreaker();
      CircuitBreakerConfig cbCfg = CircuitBreakerConfig.custom()
        .failureRateThreshold(cbc.failureRateThreshold())
        .slowCallRateThreshold(cbc.slowCallRateThreshold())
        .slowCallDurationThreshold(Duration.ofMillis(cbc.slowCallDurationMs()))
        .slidingWindowSize(cbc.slidingWindowSize())
        .minimumNumberOfCalls(cbc.minimumNumberOfCalls())
        .waitDurationInOpenState(Duration.ofMillis(cbc.waitDurationInOpenStateMs()))
        .build();
      cb = CircuitBreaker.of("zakum-controlplane", cbCfg);

      var rc = res.retry();
      RetryConfig rCfg = RetryConfig.custom()
        .maxAttempts(rc.maxAttempts())
        .waitDuration(Duration.ofMillis(rc.waitDurationMs()))
        .build();
      retry = Retry.of("zakum-controlplane", rCfg);
    }

    return Optional.of(new HttpControlPlaneClient(base, cp.apiKey(), b.build(), cb, retry));
  }

  @Override
  public CompletableFuture<ZakumHttpResponse> get(String path, Map<String, String> headers) {
    Request req = baseRequest(path, headers).get().build();
    return execute(req);
  }

  @Override
  public CompletableFuture<ZakumHttpResponse> postJson(String path, String json, Map<String, String> headers) {
    RequestBody body = RequestBody.create(json == null ? "" : json, JSON);
    Request req = baseRequest(path, headers).post(body).build();
    return execute(req);
  }

  private Request.Builder baseRequest(String path, Map<String, String> headers) {
    String clean = (path == null ? "" : path.trim());
    if (clean.startsWith("/")) clean = clean.substring(1);

    HttpUrl url = base.newBuilder().addPathSegments(clean).build();

    Request.Builder b = new Request.Builder().url(url);
    if (apiKey != null && !apiKey.isBlank()) b.header("Authorization", "Bearer " + apiKey);

    if (headers != null) {
      for (var e : headers.entrySet()) {
        if (e.getKey() != null && e.getValue() != null) b.header(e.getKey(), e.getValue());
      }
    }

    return b;
  }

  private CompletableFuture<ZakumHttpResponse> execute(Request req) {
    Supplier<CompletableFuture<ZakumHttpResponse>> call = () -> rawExecute(req);

    if (cb != null) {
      call = CircuitBreaker.decorateSupplier(cb, call);
    }
    if (retry != null) {
      call = Retry.decorateSupplier(retry, call);
    }

    try {
      return call.get();
    } catch (Throwable t) {
      CompletableFuture<ZakumHttpResponse> f = new CompletableFuture<>();
      f.completeExceptionally(t);
      return f;
    }
  }

  private CompletableFuture<ZakumHttpResponse> rawExecute(Request req) {
    CompletableFuture<ZakumHttpResponse> f = new CompletableFuture<>();

    client.newCall(req).enqueue(new Callback() {
      @Override
      public void onFailure(Call call, IOException e) {
        f.completeExceptionally(e);
      }

      @Override
      public void onResponse(Call call, Response response) {
        try (response) {
          String body = response.body() == null ? "" : response.body().string();
          f.complete(new ZakumHttpResponse(response.code(), body, response.headers().toMultimap()));
        } catch (Throwable t) {
          f.completeExceptionally(t);
        }
      }
    });

    return f;
  }
}
