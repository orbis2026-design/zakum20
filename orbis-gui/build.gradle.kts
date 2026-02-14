plugins {
  `java-library`
}

dependencies {
  compileOnly(libs.paper.api)
  compileOnly(libs.annotations)

  // Provided by Zakum at runtime (Zakum shadows zakum-api into its jar).
  compileOnly(project(":zakum-api"))
}

tasks.processResources {
  filesMatching("plugin.yml") { expand("version" to project.version) }
}
