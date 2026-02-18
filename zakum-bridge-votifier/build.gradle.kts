plugins {
  `java-library`
}

dependencies {
  compileOnly(libs.annotations)
  compileOnly(project(":zakum-api"))
  compileOnly(project(":zakum-core"))
  compileOnly(libs.paper.api)

  // Soft dependency: NuVotifier provides VotifierEvent/Vote classes under com.vexsoftware.votifier.*
  // (pulled via JitPack)
  compileOnly(libs.nuvotifier)
}

tasks.processResources {
  filesMatching("plugin.yml") { expand("version" to project.version) }
}