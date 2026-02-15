package net.orbis.zakum.core.platform;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Validates SQL schema health across modules.
 * Checks for Flyway migrations and schema consistency.
 */
public final class DataHealthProbe {

  private final File projectRoot;
  private final List<String> modulesToCheck;

  public DataHealthProbe(File projectRoot, List<String> modulesToCheck) {
    this.projectRoot = projectRoot;
    this.modulesToCheck = new ArrayList<>(modulesToCheck);
  }

  /**
   * Validates data schemas across modules.
   */
  public ValidationResult validate() {
    ValidationResult.Builder result = ValidationResult.builder("Data Schema Health");

    Map<String, SchemaInfo> moduleSchemas = new HashMap<>();

    for (String module : modulesToCheck) {
      SchemaInfo info = checkModuleSchema(module);
      moduleSchemas.put(module, info);

      if (info.hasMigrations && !info.migrationsValid) {
        result.addViolation(String.format("%s: Invalid migration files detected", module));
      }

      if (info.hasMigrations && info.migrationCount == 0) {
        result.addWarning(String.format("%s: Migration directory exists but is empty", module));
      }
    }

    // Generate summary
    int modulesWithMigrations = (int) moduleSchemas.values().stream()
        .filter(info -> info.hasMigrations && info.migrationCount > 0)
        .count();

    if (modulesWithMigrations > 0) {
      result.addWarning(String.format("Found %d module(s) with SQL migrations", 
          modulesWithMigrations));
    }

    return result.build();
  }

  private SchemaInfo checkModuleSchema(String moduleName) {
    SchemaInfo info = new SchemaInfo();
    
    // Check for Flyway migrations
    File migrationDir = new File(projectRoot, 
        moduleName + "/src/main/resources/db/migration");
    
    if (migrationDir.exists() && migrationDir.isDirectory()) {
      info.hasMigrations = true;
      
      try {
        File[] migrations = migrationDir.listFiles((dir, name) -> 
            name.endsWith(".sql") && name.startsWith("V"));
        
        if (migrations != null) {
          info.migrationCount = migrations.length;
          
          // Validate migration naming
          for (File migration : migrations) {
            if (!isValidFlywayName(migration.getName())) {
              info.migrationsValid = false;
              info.invalidFiles.add(migration.getName());
            }
          }
        }
      } catch (Exception e) {
        info.migrationsValid = false;
      }
    }

    // Check for JDBC/Hikari usage
    File buildGradle = new File(projectRoot, moduleName + "/build.gradle.kts");
    if (buildGradle.exists()) {
      try {
        String content = Files.readString(buildGradle.toPath());
        info.usesHikari = content.contains("hikari");
        info.usesFlyway = content.contains("flyway");
      } catch (IOException e) {
        // Skip
      }
    }

    return info;
  }

  private boolean isValidFlywayName(String filename) {
    // Flyway migration naming: V<version>__<description>.sql
    // Example: V1__initial_schema.sql
    return filename.matches("V\\d+__[a-zA-Z0-9_]+\\.sql");
  }

  /**
   * Generates data health report.
   */
  public String generateReport() {
    ValidationResult result = validate();
    StringBuilder report = new StringBuilder();
    report.append("=== Data Schema Health Report ===\n\n");
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
      report.append("✓ All module schemas are valid\n\n");
    }

    // Detailed module breakdown
    report.append("Module Schema Details:\n");
    for (String module : modulesToCheck) {
      SchemaInfo info = checkModuleSchema(module);
      if (info.hasMigrations || info.usesHikari || info.usesFlyway) {
        report.append(String.format("  %s:\n", module));
        if (info.hasMigrations) {
          report.append(String.format("    - Migrations: %d file(s)\n", info.migrationCount));
        }
        if (info.usesHikari) {
          report.append("    - Uses HikariCP connection pooling\n");
        }
        if (info.usesFlyway) {
          report.append("    - Uses Flyway schema migration\n");
        }
        if (!info.migrationsValid) {
          report.append("    - ⚠ Invalid migration files detected\n");
          for (String file : info.invalidFiles) {
            report.append(String.format("      - %s\n", file));
          }
        }
      }
    }

    report.append("\nDatabase Best Practices:\n");
    report.append("  - Use Flyway for schema versioning\n");
    report.append("  - Name migrations: V<version>__<description>.sql\n");
    report.append("  - Never modify existing migrations\n");
    report.append("  - Test migrations on clean database\n");
    report.append("  - Use HikariCP for connection pooling\n");
    report.append("  - Keep all database I/O on async threads\n");

    return report.toString();
  }

  private static class SchemaInfo {
    boolean hasMigrations = false;
    int migrationCount = 0;
    boolean migrationsValid = true;
    List<String> invalidFiles = new ArrayList<>();
    boolean usesHikari = false;
    boolean usesFlyway = false;
  }
}
