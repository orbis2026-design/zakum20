import org.gradle.api.GradleException
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.jvm.toolchain.JavaLanguageVersion
import java.io.File

allprojects {
  group = "net.orbis"
  version = "0.1.0-SNAPSHOT"

  repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-public/")
    // PlaceholderAPI artifacts
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    // Needed for PacketEvents and a few other ecosystem libs.
    maven("https://jitpack.io")
  }
}

val apiBoundaryModules = listOf(
  "zakum-battlepass",
  "zakum-crates",
  "zakum-pets",
  "zakum-miniaturepets",
  "orbis-essentials",
  "orbis-gui"
)

val verifyApiBoundaries = tasks.register("verifyApiBoundaries") {
  group = "verification"
  description = "Fails when feature modules import net.orbis.zakum.core directly."
  doLast {
    val violations = mutableListOf<String>()
    for (module in apiBoundaryModules) {
      val root = file("$module/src/main/java")
      if (!root.exists()) continue
      root.walkTopDown()
        .filter { it.isFile && it.extension == "java" }
        .forEach { source ->
          source.useLines { lines ->
            lines.forEachIndexed { index, line ->
              val trimmed = line.trim()
              if (trimmed.startsWith("import net.orbis.zakum.core.")) {
                val relative = source.relativeTo(project.projectDir).path.replace(File.separatorChar, '/')
                violations += "$relative:${index + 1}: $trimmed"
              }
            }
          }
        }
    }
    if (violations.isNotEmpty()) {
      throw GradleException(
        "Feature modules must depend on zakum-api only (no zakum-core imports):\n" +
          violations.joinToString("\n")
      )
    }
  }
}

subprojects {
  plugins.withId("java") {
    configurations.named("compileOnly") {
      extendsFrom(configurations.named("annotationProcessor").get())
    }

    extensions.configure<JavaPluginExtension>("java") {
      toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }

    dependencies {
      add("compileOnly", libs.lombok.get())
      add("annotationProcessor", libs.lombok.get())
    }

    tasks.withType<JavaCompile>().configureEach {
      options.release.set(21)
      options.encoding = "UTF-8"
      options.compilerArgs.add("-parameters")
    }

    tasks.withType<Test>().configureEach {
      useJUnitPlatform()
      maxHeapSize = "128m"
      jvmArgs("-XX:MaxMetaspaceSize=128m")
      testLogging {
        exceptionFormat = TestExceptionFormat.FULL
      }
    }

    tasks.named("check") {
      dependsOn(verifyApiBoundaries)
    }
  }
}
