package net.orbis.zakum.core.platform;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Validates Folia virtual thread safety and regional entity operations.
 * Ensures code uses proper entity/location schedulers for Folia compatibility.
 */
public final class FoliaCompatibilityValidator {

  private final File projectRoot;
  private final List<String> modulesToCheck;

  // Patterns for Folia-incompatible code
  private static final Pattern GLOBAL_REGION_PATTERN = Pattern.compile(
      "runTask\\(|scheduleSyncDelayedTask\\(|scheduleSyncRepeatingTask\\("
  );

  private static final Pattern ENTITY_UNSAFE_PATTERN = Pattern.compile(
      "\\.teleport\\(|\\.setVelocity\\(|\\.damage\\(|\\.remove\\(|"
      + "\\.setHealth\\(|\\.getLocation\\(\\)\\.get"
  );

  private static final Pattern WORLD_UNSAFE_PATTERN = Pattern.compile(
      "\\.getBlockAt\\(|\\.setBlockData\\(|\\.spawnEntity\\(|"
      + "\\.getChunkAt\\(|\\.loadChunk\\("
  );

  private static final Pattern FOLIA_SAFE_PATTERN = Pattern.compile(
      "runAtEntity|runAtLocation|EntityScheduler|RegionScheduler|getScheduler\\(\\)\\.run"
  );

  public FoliaCompatibilityValidator(File projectRoot, List<String> modulesToCheck) {
    this.projectRoot = projectRoot;
    this.modulesToCheck = new ArrayList<>(modulesToCheck);
  }

  /**
   * Validates Folia compatibility across modules.
   */
  public ValidationResult validate() {
    ValidationResult.Builder result = ValidationResult.builder("Folia Compatibility");

    for (String module : modulesToCheck) {
      File moduleRoot = new File(projectRoot, module + "/src/main/java");
      if (!moduleRoot.exists()) {
        result.addWarning(module + ": src/main/java directory not found");
        continue;
      }

      try {
        checkModuleFoliaCompat(module, moduleRoot.toPath(), result);
      } catch (IOException e) {
        result.addWarning(module + ": Failed to scan - " + e.getMessage());
      }
    }

    return result.build();
  }

  private void checkModuleFoliaCompat(String moduleName, Path moduleRoot,
                                       ValidationResult.Builder result) throws IOException {
    try (Stream<Path> paths = Files.walk(moduleRoot)) {
      paths.filter(Files::isRegularFile)
           .filter(p -> p.toString().endsWith(".java"))
           .forEach(javaFile -> {
             try {
               analyzeFileForFolia(moduleName, moduleRoot, javaFile, result);
             } catch (IOException e) {
               // Skip files that can't be read
             }
           });
    }
  }

  private void analyzeFileForFolia(String moduleName, Path moduleRoot, Path javaFile,
                                    ValidationResult.Builder result) throws IOException {
    List<String> lines = Files.readAllLines(javaFile);
    String relativePath = moduleRoot.relativize(javaFile).toString()
        .replace(File.separatorChar, '/');

    boolean usesFoliaSafePatterns = false;
    
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i).trim();
      int lineNum = i + 1;

      // Check for Folia-safe patterns
      if (FOLIA_SAFE_PATTERN.matcher(line).find()) {
        usesFoliaSafePatterns = true;
      }

      // Check for global region scheduler (Folia incompatible)
      if (GLOBAL_REGION_PATTERN.matcher(line).find() && 
          !line.contains("//") && !line.contains("*")) {
        result.addWarning(String.format("%s/%s:%d: Global scheduler usage detected - "
            + "use runAtEntity/runAtLocation for Folia compatibility",
            moduleName, relativePath, lineNum));
      }

      // Check for potentially unsafe entity operations
      if (ENTITY_UNSAFE_PATTERN.matcher(line).find()) {
        // Look for nearby scheduler context
        boolean hasProperScheduling = false;
        for (int j = Math.max(0, i - 10); j < Math.min(i + 3, lines.size()); j++) {
          if (FOLIA_SAFE_PATTERN.matcher(lines.get(j)).find()) {
            hasProperScheduling = true;
            break;
          }
        }
        
        if (!hasProperScheduling) {
          result.addWarning(String.format("%s/%s:%d: Entity operation without "
              + "entity scheduler - ensure proper regional scheduling",
              moduleName, relativePath, lineNum));
        }
      }

      // Check for world modifications
      if (WORLD_UNSAFE_PATTERN.matcher(line).find()) {
        boolean hasLocationScheduler = false;
        for (int j = Math.max(0, i - 10); j < Math.min(i + 3, lines.size()); j++) {
          if (lines.get(j).contains("runAtLocation")) {
            hasLocationScheduler = true;
            break;
          }
        }

        if (!hasLocationScheduler) {
          result.addWarning(String.format("%s/%s:%d: World operation without "
              + "location scheduler - use runAtLocation for Folia compatibility",
              moduleName, relativePath, lineNum));
        }
      }
    }
  }

  /**
   * Generates Folia compatibility report.
   */
  public String generateReport() {
    ValidationResult result = validate();
    StringBuilder report = new StringBuilder();
    report.append("=== Folia Virtual Thread Compatibility Report ===\n\n");
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
        report.append("✓ All code appears Folia-compatible\n\n");
      } else {
        report.append("✓ No violations, but warnings should be reviewed for Folia deployment\n\n");
      }
    }

    report.append("Folia Compatibility Guidelines:\n");
    report.append("  - Use runAtEntity() for entity-specific operations\n");
    report.append("  - Use runAtLocation() for world/block modifications\n");
    report.append("  - Use runAsync() for I/O and computation\n");
    report.append("  - Avoid global/main-thread schedulers\n");
    report.append("  - Regional operations must stay within their region\n");
    report.append("  - Entity operations must use entity's scheduler\n");
    report.append("\nFolia Technical Notes:\n");
    report.append("  - Folia splits the world into independent regions\n");
    report.append("  - Each region runs on its own virtual thread\n");
    report.append("  - Cross-region operations require careful coordination\n");
    report.append("  - Traditional global tick assumptions no longer hold\n");

    return report.toString();
  }
}
