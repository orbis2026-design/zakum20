plugins {
  `java-library`
}

dependencies {
  compileOnly(project(":zakum-api"))
  compileOnly(project(":zakum-core"))
  compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")

  // Soft dependency (present at runtime only when installed)
  compileOnly("me.clip:placeholderapi:2.11.6")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}
