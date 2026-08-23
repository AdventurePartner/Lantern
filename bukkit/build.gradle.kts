plugins {
    kotlin("jvm") version "1.9.21"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "org.lantern"
version = "1.0.0-BETA"

repositories {
    maven {
        name = "AiYo Studio Repository"
        url = uri("https://repo.mc9y.com/snapshots")
    }
    mavenCentral()
}

dependencies {
    compileOnly(fileTree("libs") { include("*.jar") })
    compileOnly("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly("com.aystudio.core:AyCore:1.3.1-BETA")
    compileOnly("me.clip:placeholderapi:2.11.1")
}

kotlin {
    jvmToolchain(8)
}

tasks {
    processResources {
        filesMatching("**/plugin.yml") {
            expand("version" to project.version)
        }
    }
    jar {
        enabled = false
    }
    shadowJar {
        archiveFileName = "LanternPlugin-$version.jar"
        relocate("kotlin", "org.lantern.shadow.kotlin")
    }
}

tasks["build"].finalizedBy(tasks["shadowJar"])
