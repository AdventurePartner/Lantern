import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.Sync

plugins {
    base
    kotlin("jvm") version "1.9.21" apply false
    id("dev.architectury.loom") version "1.7.423" apply false
    id("architectury-plugin") version "3.4.160" apply false
    id("com.github.johnrengelman.shadow") version "8.1.1" apply false
    id("net.neoforged.moddev") version "2.0.142" apply false
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

val artifactTasksByProject = mapOf(
    "fabric-1.21.1" to "remapJar",
    "forge-1.21.1" to "remapJar",
    "forge-1.20.1" to "remapJar",
    "neoforge-1.21.10" to "jar",
    "bukkit" to "shadowJar"
)

val copyJarsToObject by tasks.registering(Sync::class) {
    group = "build"
    description = "Syncs packaged platform jars into object/."
    into(layout.projectDirectory.dir("object"))
    duplicatesStrategy = DuplicatesStrategy.FAIL
}

gradle.projectsEvaluated {
    val moduleBuildTasks = subprojects.mapNotNull { it.tasks.findByName("build") }
    val artifactTasks = artifactTasksByProject.mapNotNull { (projectName, taskName) ->
        findProject(":$projectName")?.tasks?.findByName(taskName)
    }

    copyJarsToObject.configure {
        dependsOn(artifactTasks)
        mustRunAfter(moduleBuildTasks)
        artifactTasks.forEach { artifactTask ->
            from(artifactTask.outputs.files) {
                include("*.jar")
                exclude("*-dev-shadow.jar", "*-sources.jar", "*-javadoc.jar")
            }
        }
    }

    tasks.named("build") {
        dependsOn(moduleBuildTasks)
        finalizedBy(copyJarsToObject)
    }
}

tasks.register("runClient") {
    group = "application"
    description = "Runs the primary NeoForge 1.21.10 development client."
    dependsOn(":neoforge-1.21.10:runClient")
}
