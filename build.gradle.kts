import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.jvm.toolchain.JavaLanguageVersion

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
      testLogging {
        exceptionFormat = TestExceptionFormat.FULL
      }
    }
  }
}
