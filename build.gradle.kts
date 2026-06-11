import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.Sync

plugins {
    kotlin("jvm") version "1.9.21"
    id("fabric-loom") version "1.7-SNAPSHOT"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("maven-publish")
}

group = "${property("maven_group")}"
version = "${property("mod_version")}"

val minecraftVersion = findProperty("minecraft_version") as String
val projectArchivesName = property("archives_name") as String

kotlin {
    jvmToolchain(21)
}

base {
    archivesName = projectArchivesName
}

sourceSets {
    main {
        java {
            setSrcDirs(listOf("src/main/java", "src/main/kotlin"))
        }
    }
}

repositories {
    mavenCentral()
    maven {
        name = "GeckoLib"
        url = uri("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/")
        content {
            includeGroupByRegex("software\\.bernie.*")
            includeGroup("com.eliotlash.mclib")
        }
    }
}

dependencies {
    minecraft("net.minecraft:minecraft:${property("minecraft_version")}")
    mappings(loom.officialMojangMappings())

    // Fabric
    modImplementation("net.fabricmc:fabric-loader:${property("fabric_loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:1.12.3+kotlin.2.0.21")

    // GeckoLib for Fabric
    modImplementation("software.bernie.geckolib:geckolib-fabric-$minecraftVersion:${property("geckolib_version")}")

    // zip4j for encrypted ZIP resource packs
    implementation("net.lingala.zip4j:zip4j:2.11.5")
    include("net.lingala.zip4j:zip4j:2.11.5")

    testImplementation(kotlin("test-junit5"))
}

tasks {
    shadowJar {
        archiveClassifier = "dev-shadow"
        dependencies {
            exclude { true }
        }
    }

    remapJar {
        inputFile.set(shadowJar.get().archiveFile)
        archiveFileName.set("$projectArchivesName-$version-$minecraftVersion-fabric.jar")
    }

    processResources {
        inputs.property("version", project.version)
        filesMatching("fabric.mod.json") {
            expand(
                "version" to rootProject.version,
                "minecraft_version" to minecraftVersion
            )
        }
    }

    test {
        useJUnitPlatform()
    }
}

val packageArtifactTaskNames = listOf("remapJar", "shadowJar", "jar")
val standalonePlatformModuleDirs = listOf("forge-1.21.1", "forge-1.20.1")

val copyJarsToObject by tasks.registering(Sync::class) {
    group = "build"
    description = "Syncs packaged module jars into object/."
    into(layout.projectDirectory.dir("object"))
    duplicatesStrategy = DuplicatesStrategy.FAIL
}

gradle.projectsEvaluated {
    val artifactTasks = allprojects.mapNotNull { project ->
        packageArtifactTaskNames
            .asSequence()
            .mapNotNull { taskName -> project.tasks.findByName(taskName) }
            .firstOrNull { task -> task.enabled }
    }
    val includedProjectDirs = allprojects.map { project -> project.projectDir.canonicalFile }.toSet()
    val standaloneJarDirs = standalonePlatformModuleDirs
        .map { moduleDir -> layout.projectDirectory.dir(moduleDir).asFile }
        .filter { moduleDir -> moduleDir.exists() && moduleDir.canonicalFile !in includedProjectDirs }
        .map { moduleDir -> moduleDir.resolve("build/libs") }

    copyJarsToObject.configure {
        dependsOn(artifactTasks)
        mustRunAfter(allprojects.mapNotNull { project -> project.tasks.findByName("build") })
        artifactTasks.forEach { artifactTask ->
            from(artifactTask.outputs.files) {
                include("*.jar")
            }
        }
        standaloneJarDirs.forEach { jarDir ->
            from(jarDir) {
                include("*.jar")
                exclude("*-dev-shadow.jar")
            }
        }
    }
}

tasks.named("build") {
    finalizedBy(copyJarsToObject)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}