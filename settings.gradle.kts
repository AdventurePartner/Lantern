pluginManagement {
    repositories {
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://maven.architectury.dev/") }
        maven { url = uri("https://maven.minecraftforge.net/") }
        maven { url = uri("https://maven.neoforged.net/releases") }
        gradlePluginPortal()
    }
}

rootProject.name = "Lantern"

include(":common-core")
include(":fabric-1.21.1")
include(":forge-1.21.1")
include(":forge-1.20.1")
include(":neoforge-1.21.10")
include(":bukkit")
