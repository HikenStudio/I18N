plugins {
    id("java")
    id("com.gradleup.shadow") version "9.0.0-beta13"
}

group = "it.hiken.i18n"
version = "1.0-SNAPSHOT"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.21.10-R0.1-SNAPSHOT")

    implementation("net.kyori:adventure-platform-bukkit:4.4.1")
    implementation("net.kyori:adventure-text-minimessage:4.25.0")
}

tasks.shadowJar {
    relocate("net.kyori", "it.hiken.i18n.libs.kyori")


    minimize()
    archiveClassifier.set("")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}