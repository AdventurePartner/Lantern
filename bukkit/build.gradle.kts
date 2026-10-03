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
    maven {
        name = "Lumine Repository"
        url = uri("https://mvn.lumine.io/repository/maven-public/")
    }
    mavenCentral()
}

dependencies {
    compileOnly(fileTree("libs") { include("*.jar") })
    compileOnly("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly("com.aystudio.core:AyCore:1.3.1-BETA")
    compileOnly("me.clip:placeholderapi:2.11.1")
    compileOnly("io.lumine:Mythic-Dist:5.13.0")

    // YamlConfiguration 在 spigot-api 里有完整实现（snakeyaml 为其传递依赖），
    // 配置解析可以走纯 JVM 测试，不必起服务器
    testImplementation("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    testImplementation(kotlin("test-junit5"))
}

tasks.test {
    useJUnitPlatform()
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
