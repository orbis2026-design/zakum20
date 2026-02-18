plugins {
  `java-library`
}

dependencies {
  compileOnly(libs.paper.api)
  compileOnly(libs.annotations)

  // Provided by Zakum at runtime (plugin depend + ServicesManager)
  compileOnly(project(":zakum-api"))
  
  // Optional third-party integrations
  compileOnly(libs.vault.api)
  compileOnly(libs.luckperms.api)
}

tasks.processResources {
  filesMatching("plugin.yml") { expand("version" to project.version) }
}
