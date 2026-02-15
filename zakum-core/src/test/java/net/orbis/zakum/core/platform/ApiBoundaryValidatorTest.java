package net.orbis.zakum.core.platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class ApiBoundaryValidatorTest {

  @Test
  void passesWhenNoViolationsFound(@TempDir Path tempDir) throws IOException {
    // Setup test structure
    File projectRoot = tempDir.toFile();
    createModule(tempDir, "test-module", 
        "package com.example;\n" +
        "import net.orbis.zakum.api.ZakumApi;\n" +
        "public class TestClass {}\n");

    ApiBoundaryValidator validator = new ApiBoundaryValidator(
        projectRoot, 
        List.of("test-module")
    );

    ValidationResult result = validator.validate();
    
    assertTrue(result.isPassed());
    assertTrue(result.getViolations().isEmpty());
  }

  @Test
  void detectsCoreImports(@TempDir Path tempDir) throws IOException {
    File projectRoot = tempDir.toFile();
    createModule(tempDir, "bad-module",
        "package com.example;\n" +
        "import net.orbis.zakum.core.ZakumPlugin;\n" +
        "public class BadClass {}\n");

    ApiBoundaryValidator validator = new ApiBoundaryValidator(
        projectRoot,
        List.of("bad-module")
    );

    ValidationResult result = validator.validate();

    assertFalse(result.isPassed());
    assertFalse(result.getViolations().isEmpty());
    assertTrue(result.getViolations().get(0).contains("net.orbis.zakum.core"));
  }

  @Test
  void generatesReadableReport(@TempDir Path tempDir) throws IOException {
    File projectRoot = tempDir.toFile();
    createModule(tempDir, "test-module",
        "package com.example;\n" +
        "import net.orbis.zakum.api.ZakumApi;\n" +
        "public class TestClass {}\n");

    ApiBoundaryValidator validator = new ApiBoundaryValidator(
        projectRoot,
        List.of("test-module")
    );

    String report = validator.generateReport();

    assertNotNull(report);
    assertTrue(report.contains("API Boundary Validation Report"));
    assertTrue(report.contains("PASS") || report.contains("FAIL"));
  }

  @Test
  void handlesNonexistentModules(@TempDir Path tempDir) {
    File projectRoot = tempDir.toFile();
    
    ApiBoundaryValidator validator = new ApiBoundaryValidator(
        projectRoot,
        List.of("nonexistent-module")
    );

    ValidationResult result = validator.validate();

    // Should pass but with warnings
    assertTrue(result.isPassed());
    assertFalse(result.getWarnings().isEmpty());
    assertTrue(result.getWarnings().get(0).contains("not found"));
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
