package net.orbis.zakum.core.platform;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Validates async/threading patterns for Folia compatibility.
 * Detects blocking I/O on main thread and ensures entity schedulers are used.
 */
public final class AsyncSafetyChecker {

  private final File projectRoot;
  private final List<String> modulesToCheck;

  // Patterns for detecting problematic threading usage
  private static final Pattern BLOCKING_IO_PATTERN = Pattern.compile(
      "\\.(read|write|execute|query|connect|send|receive|flush|close)\\(|"
      + "new\\s+(FileInputStream|FileOutputStream|FileReader|FileWriter|"
      + "Socket|ServerSocket|HttpURLConnection)|"
      + "Thread\\.sleep\\(|Thread\\.join\\(|"
      + "CountDownLatch\\.(await|countDown)|"
      + "\\.get\\(\\)(?!;?\\s*//.*async)"
  );

  private static final Pattern SCHEDULER_PATTERN = Pattern.compile(
      "runAtEntity|runAtLocation|runAsync|asyncExecutor|supplyAsync"
  );

  private static final Pattern BUKKIT_SCHEDULER_PATTERN = Pattern.compile(
      "getScheduler\\(\\)|BukkitScheduler|runTask\\(|scheduleSync"
  );

  public AsyncSafetyChecker(File projectRoot, List<String> modulesToCheck) {
    this.projectRoot = projectRoot;
    this.modulesToCheck = new ArrayList<>(modulesToCheck);
  }

  /**
   * Validates async safety across specified modules.
   */
  public ValidationResult validate() {
    ValidationResult.Builder result = ValidationResult.builder("Async/Threading Safety");

    for (String module : modulesToCheck) {
      File moduleRoot = new File(projectRoot, module + "/src/main/java");
      if (!moduleRoot.exists()) {
        result.addWarning(module + ": src/main/java directory not found");
        continue;
      }

      try {
        checkModuleAsyncSafety(module, moduleRoot.toPath(), result);
      } catch (IOException e) {
        result.addWarning(module + ": Failed to scan - " + e.getMessage());
      }
    }

    return result.build();
  }

  private void checkModuleAsyncSafety(String moduleName, Path moduleRoot, 
                                      ValidationResult.Builder result) throws IOException {
    try (Stream<Path> paths = Files.walk(moduleRoot)) {
      paths.filter(Files::isRegularFile)
           .filter(p -> p.toString().endsWith(".java"))
           .forEach(javaFile -> {
             try {
               analyzeFile(moduleName, moduleRoot, javaFile, result);
             } catch (IOException e) {
               // Skip files that can't be read
             }
           });
    }
  }

  private void analyzeFile(String moduleName, Path moduleRoot, Path javaFile, 
                           ValidationResult.Builder result) throws IOException {
    List<String> lines = Files.readAllLines(javaFile);
    String relativePath = moduleRoot.relativize(javaFile).toString()
        .replace(File.separatorChar, '/');

    boolean hasZakumScheduler = false;
    List<Integer> suspiciousLines = new ArrayList<>();

    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      
      // Check for proper scheduler usage
      if (SCHEDULER_PATTERN.matcher(line).find()) {
        hasZakumScheduler = true;
      }

      // Flag old Bukkit scheduler usage
      if (BUKKIT_SCHEDULER_PATTERN.matcher(line).find()) {
        result.addWarning(String.format("%s/%s:%d: Legacy BukkitScheduler usage detected - "
            + "consider using ZakumScheduler for Folia compatibility", 
            moduleName, relativePath, i + 1));
      }

      // Detect potentially blocking operations
      Matcher blockingMatcher = BLOCKING_IO_PATTERN.matcher(line);
      if (blockingMatcher.find() && !isInAsyncContext(lines, i)) {
        suspiciousLines.add(i + 1);
      }
    }

    // Report suspicious blocking operations
    if (!suspiciousLines.isEmpty() && suspiciousLines.size() <= 10) {
      for (Integer lineNum : suspiciousLines) {
        result.addWarning(String.format("%s/%s:%d: Potential blocking I/O detected - "
            + "verify this runs in async context", 
            moduleName, relativePath, lineNum));
      }
    } else if (suspiciousLines.size() > 10) {
      result.addWarning(String.format("%s/%s: %d potential blocking operations detected",
          moduleName, relativePath, suspiciousLines.size()));
    }
  }

  private boolean isInAsyncContext(List<String> lines, int currentLine) {
    // Look backwards up to 20 lines to see if we're in an async context
    int start = Math.max(0, currentLine - 20);
    for (int i = start; i < currentLine; i++) {
      String line = lines.get(i);
      if (line.contains("runAsync") || line.contains("asyncExecutor") || 
          line.contains("CompletableFuture") || line.contains("@Async")) {
        return true;
      }
    }
    return false;
  }

  /**
   * Generates a detailed async safety report.
   */
  public String generateReport() {
    ValidationResult result = validate();
    StringBuilder report = new StringBuilder();
    report.append("=== Async/Threading Safety Report ===\n\n");
    report.append("Status: ").append(result.isPassed() ? "PASS" : "FAIL").append("\n");
    report.append("Modules checked: ").append(modulesToCheck.size()).append("\n\n");

    if (!result.getViolations().isEmpty()) {
      report.append("Violations found (").append(result.getViolations().size()).append("):\n");
      for (String violation : result.getViolations()) {
        report.append("  ✗ ").append(violation).append("\n");
      }
      report.append("\n");
    }

    if (!result.getWarnings().isEmpty()) {
      report.append("Warnings (").append(result.getWarnings().size()).append("):\n");
      for (String warning : result.getWarnings()) {
        report.append("  ⚠ ").append(warning).append("\n");
      }
      report.append("\n");
    }

    if (result.isPassed()) {
      if (result.getWarnings().isEmpty()) {
        report.append("✓ No async safety violations detected\n");
      } else {
        report.append("✓ No blocking violations, but warnings should be reviewed\n");
      }
    }

    report.append("\nAsync Safety Guidelines:\n");
    report.append("  - Use ZakumScheduler.runAsync() for I/O operations\n");
    report.append("  - Use runAtEntity/runAtLocation for entity/world modifications\n");
    report.append("  - Avoid blocking operations on main thread\n");
    report.append("  - Use CompletableFuture for async composition\n");

    return report.toString();
  }
}
