package net.orbis.zakum.core.platform;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Validates that feature modules only import from zakum-api package,
 * preventing direct coupling to zakum-core implementation.
 */
public final class ApiBoundaryValidator {

  private final File projectRoot;
  private final List<String> featureModules;

  public ApiBoundaryValidator(File projectRoot, List<String> featureModules) {
    this.projectRoot = projectRoot;
    this.featureModules = new ArrayList<>(featureModules);
  }

  /**
   * Validates API boundary compliance across all feature modules.
   * 
   * @return ValidationResult indicating pass/fail and any violations found
   */
  public ValidationResult validate() {
    ValidationResult.Builder result = ValidationResult.builder("API Boundary Compliance");

    for (String module : featureModules) {
      File moduleRoot = new File(projectRoot, module + "/src/main/java");
      if (!moduleRoot.exists()) {
        result.addWarning(module + ": src/main/java directory not found");
        continue;
      }

      try {
        List<String> violations = scanModuleForCoreImports(module, moduleRoot.toPath());
        violations.forEach(result::addViolation);
      } catch (IOException e) {
        result.addWarning(module + ": Failed to scan - " + e.getMessage());
      }
    }

    return result.build();
  }

  private List<String> scanModuleForCoreImports(String moduleName, Path moduleRoot) throws IOException {
    List<String> violations = new ArrayList<>();

    try (Stream<Path> paths = Files.walk(moduleRoot)) {
      paths.filter(Files::isRegularFile)
           .filter(p -> p.toString().endsWith(".java"))
           .forEach(javaFile -> {
             try {
               List<String> lines = Files.readAllLines(javaFile);
               for (int i = 0; i < lines.size(); i++) {
                 String line = lines.get(i).trim();
                 if (line.startsWith("import net.orbis.zakum.core.")) {
                   String relativePath = moduleRoot.relativize(javaFile).toString()
                       .replace(File.separatorChar, '/');
                   violations.add(String.format("%s/%s:%d: %s", 
                       moduleName, relativePath, i + 1, line));
                 }
               }
             } catch (IOException e) {
               // Skip files that can't be read
             }
           });
    }

    return violations;
  }

  /**
   * Generates a detailed report of API boundary compliance.
   */
  public String generateReport() {
    ValidationResult result = validate();
    StringBuilder report = new StringBuilder();
    report.append("=== API Boundary Validation Report ===\n\n");
    report.append("Status: ").append(result.isPassed() ? "PASS" : "FAIL").append("\n");
    report.append("Modules checked: ").append(featureModules.size()).append("\n\n");

    if (!result.getViolations().isEmpty()) {
      report.append("Violations found (").append(result.getViolations().size()).append("):\n");
      for (String violation : result.getViolations()) {
        report.append("  - ").append(violation).append("\n");
      }
      report.append("\n");
    }

    if (!result.getWarnings().isEmpty()) {
      report.append("Warnings (").append(result.getWarnings().size()).append("):\n");
      for (String warning : result.getWarnings()) {
        report.append("  - ").append(warning).append("\n");
      }
    }

    if (result.isPassed() && result.getWarnings().isEmpty()) {
      report.append("✓ All feature modules comply with API boundary rules\n");
    }

    return report.toString();
  }
}
