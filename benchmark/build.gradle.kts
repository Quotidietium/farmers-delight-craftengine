plugins {
    java
}

group = "dev.zurker.fd"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.momirealms.net/releases/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("net.momirealms:craft-engine-core:26.7")
    compileOnly("net.momirealms:craft-engine-bukkit:26.7")
    // benchmark runs inside the smoke server next to the production plugin
    compileOnly(fileTree("../papo-plugin/build/libs") { include("farmers-delight-papo-*.jar") })
}

tasks.jar {
    archiveFileName = "fd-bench.jar"
}
