package net.orbis.zakum.core.platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class ServiceResolutionTest {

  @Test
  void recognizesZakumPluginBase(@TempDir Path tempDir) throws IOException {
    File projectRoot = tempDir.toFile();
    createModule(tempDir, "test-plugin",
        "package com.example;\n" +
        "import net.orbis.zakum.api.plugin.ZakumPluginBase;\n" +
        "import net.orbis.zakum.api.ZakumApi;\n" +
        "public class TestPlugin extends ZakumPluginBase {\n" +
        "  protected void onZakumEnable(ZakumApi api) {}\n" +
        "}\n");

    ServiceResolutionValidator validator = new ServiceResolutionValidator(
        projectRoot,
        List.of("test-plugin")
    );

    ValidationResult result = validator.validate();

    assertTrue(result.isPassed());
    assertTrue(result.getWarnings().isEmpty());
  }

  @Test
  void warnsAboutDirectJavaPlugin(@TempDir Path tempDir) throws IOException {
    File projectRoot = tempDir.toFile();
    createModule(tempDir, "test-plugin",
        "package com.example;\n" +
        "import org.bukkit.plugin.java.JavaPlugin;\n" +
        "import net.orbis.zakum.api.ZakumApi;\n" +
        "public class TestPlugin extends JavaPlugin {\n" +
        "  private ZakumApi api;\n" +
        "}\n");

    ServiceResolutionValidator validator = new ServiceResolutionValidator(
        projectRoot,
        List.of("test-plugin")
    );

    ValidationResult result = validator.validate();

    // Should pass but with warnings
    assertTrue(result.isPassed());
    assertFalse(result.getWarnings().isEmpty());
    assertTrue(result.getWarnings().get(0).contains("ZakumPluginBase"));
  }

  @Test
  void skipsZakumCore(@TempDir Path tempDir) throws IOException {
    File projectRoot = tempDir.toFile();
    createModule(tempDir, "zakum-core",
        "package net.orbis.zakum.core;\n" +
        "public class ZakumPlugin {}\n");

    ServiceResolutionValidator validator = new ServiceResolutionValidator(
        projectRoot,
        List.of("zakum-core")
    );

    ValidationResult result = validator.validate();

    assertTrue(result.isPassed());
  }

  @Test
  void generatesReport(@TempDir Path tempDir) throws IOException {
    File projectRoot = tempDir.toFile();
    createModule(tempDir, "test-plugin",
        "package com.example;\n" +
        "public class SomeClass {}\n");

    ServiceResolutionValidator validator = new ServiceResolutionValidator(
        projectRoot,
        List.of("test-plugin")
    );

    String report = validator.generateReport();

    assertNotNull(report);
    assertTrue(report.contains("Service Resolution Report"));
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
