plugins {
  `java-library`
}

dependencies {
  compileOnly(libs.paper.api)
  compileOnly(libs.annotations)

  // Provided by Zakum at runtime (plugin depend + ServicesManager)
  compileOnly(project(":zakum-api"))

  // Optional (only if PlaceholderAPI installed)
  compileOnly(libs.placeholderapi)
}

tasks.processResources {
  filesMatching("plugin.yml") { expand("version" to project.version) }
}
