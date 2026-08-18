plugins {
    java
}

group = "com.nhoryzon.mc"
version = "1.0.4"
description = "Farmer's Delight rewritten for Papo (Paper 1.21.11) with CraftEngine-hosted assets"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.momirealms.net/releases/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("net.momirealms:craft-engine-core:26.7")
    compileOnly("net.momirealms:craft-engine-bukkit:26.7")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}
