pluginManagement {
    repositories {
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://maven.architectury.dev/") }
        gradlePluginPortal()
    }
}

rootProject.name = "Lantern"

include(":bukkit")

listOf("fabric-1.21.1", "forge-1.21.1", "forge-1.20.1").forEach { moduleName ->
    if (file("$moduleName/build.gradle.kts").isFile || file("$moduleName/build.gradle").isFile) {
        include(":$moduleName")
    }
}