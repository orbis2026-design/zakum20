plugins {
  `java-library`
}

dependencies {
  compileOnly(libs.annotations)
  compileOnly(project(":zakum-api"))
  compileOnly(libs.paper.api)
}

tasks.processResources {
  filesMatching("plugin.yml") { expand("version" to project.version) }
}
