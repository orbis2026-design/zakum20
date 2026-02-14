plugins {
  `java-library`
}

repositories {
  mavenCentral()
}

dependencies {
  compileOnly(project(":zakum-api"))
  compileOnly(project(":zakum-core"))
  compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")

  // Soft dependency
  compileOnly("net.luckperms:api:5.5")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}
