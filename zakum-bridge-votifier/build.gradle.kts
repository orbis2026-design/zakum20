plugins {
  `java-library`
}

repositories {
  mavenCentral()
  maven("https://jitpack.io")
}

dependencies {
  compileOnly(project(":zakum-api"))
  compileOnly(project(":zakum-core"))
  compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")

  // Soft dependency: NuVotifier provides VotifierEvent/Vote classes under com.vexsoftware.votifier.*
  // (pulled via JitPack)
  compileOnly("com.github.NuVotifier:NuVotifier:2.7.1")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}
