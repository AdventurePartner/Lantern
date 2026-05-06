plugins {
    kotlin("jvm")
    id("dev.architectury.loom")
    id("com.github.johnrengelman.shadow")
    id("maven-publish")
}

val minecraftVersion = "1.21.1"

base {
    archivesName.set("${rootProject.property("archives_name")}-${project.version}-${minecraftVersion}-fabric")
}

kotlin {
    jvmToolchain(21)
}

loom {
    silentMojangMappingsLicense()
}

sourceSets {
    main {
        java.setSrcDirs(
            listOf(
                rootProject.file("src/main/java"),
                rootProject.file("src/main/kotlin"),
                project.file("src/main/kotlin")
            )
        )
        resources.setSrcDirs(listOf(rootProject.file("src/main/resources")))
    }
}

dependencies {
    minecraft("net.minecraft:minecraft:$minecraftVersion")
    mappings(loom.officialMojangMappings())

    implementation(project(":common-core"))

    modImplementation("net.fabricmc:fabric-loader:${rootProject.property("fabric_loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${rootProject.property("fabric_api_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:1.12.3+kotlin.2.0.21")
    modImplementation("software.bernie.geckolib:geckolib-fabric-$minecraftVersion:${rootProject.property("geckolib_version")}")

    implementation("net.lingala.zip4j:zip4j:2.11.5")
    include("net.lingala.zip4j:zip4j:2.11.5")
}

tasks {
    withType<org.gradle.api.tasks.bundling.AbstractArchiveTask>().configureEach {
        archiveVersion.set("")
    }

    processResources {
        inputs.property("version", project.version)
        inputs.property("minecraft_version", minecraftVersion)
        filesMatching("fabric.mod.json") {
            expand(
                "version" to project.version,
                "minecraft_version" to minecraftVersion
            )
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
