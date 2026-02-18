import org.gradle.api.GradleException
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.jvm.toolchain.JavaLanguageVersion
import java.io.File
import java.util.zip.ZipFile

plugins {
  id("org.owasp.dependencycheck") version "9.0.9" apply false
}

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

// Configure OWASP Dependency Check
apply(plugin = "org.owasp.dependencycheck")

configure<org.owasp.dependencycheck.gradle.extension.DependencyCheckExtension> {
  formats = listOf("HTML", "JSON", "SARIF")
  failBuildOnCVSS = 7.0f
  suppressionFile = "config/owasp-suppressions.xml"
  analyzers.assemblyEnabled = false
  analyzers.nodeEnabled = false
  nvd.apiKey = System.getenv("NVD_API_KEY")
}

val apiBoundaryModules = listOf(
  "zakum-battlepass",
  "zakum-crates",
  "zakum-pets",
  "zakum-miniaturepets",
  "orbis-essentials",
  "orbis-gui",
  "orbis-hud",
  "orbis-worlds",
  "orbis-holograms",
  "orbis-loot"
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

val pluginDescriptorModules: List<String> = rootDir
  .listFiles()
  ?.asSequence()
  ?.filter { it.isDirectory }
  ?.filter { File(it, "src/main/resources/plugin.yml").exists() }
  ?.map { it.name }
  ?.sorted()
  ?.toList()
  ?: emptyList()

val verifyPluginDescriptors = tasks.register("verifyPluginDescriptors") {
  group = "verification"
  description = "Validates plugin.yml baseline contract for all plugin modules."
  doLast {
    val errors = mutableListOf<String>()
    fun hasTopLevelKey(text: String, key: String): Boolean =
      Regex("(?m)^\\s*${Regex.escape(key)}\\s*:").containsMatchIn(text)

    fun dependsOnZakum(text: String): Boolean {
      val lines = text.lines()
      var i = 0
      while (i < lines.size) {
        val line = lines[i]
        val match = Regex("^\\s*(depend|softdepend)\\s*:\\s*(.*)$").find(line)
        if (match == null) {
          i++
          continue
        }
        val inline = match.groupValues[2].trim()
        if (inline.isNotEmpty() && Regex("\\bZakum\\b").containsMatchIn(inline)) {
          return true
        }
        var j = i + 1
        while (j < lines.size) {
          val next = lines[j]
          if (next.trim().isEmpty()) {
            j++
            continue
          }
          if (!next.startsWith(" ") && !next.startsWith("\t")) break
          val bullet = Regex("^\\s*-\\s*(.+)$").find(next)
          if (bullet != null && bullet.groupValues[1].trim() == "Zakum") return true
          j++
        }
        i = j
      }
      return false
    }

    for (module in pluginDescriptorModules) {
      val path = "$module/src/main/resources/plugin.yml"
      val descriptor = file(path)
      if (!descriptor.exists()) {
        errors += "$path missing"
        continue
      }
      val text = descriptor.readText()
      val requiredKeys = listOf("name", "version", "main", "api-version")
      for (key in requiredKeys) {
        if (!hasTopLevelKey(text, key)) {
          errors += "$path missing top-level key: $key"
        }
      }
      val versionMatch = Regex("(?m)^\\s*version\\s*:\\s*(.+?)\\s*$").find(text)
      if (versionMatch == null) {
        errors += "$path missing version key"
      } else {
        val rawVersion = versionMatch.groupValues[1].trim()
        if (rawVersion != "\${version}") {
          errors += "$path version must be \${version}, found: $rawVersion"
        }
      }
      if (module != "zakum-core" && !dependsOnZakum(text)) {
        errors += "$path must declare Zakum in depend/softdepend"
      }
    }

    if (errors.isNotEmpty()) {
      throw GradleException(
        "Plugin descriptor verification failed:\n" + errors.joinToString("\n")
      )
    }
  }
}

val verifyModuleBuildConventions = tasks.register("verifyModuleBuildConventions") {
  group = "verification"
  description = "Validates module build.gradle.kts conventions for plugin development."
  doLast {
    val errors = mutableListOf<String>()
    for (module in pluginDescriptorModules) {
      val path = "$module/build.gradle.kts"
      val script = file(path)
      if (!script.exists()) {
        errors += "$path missing"
        continue
      }
      val text = script.readText()
      if (!text.contains("compileOnly(libs.paper.api)")) {
        errors += "$path must use compileOnly(libs.paper.api)"
      }
      if (text.contains("io.papermc.paper:paper-api:")) {
        errors += "$path must not hardcode paper-api versions"
      }
      if (!text.contains("project(\":zakum-api\")")) {
        errors += "$path must include a dependency on project(\":zakum-api\")"
      }
      if (!text.contains("expand(\"version\" to project.version)")) {
        errors += "$path must expand plugin.yml version via processResources"
      }
    }
    if (errors.isNotEmpty()) {
      throw GradleException(
        "Module build convention verification failed:\n" + errors.joinToString("\n")
      )
    }
  }
}

val releaseShadedCollisionAudit = tasks.register("releaseShadedCollisionAudit") {
  group = "verification"
  description = "Audits shaded relocation collisions and leakage for release artifacts."
  dependsOn(":zakum-core:shadowJar")
  doLast {
    val errors = mutableListOf<String>()
    val warnings = mutableListOf<String>()
    val archiveOwners = linkedMapOf<String, MutableList<String>>()
    val relocationOwners = linkedMapOf<String, String>()
    val moduleRelocations = linkedMapOf<String, MutableSet<String>>()

    val moduleDirs = rootDir
      .listFiles()
      ?.asSequence()
      ?.filter { it.isDirectory }
      ?.filter { File(it, "build.gradle.kts").exists() }
      ?.sortedBy { it.name }
      ?.toList()
      ?: emptyList()

    val archiveRegex = Regex("""archiveBaseName\.set\("([^"]+)"\)""")
    val relocateRegex = Regex("""relocate\("([^"]+)",\s*"([^"]+)"\)""")
    val expectedRelocations = mapOf(
      "zakum-core" to setOf(
        "net.orbis.zakum.shaded.caffeine",
        "net.orbis.zakum.shaded.hikari",
        "net.orbis.zakum.shaded.flyway",
        "net.orbis.zakum.shaded.slf4j",
        "net.orbis.zakum.shaded.configurate",
        "net.orbis.zakum.shaded.okhttp3",
        "net.orbis.zakum.shaded.okio",
        "net.orbis.zakum.shaded.resilience4j",
        "net.orbis.zakum.shaded.micrometer",
        "net.orbis.zakum.shaded.prometheus",
        "net.orbis.zakum.shaded.mongodb",
        "net.orbis.zakum.shaded.bson",
        "net.orbis.zakum.shaded.redis",
        "net.orbis.zakum.shaded.kotlin",
        "net.orbis.zakum.shaded.kotlinx",
        "net.orbis.zakum.shaded.vavr",
        "net.orbis.zakum.shaded.hdrhistogram",
        "net.orbis.zakum.shaded.latencyutils"
      )
    )

    for (moduleDir in moduleDirs) {
      val module = moduleDir.name
      val buildScript = File(moduleDir, "build.gradle.kts")
      val text = buildScript.readText()
      if (!text.contains("libs.plugins.shadow")) continue

      val archiveBaseName = archiveRegex.find(text)?.groupValues?.get(1)?.trim()
      if (!archiveBaseName.isNullOrBlank()) {
        archiveOwners.computeIfAbsent(archiveBaseName) { mutableListOf() }.add(module)
      }

      for (match in relocateRegex.findAll(text)) {
        val source = match.groupValues[1].trim()
        val target = match.groupValues[2].trim()
        if (target.isBlank()) continue
        if (!target.startsWith("net.orbis.zakum.shaded.")) {
          errors += "$module relocation target must start with net.orbis.zakum.shaded.: $target"
        }
        if (source == target) {
          errors += "$module relocation source and target are identical: $source"
        }
        moduleRelocations.computeIfAbsent(module) { linkedSetOf() }.add(target)
        val owner = relocationOwners.putIfAbsent(target, module)
        if (owner != null && owner != module) {
          errors += "Relocation target collision for $target between $owner and $module"
        }
      }
    }

    for ((archive, owners) in archiveOwners) {
      if (owners.size > 1) {
        errors += "Shadow archiveBaseName collision for $archive: ${owners.sorted().joinToString(", ")}"
      }
    }

    for ((module, expected) in expectedRelocations) {
      val seen = moduleRelocations[module].orEmpty()
      val missing = expected.filterNot { it in seen }
      if (missing.isNotEmpty()) {
        errors += "$module missing expected relocation targets: ${missing.joinToString(", ")}"
      }
    }

    val shadedJar = fileTree("zakum-core/build/libs")
      .matching { include("*.jar") }
      .files
      .sortedByDescending { it.lastModified() }
      .firstOrNull()

    if (shadedJar == null || !shadedJar.exists()) {
      errors += "Missing shaded jar: zakum-core/build/libs/*.jar"
    } else {
      val forbiddenPrefixes = listOf(
        "com/github/benmanes/caffeine/",
        "com/zaxxer/hikari/",
        "org/flywaydb/",
        "org/slf4j/",
        "org/spongepowered/configurate/",
        "okhttp3/",
        "okio/",
        "io/github/resilience4j/",
        "io/micrometer/",
        "io/prometheus/",
        "com/mongodb/",
        "org/bson/",
        "redis/clients/",
        "kotlin/",
        "kotlinx/",
        "io/vavr/",
        "org/HdrHistogram/",
        "org/LatencyUtils/"
      )
      val expectedRelocatedPaths = expectedRelocations.getValue("zakum-core")
        .map { it.replace('.', '/') + "/" }
      val foundRelocatedPaths = mutableSetOf<String>()
      ZipFile(shadedJar).use { zip ->
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
          val entry = entries.nextElement()
          if (entry.isDirectory) continue
          val name = entry.name
          if (!name.endsWith(".class")) continue
          if (forbiddenPrefixes.any { name.startsWith(it) }) {
            errors += "Forbidden leakage in shaded jar: $name"
          }
          for (relocatedPrefix in expectedRelocatedPaths) {
            if (name.startsWith(relocatedPrefix)) {
              foundRelocatedPaths += relocatedPrefix
            }
          }
        }
      }
      val missingInJar = expectedRelocatedPaths.filterNot { it in foundRelocatedPaths }
      if (missingInJar.isNotEmpty()) {
        errors += "Missing expected relocated packages in shaded jar: ${missingInJar.joinToString(", ")}"
      }
      if (foundRelocatedPaths.isEmpty()) {
        warnings += "No relocated class paths were detected in ${shadedJar.name}."
      }
    }

    val reportFile = file("build/reports/release/shaded-collision-audit.txt")
    reportFile.parentFile.mkdirs()
    val report = buildString {
      appendLine("releaseShadedCollisionAudit")
      appendLine("status=" + if (errors.isEmpty()) "PASS" else "FAIL")
      if (warnings.isNotEmpty()) {
        appendLine("warnings=" + warnings.size)
        warnings.forEach { appendLine("WARN: $it") }
      }
      if (errors.isNotEmpty()) {
        appendLine("errors=" + errors.size)
        errors.forEach { appendLine("ERROR: $it") }
      } else {
        appendLine("errors=0")
      }
    }
    reportFile.writeText(report)

    if (errors.isNotEmpty()) {
      throw GradleException(
        "Shaded collision audit failed. See ${reportFile.path}:\n" + errors.joinToString("\n")
      )
    }
  }
}

tasks.register("verifyPlatformInfrastructure") {
  group = "verification"
  description = "Runs cross-module platform contract checks (API boundaries + descriptors + module conventions)."
  dependsOn(verifyApiBoundaries, verifyPluginDescriptors, verifyModuleBuildConventions, releaseShadedCollisionAudit)
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
      dependsOn(verifyPluginDescriptors)
      dependsOn(verifyModuleBuildConventions)
      dependsOn(releaseShadedCollisionAudit)
    }
  }
}
