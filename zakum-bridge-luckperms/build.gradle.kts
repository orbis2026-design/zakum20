plugins {
  `java-library`
}

dependencies {
  compileOnly(libs.annotations)
  compileOnly(project(":zakum-api"))
  compileOnly(project(":zakum-core"))
  compileOnly(libs.paper.api)

  // Soft dependency
  compileOnly("net.luckperms:api:5.5")
}

tasks.processResources {
  filesMatching("plugin.yml") { expand("version" to project.version) }
}