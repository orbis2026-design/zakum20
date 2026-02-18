plugins {
  `java-library`
}

dependencies {
  compileOnly(libs.annotations)
  compileOnly(project(":zakum-api"))
  compileOnly(project(":zakum-core"))
  compileOnly(libs.paper.api)

  // Soft dependency (present at runtime only when installed)
  compileOnly(libs.vault.api) {
    exclude(group = "org.bukkit", module = "bukkit")
    exclude(group = "org.spigotmc", module = "spigot-api")
  }
}

tasks.processResources {
  filesMatching("plugin.yml") { expand("version" to project.version) }
}
