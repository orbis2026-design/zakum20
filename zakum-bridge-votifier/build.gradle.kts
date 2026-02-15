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
  compileOnly("com.github.NuVotifier:NuVotifier:2.7.1")
}

tasks.processResources {
  filesMatching("plugin.yml") { expand("version" to project.version) }
}