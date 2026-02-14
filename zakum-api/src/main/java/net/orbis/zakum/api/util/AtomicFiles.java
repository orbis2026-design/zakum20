package net.orbis.zakum.api.util;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Minimal helpers for safer file writes.
 *
 * We keep this tiny and dependency-free so it can be used from any module.
 */
public final class AtomicFiles {

  private AtomicFiles() {}

  /**
   * Atomically replaces {@code target} with {@code temp} when possible.
   *
   * Contract:
   * - caller writes fully to {@code temp}
   * - this method moves {@code temp} over {@code target}
   */
  public static void moveReplace(Path temp, Path target) throws IOException {
    try {
      Files.move(temp, target,
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.ATOMIC_MOVE
      );
    } catch (AtomicMoveNotSupportedException ex) {
      // Some FS/containers don't support atomic moves. Fallback still prevents partial writes.
      Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
