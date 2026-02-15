package net.orbis.zakum.core.platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class AsyncSafetyCheckerTest {

  @Test
  void detectsLegacyBukkitScheduler(@TempDir Path tempDir) throws IOException {
    File projectRoot = tempDir.toFile();
    createModule(tempDir, "test-module",
        "package com.example;\n" +
        "public class TestClass {\n" +
        "  void method() {\n" +
        "    getScheduler().runTask(plugin, () -> {});\n" +
        "  }\n" +
        "}\n");

    AsyncSafetyChecker checker = new AsyncSafetyChecker(
        projectRoot,
        List.of("test-module")
    );

    ValidationResult result = checker.validate();

    // Should pass but with warnings about legacy scheduler
    assertTrue(result.isPassed());
    assertFalse(result.getWarnings().isEmpty());
  }

  @Test
  void recognizesFoliaSafePatterns(@TempDir Path tempDir) throws IOException {
    File projectRoot = tempDir.toFile();
    createModule(tempDir, "test-module",
        "package com.example;\n" +
        "public class TestClass {\n" +
        "  void method() {\n" +
        "    scheduler.runAtEntity(entity, () -> {});\n" +
        "    scheduler.runAtLocation(location, () -> {});\n" +
        "  }\n" +
        "}\n");

    AsyncSafetyChecker checker = new AsyncSafetyChecker(
        projectRoot,
        List.of("test-module")
    );

    ValidationResult result = checker.validate();

    assertTrue(result.isPassed());
  }

  @Test
  void generatesReport(@TempDir Path tempDir) throws IOException {
    File projectRoot = tempDir.toFile();
    createModule(tempDir, "test-module",
        "package com.example;\n" +
        "public class TestClass {}\n");

    AsyncSafetyChecker checker = new AsyncSafetyChecker(
        projectRoot,
        List.of("test-module")
    );

    String report = checker.generateReport();

    assertNotNull(report);
    assertTrue(report.contains("Async/Threading Safety Report"));
    assertTrue(report.contains("Async Safety Guidelines"));
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
