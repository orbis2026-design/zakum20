plugins {
  `java-library`
}

dependencies {
  compileOnly(libs.paper.api)
  compileOnly(libs.annotations)

  // Provided by Zakum at runtime (plugin depend + ServicesManager)
  compileOnly(project(":zakum-api"))

  // Optional (only if PlaceholderAPI installed)
  compileOnly("me.clip:placeholderapi:2.11.6")
}

tasks.processResources {
  filesMatching("plugin.yml") { expand("version" to project.version) }
}
