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
 * Validates service resolution and plugin lifecycle contracts.
 * Ensures plugins properly implement ZakumPluginBase and handle service dependencies.
 */
public final class ServiceResolutionValidator {

  private final File projectRoot;
  private final List<String> pluginModules;

  private static final Pattern PLUGIN_CLASS_PATTERN = Pattern.compile(
      "class\\s+\\w+\\s+extends\\s+(JavaPlugin|ZakumPluginBase)"
  );

  private static final Pattern SERVICE_LOAD_PATTERN = Pattern.compile(
      "ServicesManager\\.load\\(|optionalService\\(|requiredService\\("
  );

  private static final Pattern ZAKUM_API_USAGE = Pattern.compile(
      "ZakumApi\\s+\\w+|zakum\\(\\)|ZakumApi\\.class"
  );

  public ServiceResolutionValidator(File projectRoot, List<String> pluginModules) {
    this.projectRoot = projectRoot;
    this.pluginModules = new ArrayList<>(pluginModules);
  }

  /**
   * Validates service resolution patterns across plugin modules.
   */
  public ValidationResult validate() {
    ValidationResult.Builder result = ValidationResult.builder("Service Resolution");

    for (String module : pluginModules) {
      // Skip zakum-core itself
      if ("zakum-core".equals(module)) {
        continue;
      }

      File moduleRoot = new File(projectRoot, module + "/src/main/java");
      if (!moduleRoot.exists()) {
        result.addWarning(module + ": src/main/java directory not found");
        continue;
      }

      try {
        checkModuleServiceResolution(module, moduleRoot.toPath(), result);
      } catch (IOException e) {
        result.addWarning(module + ": Failed to scan - " + e.getMessage());
      }
    }

    return result.build();
  }

  private void checkModuleServiceResolution(String moduleName, Path moduleRoot,
                                             ValidationResult.Builder result) throws IOException {
    boolean hasPluginClass = false;
    boolean extendsZakumBase = false;
    boolean usesZakumApi = false;

    try (Stream<Path> paths = Files.walk(moduleRoot)) {
      for (Path javaFile : paths.filter(Files::isRegularFile)
                                .filter(p -> p.toString().endsWith(".java"))
                                .toList()) {
        String content = Files.readString(javaFile);
        
        if (PLUGIN_CLASS_PATTERN.matcher(content).find()) {
          hasPluginClass = true;
          if (content.contains("extends ZakumPluginBase")) {
            extendsZakumBase = true;
          }
        }

        if (ZAKUM_API_USAGE.matcher(content).find()) {
          usesZakumApi = true;
        }

        // Check for proper service resolution
        analyzeServiceUsage(moduleName, moduleRoot, javaFile, content, result);
      }
    }

    // Report findings for this module
    if (hasPluginClass && !extendsZakumBase && usesZakumApi) {
      result.addWarning(String.format("%s: Plugin uses ZakumApi but does not extend "
          + "ZakumPluginBase - consider using ZakumPluginBase for lifecycle safety",
          moduleName));
    }

    if (hasPluginClass && extendsZakumBase) {
      // This is good - proper lifecycle contract
    }
  }

  private void analyzeServiceUsage(String moduleName, Path moduleRoot, Path javaFile,
                                    String content, ValidationResult.Builder result) {
    String relativePath = moduleRoot.relativize(javaFile).toString()
        .replace(File.separatorChar, '/');

    // Check for direct ServicesManager.load without null check
    if (content.contains("ServicesManager.load(") && !content.contains("if (") &&
        !content.contains("!= null") && !content.contains("== null")) {
      String[] lines = content.split("\n");
      for (int i = 0; i < lines.length; i++) {
        if (lines[i].contains("ServicesManager.load(")) {
          // Check if next few lines have null check
          boolean hasNullCheck = false;
          for (int j = i; j < Math.min(i + 5, lines.length); j++) {
            if (lines[j].contains("!= null") || lines[j].contains("== null")) {
              hasNullCheck = true;
              break;
            }
          }
          if (!hasNullCheck) {
            result.addWarning(String.format("%s/%s:%d: ServicesManager.load without "
                + "null check - use optionalService or add null handling",
                moduleName, relativePath, i + 1));
          }
        }
      }
    }

    // Check for good patterns
    if (SERVICE_LOAD_PATTERN.matcher(content).find()) {
      // Using proper service resolution methods - this is good
    }
  }

  /**
   * Generates service resolution report.
   */
  public String generateReport() {
    ValidationResult result = validate();
    StringBuilder report = new StringBuilder();
    report.append("=== Service Resolution Report ===\n\n");
    report.append("Status: ").append(result.isPassed() ? "PASS" : "FAIL").append("\n");
    report.append("Modules checked: ").append(pluginModules.size()).append("\n\n");

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
      report.append("✓ All plugins properly implement service resolution\n\n");
    }

    report.append("Service Resolution Best Practices:\n");
    report.append("  - Extend ZakumPluginBase for automatic ZakumApi resolution\n");
    report.append("  - Use optionalService() for optional dependencies\n");
    report.append("  - Use requiredService() with descriptive error messages\n");
    report.append("  - Handle service unavailability gracefully\n");
    report.append("  - Check plugin.yml for proper depend/softdepend declarations\n");

    return report.toString();
  }
}
