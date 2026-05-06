plugins {
    kotlin("jvm") version "1.9.21" apply false
    id("dev.architectury.loom") version "1.7.423" apply false
    id("architectury-plugin") version "3.4.160" apply false
    id("com.github.johnrengelman.shadow") version "8.1.1" apply false
}

allprojects {
    group = property("maven_group").toString()
    version = property("mod_version").toString()

    repositories {
        mavenCentral()
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://maven.architectury.dev/") }
        maven { url = uri("https://maven.minecraftforge.net/") }
        maven {
            name = "GeckoLib"
            url = uri("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/")
            content {
                includeGroupByRegex("software\\.bernie.*")
                includeGroup("com.eliotlash.mclib")
            }
        }
    }
}
