package net.orbis.zakum.core.net;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class HttpControlPlaneClient implements ControlPlaneClient {

  private static final MediaType JSON = MediaType.parse("application/json");

  private final OkHttpClient client;
  private final String baseUrl;
  private final String authToken;
  private final ExecutorService async;

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
    return CompletableFuture.supplyAsync(() -> execute(newRequest(path, headers).get().build()), async);
  }

  @Override
  public CompletableFuture<ZakumHttpResponse> postJson(String path, String json, Map<String, String> headers) {
    String payload = json == null ? "" : json;
    RequestBody body = RequestBody.create(payload, JSON);
    return CompletableFuture.supplyAsync(
      () -> execute(newRequest(path, headers).post(body).build()),
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
}
