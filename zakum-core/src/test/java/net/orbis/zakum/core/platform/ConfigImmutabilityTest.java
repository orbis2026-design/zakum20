package net.orbis.zakum.core.platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class ConfigImmutabilityTest {

  @Test
  void passesForRecordConfigs(@TempDir Path tempDir) throws IOException {
    File projectRoot = tempDir.toFile();
    createModule(tempDir, "test-module",
        "package com.example;\n" +
        "public record TestConfig(String name, int value) {}\n");

    ConfigSnapshotGenerator validator = new ConfigSnapshotGenerator(
        projectRoot,
        List.of("test-module")
    );

    ValidationResult result = validator.validate();

    assertTrue(result.isPassed());
  }

  @Test
  void detectsMutableFields(@TempDir Path tempDir) throws IOException {
    File projectRoot = tempDir.toFile();
    createModule(tempDir, "test-module",
        "package com.example;\n" +
        "public class TestConfig {\n" +
        "  public String mutableField;\n" +
        "}\n");

    ConfigSnapshotGenerator validator = new ConfigSnapshotGenerator(
        projectRoot,
        List.of("test-module")
    );

    ValidationResult result = validator.validate();

    assertFalse(result.isPassed());
    assertFalse(result.getViolations().isEmpty());
  }

  @Test
  void acceptsFinalFields(@TempDir Path tempDir) throws IOException {
    File projectRoot = tempDir.toFile();
    createModule(tempDir, "test-module",
        "package com.example;\n" +
        "public final class TestConfig {\n" +
        "  private final String name;\n" +
        "  public TestConfig(String name) { this.name = name; }\n" +
        "}\n");

    ConfigSnapshotGenerator validator = new ConfigSnapshotGenerator(
        projectRoot,
        List.of("test-module")
    );

    ValidationResult result = validator.validate();

    assertTrue(result.isPassed());
  }

  @Test
  void warnsAboutSetters(@TempDir Path tempDir) throws IOException {
    File projectRoot = tempDir.toFile();
    createModule(tempDir, "test-module",
        "package com.example;\n" +
        "public class TestConfig {\n" +
        "  private String name;\n" +
        "  public void setName(String name) { this.name = name; }\n" +
        "}\n");

    ConfigSnapshotGenerator validator = new ConfigSnapshotGenerator(
        projectRoot,
        List.of("test-module")
    );

    ValidationResult result = validator.validate();

    // Should have warnings about setters
    assertFalse(result.getWarnings().isEmpty());
  }

  @Test
  void generatesReport(@TempDir Path tempDir) throws IOException {
    File projectRoot = tempDir.toFile();
    createModule(tempDir, "test-module",
        "package com.example;\n" +
        "public record TestConfig(String name) {}\n");

    ConfigSnapshotGenerator validator = new ConfigSnapshotGenerator(
        projectRoot,
        List.of("test-module")
    );

    String report = validator.generateReport();

    assertNotNull(report);
    assertTrue(report.contains("Configuration Immutability Report"));
    assertTrue(report.contains("Best Practices"));
  }

  private void createModule(Path root, String moduleName, String... fileContents) throws IOException {
    Path modulePath = root.resolve(moduleName).resolve("src/main/java/com/example");
    Files.createDirectories(modulePath);

    for (int i = 0; i < fileContents.length; i++) {
      Path javaFile = modulePath.resolve("TestFile" + i + ".java");
      Files.writeString(javaFile, fileContents[i]);
    }
  }
}
