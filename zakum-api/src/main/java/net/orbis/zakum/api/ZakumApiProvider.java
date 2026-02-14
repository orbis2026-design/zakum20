package net.orbis.zakum.api;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Global access helper for environments where services are awkward to thread through.
 *
 * ServicesManager lookup remains the preferred integration path.
 */
public final class ZakumApiProvider {

  private static final AtomicReference<ZakumApi> REF = new AtomicReference<>();

  private ZakumApiProvider() {}

  public static ZakumApi get() {
    ZakumApi api = REF.get();
    if (api == null) {
      throw new IllegalStateException("ZakumApi is not initialized");
    }
    return api;
  }

  public static void set(ZakumApi api) {
    REF.set(Objects.requireNonNull(api, "api"));
  }

  public static void clear() {
    REF.set(null);
  }
}
