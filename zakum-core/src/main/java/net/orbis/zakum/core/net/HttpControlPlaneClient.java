package net.orbis.zakum.core.net;

import net.orbis.zakum.api.config.ZakumSettings;
import net.orbis.zakum.api.net.ControlPlaneClient;
import net.orbis.zakum.api.net.ZakumHttpResponse;
import okhttp3.*;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class HttpControlPlaneClient implements ControlPlaneClient {

    private final OkHttpClient client;
    private final String baseUrl;
    private final String authToken;
    private final ExecutorService async;

    public HttpControlPlaneClient(String baseUrl, String authToken, ExecutorService async) {
        this.baseUrl = baseUrl;
        this.authToken = authToken;
        this.async = async;

        // FIXED: Pass the ExecutorService directly, do not use a lambda
        Dispatcher dispatcher = new Dispatcher(async);
        
        this.client = new OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .callTimeout(Duration.ofSeconds(10))
                .build();
    }

    public static HttpControlPlaneClient fromSettings(ZakumSettings settings, ExecutorService async) {
        return new HttpControlPlaneClient(
            settings.server().controlPlaneUrl(),
            settings.server().secret(),
            async
        );
    }

    @Override
    public CompletableFuture<ZakumHttpResponse> post(String endpoint, String jsonBody) {
        return CompletableFuture.supplyAsync(() -> {
            Request req = new Request.Builder()
                    .url(baseUrl + endpoint)
                    .header("Authorization", "Bearer " + authToken)
                    .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                    .build();

            try (Response res = client.newCall(req).execute()) {
                String body = res.body() != null ? res.body().string() : "";
                return new ZakumHttpResponse(res.code(), body);
            } catch (IOException e) {
                throw new RuntimeException("Control plane request failed", e);
            }
        }, async);
    }

    @Override
    public CompletableFuture<ZakumHttpResponse> get(String endpoint) {
        return CompletableFuture.supplyAsync(() -> {
            Request req = new Request.Builder()
                    .url(baseUrl + endpoint)
                    .header("Authorization", "Bearer " + authToken)
                    .get()
                    .build();

            try (Response res = client.newCall(req).execute()) {
                String body = res.body() != null ? res.body().string() : "";
                return new ZakumHttpResponse(res.code(), body);
            } catch (IOException e) {
                throw new RuntimeException("Control plane request failed", e);
            }
        }, async);
    }
}
