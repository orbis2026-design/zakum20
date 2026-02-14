import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  `java-library`
  alias(libs.plugins.shadow)
}

dependencies {
  api(project(":zakum-api"))

  compileOnly(libs.paper.api)

  implementation(libs.hikaricp)
  implementation(libs.flyway.core)
  implementation(libs.mysql)
  implementation(libs.caffeine)

  // Central config + mapping
  implementation(libs.configurate.yaml)

  // HTTP + resilience (used by ControlPlane client and future bridges)
  implementation(libs.okhttp)
  implementation(libs.resilience4j.circuitbreaker)
  implementation(libs.resilience4j.retry)
  implementation(libs.resilience4j.bulkhead)
  implementation(libs.resilience4j.ratelimiter)

  // Metrics (Prometheus)
  implementation(libs.micrometer.core)
  implementation(libs.micrometer.prometheus)

  implementation(libs.slf4j.api)
  implementation(libs.slf4j.jdk14)

  compileOnly(libs.annotations)
}

tasks.processResources {
  filesMatching("plugin.yml") {
    expand("version" to project.version)
  }
}

tasks.jar {
  enabled = false
}

tasks.named<ShadowJar>("shadowJar") {
  archiveBaseName.set("Zakum")
  archiveClassifier.set("")

  // Keep MySQL driver canonical (no relocation).
  relocate("com.github.benmanes.caffeine", "net.orbis.zakum.libs.caffeine")
  relocate("com.zaxxer.hikari", "net.orbis.zakum.libs.hikari")
  relocate("org.flywaydb", "net.orbis.zakum.libs.flyway")
  relocate("org.slf4j", "net.orbis.zakum.libs.slf4j")
  relocate("org.spongepowered.configurate", "net.orbis.zakum.libs.configurate")
  relocate("okhttp3", "net.orbis.zakum.libs.okhttp3")
  relocate("okio", "net.orbis.zakum.libs.okio")
  relocate("io.github.resilience4j", "net.orbis.zakum.libs.resilience4j")
  relocate("io.micrometer", "net.orbis.zakum.libs.micrometer")
  relocate("io.prometheus", "net.orbis.zakum.libs.prometheus")
  relocate("kotlin", "net.orbis.zakum.libs.kotlin")
  relocate("kotlinx", "net.orbis.zakum.libs.kotlinx")
  relocate("io.vavr", "net.orbis.zakum.libs.vavr")
  relocate("org.HdrHistogram", "net.orbis.zakum.libs.hdrhistogram")
  relocate("org.LatencyUtils", "net.orbis.zakum.libs.latencyutils")

  isReproducibleFileOrder = true
  isPreserveFileTimestamps = false
}

tasks.build {
  dependsOn(tasks.named("shadowJar"))
}
