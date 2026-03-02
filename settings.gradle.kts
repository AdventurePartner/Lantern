pluginManagement {
    repositories {
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://maven.architectury.dev/") }
        gradlePluginPortal()
    }
}

rootProject.name = "Lantern"

include(":bukkit")