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
  implementation(libs.mongodb.driver.sync)
  implementation(libs.jedis)

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
  implementation(libs.adventure.minimessage)

  implementation(libs.slf4j.api)
  implementation(libs.slf4j.jdk14)

  compileOnly(libs.annotations)

  // Test dependencies
  testImplementation(libs.junit.jupiter.api)
  testRuntimeOnly(libs.junit.jupiter.engine)
  testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.processResources {
  filesMatching(listOf("plugin.yml", "paper-plugin.yml")) {
    expand("version" to project.version)
  }
  filesMatching("db/migration/*.sql") {
    filteringCharset = "UTF-8"
  }
}

tasks.jar {
  enabled = false
}

tasks.named<ShadowJar>("shadowJar") {
  archiveBaseName.set("Zakum")
  archiveClassifier.set("")

  // Keep MySQL driver canonical (no relocation).
  relocate("com.github.benmanes.caffeine", "net.orbis.zakum.shaded.caffeine")
  relocate("com.zaxxer.hikari", "net.orbis.zakum.shaded.hikari")
  relocate("org.flywaydb", "net.orbis.zakum.shaded.flyway")
  relocate("org.slf4j", "net.orbis.zakum.shaded.slf4j")
  relocate("org.spongepowered.configurate", "net.orbis.zakum.shaded.configurate")
  relocate("okhttp3", "net.orbis.zakum.shaded.okhttp3")
  relocate("okio", "net.orbis.zakum.shaded.okio")
  relocate("io.github.resilience4j", "net.orbis.zakum.shaded.resilience4j")
  relocate("io.micrometer", "net.orbis.zakum.shaded.micrometer")
  relocate("io.prometheus", "net.orbis.zakum.shaded.prometheus")
  relocate("com.mongodb", "net.orbis.zakum.shaded.mongodb")
  relocate("org.bson", "net.orbis.zakum.shaded.bson")
  relocate("redis.clients", "net.orbis.zakum.shaded.redis")
  relocate("kotlin", "net.orbis.zakum.shaded.kotlin")
  relocate("kotlinx", "net.orbis.zakum.shaded.kotlinx")
  relocate("io.vavr", "net.orbis.zakum.shaded.vavr")
  relocate("org.HdrHistogram", "net.orbis.zakum.shaded.hdrhistogram")
  relocate("org.LatencyUtils", "net.orbis.zakum.shaded.latencyutils")

  isReproducibleFileOrder = true
  isPreserveFileTimestamps = false
}

tasks.build {
  dependsOn(tasks.named("shadowJar"))
}
