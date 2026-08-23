plugins {
    java
}

group = "dev.zurker"
version = "0.1.0"
description = "FD audit probe: JVM-level capture of PapersDelight method flows + Bukkit event tracing"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    implementation("net.bytebuddy:byte-buddy:1.17.5")
    implementation("net.bytebuddy:byte-buddy-agent:1.17.5")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

// 产物 1: sink —— 只含 JDK 类，运行期被追加进 bootstrap classloader，供被插桩类的 advice 内联代码调用
val sinkJar = tasks.register<Jar>("sinkJar") {
    archiveClassifier.set("sink")
    from(sourceSets.main.get().output)
    include("dev/zurker/fdprobe/sink/**")
}

// 产物 2: 探针主 jar —— 含主类/事件追踪/agent 逻辑 + shaded byte-buddy + 内嵌 sink jar 资源（不含 sink 类本身）
tasks.jar {
    archiveBaseName.set("fd-probe")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    exclude("dev/zurker/fdprobe/sink/**")
    from({ configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) } })
    from(sinkJar.get().archiveFile) { rename { "fd-probe-sink.jar" } }
    dependsOn(sinkJar)
}
