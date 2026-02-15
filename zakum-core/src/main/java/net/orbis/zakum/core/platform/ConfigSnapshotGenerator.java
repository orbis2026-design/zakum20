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
 * Validates configuration classes for immutability and thread-safety.
 * Ensures configs use records or immutable patterns.
 */
public final class ConfigSnapshotGenerator {

  private final File projectRoot;
  private final List<String> modulesToCheck;

  private static final Pattern CONFIG_CLASS_PATTERN = Pattern.compile(
      "class\\s+\\w+Config|class\\s+\\w+Settings|class\\s+\\w+Configuration"
  );

  private static final Pattern MUTABLE_FIELD_PATTERN = Pattern.compile(
      "public\\s+(?!final\\s|static\\s+final\\s)\\w+\\s+\\w+\\s*[;=]"
  );

  private static final Pattern SETTER_METHOD_PATTERN = Pattern.compile(
      "public\\s+void\\s+set[A-Z]\\w*\\("
  );

  public ConfigSnapshotGenerator(File projectRoot, List<String> modulesToCheck) {
    this.projectRoot = projectRoot;
    this.modulesToCheck = new ArrayList<>(modulesToCheck);
  }

  /**
   * Validates configuration immutability across modules.
   */
  public ValidationResult validate() {
    ValidationResult.Builder result = ValidationResult.builder("Configuration Immutability");

    for (String module : modulesToCheck) {
      File moduleRoot = new File(projectRoot, module + "/src/main/java");
      if (!moduleRoot.exists()) {
        result.addWarning(module + ": src/main/java directory not found");
        continue;
      }

      try {
        checkModuleConfigs(module, moduleRoot.toPath(), result);
      } catch (IOException e) {
        result.addWarning(module + ": Failed to scan - " + e.getMessage());
      }
    }

    return result.build();
  }

  private void checkModuleConfigs(String moduleName, Path moduleRoot,
                                   ValidationResult.Builder result) throws IOException {
    try (Stream<Path> paths = Files.walk(moduleRoot)) {
      paths.filter(Files::isRegularFile)
           .filter(p -> p.toString().endsWith(".java"))
           .filter(p -> {
             try {
               String content = Files.readString(p);
               return CONFIG_CLASS_PATTERN.matcher(content).find();
             } catch (IOException e) {
               return false;
             }
           })
           .forEach(configFile -> {
             try {
               analyzeConfigFile(moduleName, moduleRoot, configFile, result);
             } catch (IOException e) {
               // Skip files that can't be read
             }
           });
    }
  }

  private void analyzeConfigFile(String moduleName, Path moduleRoot, Path configFile,
                                  ValidationResult.Builder result) throws IOException {
    String content = Files.readString(configFile);
    List<String> lines = Files.readAllLines(configFile);
    String relativePath = moduleRoot.relativize(configFile).toString()
        .replace(File.separatorChar, '/');

    // Check if it's a record (records are immutable by default)
    if (content.contains("public record ") || content.contains("record ")) {
      // Records are good
      return;
    }

    // Check for mutable public fields
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i).trim();
      if (MUTABLE_FIELD_PATTERN.matcher(line).find()) {
        result.addViolation(String.format("%s/%s:%d: Mutable public field detected - "
            + "config fields should be final or use records", 
            moduleName, relativePath, i + 1));
      }

      if (SETTER_METHOD_PATTERN.matcher(line).find()) {
        result.addWarning(String.format("%s/%s:%d: Setter method detected - "
            + "configs should be immutable", 
            moduleName, relativePath, i + 1));
      }
    }

    // Check if class has proper immutability markers
    if (!content.contains("final class") && !content.contains("@Immutable") &&
        !content.contains("@Value")) {
      result.addWarning(String.format("%s/%s: Config class should be final or use immutable "
          + "pattern (record, @Value, or final class with final fields)",
          moduleName, relativePath));
    }
  }

  /**
   * Generates typed config snapshots and validation report.
   */
  public String generateReport() {
    ValidationResult result = validate();
    StringBuilder report = new StringBuilder();
    report.append("=== Configuration Immutability Report ===\n\n");
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
      report.append("✓ All configuration classes follow immutability patterns\n\n");
    }

    report.append("Best Practices:\n");
    report.append("  - Use Java records for config DTOs\n");
    report.append("  - Make config classes final with final fields\n");
    report.append("  - Avoid setters in configuration classes\n");
    report.append("  - Use builder pattern for complex configs\n");
    report.append("  - Validate ranges at construction time\n");

    return report.toString();
  }
}
