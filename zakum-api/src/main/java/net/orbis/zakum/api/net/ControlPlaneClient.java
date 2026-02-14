package net.orbis.zakum.api.net;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface ControlPlaneClient {

  CompletableFuture<ZakumHttpResponse> get(String path, Map<String, String> headers);

  CompletableFuture<ZakumHttpResponse> postJson(String path, String json, Map<String, String> headers);
}
