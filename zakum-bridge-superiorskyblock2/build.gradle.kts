plugins {
  `java-library`
}

dependencies {
  compileOnly(project(":zakum-api"))
  compileOnly(project(":zakum-core"))
  compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}
