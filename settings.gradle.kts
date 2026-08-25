pluginManagement {
    repositories {
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://maven.architectury.dev/") }
        maven { url = uri("https://maven.minecraftforge.net/") }
        maven { url = uri("https://maven.neoforged.net/releases") }
        gradlePluginPortal()
    }
}

plugins {
    // 允许 Gradle 自动下载缺失的 JDK 工具链 (8/17/21)，无需本机预装
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "Lantern"

include(":common-core")
include(":fabric-1.21.1")
include(":forge-1.21.1")
include(":forge-1.20.1")
include(":neoforge-1.21.10")
include(":bukkit")
