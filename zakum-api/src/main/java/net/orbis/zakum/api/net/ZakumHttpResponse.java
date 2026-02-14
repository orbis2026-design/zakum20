package net.orbis.zakum.api.net;

import java.util.List;
import java.util.Map;

/**
 * Minimal HTTP response abstraction (keeps ControlPlaneClient independent
 * from any particular HTTP implementation).
 */
public record ZakumHttpResponse(
  int statusCode,
  String body,
  Map<String, List<String>> headers
) {}
