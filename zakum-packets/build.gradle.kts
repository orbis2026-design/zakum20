import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  `java-library`
  alias(libs.plugins.shadow)
}

dependencies {
  compileOnly(project(":zakum-api"))
  compileOnly(project(":zakum-core"))
  compileOnly(libs.paper.api)

  // Runtime dependency (installed as a plugin). We compile against the API.
  compileOnly(libs.packetevents.spigot)
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
  archiveBaseName.set("ZakumPackets")
  archiveClassifier.set("")

  isReproducibleFileOrder = true
  isPreserveFileTimestamps = false
}

tasks.build {
  dependsOn(tasks.named("shadowJar"))
}
