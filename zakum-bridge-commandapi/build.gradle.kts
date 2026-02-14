import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  `java-library`
  alias(libs.plugins.shadow)
}

dependencies {
  compileOnly(project(":zakum-api"))
  compileOnly(project(":zakum-core"))
  compileOnly(libs.paper.api)

  // CommandAPI is installed as a plugin. This bridge compiles against its API.
  compileOnly(libs.commandapi.bukkit)
}

tasks.processResources {
  filesMatching("plugin.yml") {
    expand("version" to project.version)
  }
}

tasks.jar { enabled = false }

tasks.named<ShadowJar>("shadowJar") {
  archiveBaseName.set("ZakumBridgeCommandAPI")
  archiveClassifier.set("")

  isReproducibleFileOrder = true
  isPreserveFileTimestamps = false
}

tasks.build { dependsOn(tasks.named("shadowJar")) }
