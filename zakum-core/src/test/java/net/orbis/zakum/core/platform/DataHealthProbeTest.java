package net.orbis.zakum.core.platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class DataHealthProbeTest {

  @Test
  void detectsValidFlywayMigrations(@TempDir Path tempDir) throws IOException {
    File projectRoot = tempDir.toFile();
    createModuleWithMigration(tempDir, "test-module", "V1__initial_schema.sql",
        "CREATE TABLE test (id INT PRIMARY KEY);");

    DataHealthProbe probe = new DataHealthProbe(
        projectRoot,
        List.of("test-module")
    );

    ValidationResult result = probe.validate();

    assertTrue(result.isPassed());
  }

  @Test
  void detectsInvalidMigrationNaming(@TempDir Path tempDir) throws IOException {
    File projectRoot = tempDir.toFile();
    createModuleWithMigration(tempDir, "test-module", "invalid_migration.sql",
        "CREATE TABLE test (id INT PRIMARY KEY);");

    DataHealthProbe probe = new DataHealthProbe(
        projectRoot,
        List.of("test-module")
    );

    ValidationResult result = probe.validate();

    assertFalse(result.isPassed());
    assertFalse(result.getViolations().isEmpty());
  }

  @Test
  void handlesModulesWithoutMigrations(@TempDir Path tempDir) throws IOException {
    File projectRoot = tempDir.toFile();
    Path modulePath = tempDir.resolve("test-module");
    Files.createDirectories(modulePath);

    DataHealthProbe probe = new DataHealthProbe(
        projectRoot,
        List.of("test-module")
    );

    ValidationResult result = probe.validate();

    assertTrue(result.isPassed());
  }

  @Test
  void generatesDetailedReport(@TempDir Path tempDir) throws IOException {
    File projectRoot = tempDir.toFile();
    createModuleWithMigration(tempDir, "test-module", "V1__initial.sql", "CREATE TABLE test;");

    DataHealthProbe probe = new DataHealthProbe(
        projectRoot,
        List.of("test-module")
    );

    String report = probe.generateReport();

    assertNotNull(report);
    assertTrue(report.contains("Data Schema Health Report"));
    assertTrue(report.contains("test-module"));
    assertTrue(report.contains("Database Best Practices"));
  }

  @Test
  void countsMultipleMigrations(@TempDir Path tempDir) throws IOException {
    File projectRoot = tempDir.toFile();
    createModuleWithMigration(tempDir, "test-module", 
        "V1__initial.sql", "CREATE TABLE test1;");
    createModuleWithMigration(tempDir, "test-module",
        "V2__add_users.sql", "CREATE TABLE users;");

    DataHealthProbe probe = new DataHealthProbe(
        projectRoot,
        List.of("test-module")
    );

    String report = probe.generateReport();

    assertTrue(report.contains("2 migration(s)"));
  }

  private void createModuleWithMigration(Path root, String moduleName, 
                                          String migrationFile, String content) throws IOException {
    Path migrationPath = root.resolve(moduleName)
                            .resolve("src/main/resources/db/migration");
    Files.createDirectories(migrationPath);

    Path sqlFile = migrationPath.resolve(migrationFile);
    Files.writeString(sqlFile, content);
  }
}
