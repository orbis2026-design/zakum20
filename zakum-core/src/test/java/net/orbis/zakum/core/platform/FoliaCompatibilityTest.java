package net.orbis.zakum.core.platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class FoliaCompatibilityTest {

  @Test
  void detectsGlobalSchedulerUsage(@TempDir Path tempDir) throws IOException {
    File projectRoot = tempDir.toFile();
    createModule(tempDir, "test-module",
        "package com.example;\n" +
        "public class TestClass {\n" +
        "  void method() {\n" +
        "    scheduler.runTask(plugin, () -> {});\n" +
        "  }\n" +
        "}\n");

    FoliaCompatibilityValidator validator = new FoliaCompatibilityValidator(
        projectRoot,
        List.of("test-module")
    );

    ValidationResult result = validator.validate();

    // Should pass but with warnings
    assertTrue(result.isPassed());
    assertFalse(result.getWarnings().isEmpty());
    assertTrue(result.getWarnings().get(0).contains("Folia"));
  }

  @Test
  void recognizesFoliaSafeSchedulers(@TempDir Path tempDir) throws IOException {
    File projectRoot = tempDir.toFile();
    createModule(tempDir, "test-module",
        "package com.example;\n" +
        "public class TestClass {\n" +
        "  void method() {\n" +
        "    scheduler.runAtEntity(entity, () -> {});\n" +
        "    scheduler.runAtLocation(location, () -> {});\n" +
        "  }\n" +
        "}\n");

    FoliaCompatibilityValidator validator = new FoliaCompatibilityValidator(
        projectRoot,
        List.of("test-module")
    );

    ValidationResult result = validator.validate();

    assertTrue(result.isPassed());
  }

  @Test
  void ignoresComments(@TempDir Path tempDir) throws IOException {
    File projectRoot = tempDir.toFile();
    createModule(tempDir, "test-module",
        "package com.example;\n" +
        "public class TestClass {\n" +
        "  void method() {\n" +
        "    // scheduler.runTask(plugin, () -> {});\n" +
        "  }\n" +
        "}\n");

    FoliaCompatibilityValidator validator = new FoliaCompatibilityValidator(
        projectRoot,
        List.of("test-module")
    );

    ValidationResult result = validator.validate();

    assertTrue(result.isPassed());
    assertTrue(result.getWarnings().isEmpty());
  }

  @Test
  void generatesReport(@TempDir Path tempDir) throws IOException {
    File projectRoot = tempDir.toFile();
    createModule(tempDir, "test-module",
        "package com.example;\n" +
        "public class TestClass {}\n");

    FoliaCompatibilityValidator validator = new FoliaCompatibilityValidator(
        projectRoot,
        List.of("test-module")
    );

    String report = validator.generateReport();

    assertNotNull(report);
    assertTrue(report.contains("Folia"));
    assertTrue(report.contains("Compatibility Guidelines"));
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
