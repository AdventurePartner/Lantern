import org.gradle.api.tasks.SourceSetContainer

plugins {
    kotlin("jvm")
    id("dev.architectury.loom")
    id("com.github.johnrengelman.shadow")
    id("maven-publish")
}

val minecraftVersion = "1.21.1"
val forgeVersion = rootProject.property("forge_1211_version").toString()

val commonCoreOutput = project(":common-core")
    .extensions
    .getByType<SourceSetContainer>()["main"]
    .output

base {
    archivesName.set("${rootProject.property("archives_name")}-${project.version}-${minecraftVersion}-forge")
}

kotlin {
    jvmToolchain(21)
}

loom {
    silentMojangMappingsLicense()
    forge {
        mixinConfig("lantern-common-1211.mixins.json")
        mixinConfig("lantern-forge-1211.mixins.json")
    }
}

sourceSets {
    main {
        java.setSrcDirs(
            listOf(
                rootProject.file("src/main/java"),
                rootProject.file("src/main/kotlin"),
                project.file("src/main/java"),
                project.file("src/main/kotlin")
            )
        )
        java.exclude(
            "org/lantern/LanternFabric.kt",
            "org/lantern/internal/listen/FabricClientListener.kt",
            "org/lantern/internal/network/PacketNetwork.kt",
            "org/lantern/internal/network/packet/**",
            "org/lantern/item/plugin/LanternModelPlugin.kt",
            "org/lantern/item/model/LanternBakedModel.kt",
            "org/lantern/internal/mixin/item/ItemRendererMixin.java"
        )
        resources.setSrcDirs(listOf(project.file("src/main/resources")))
    }
}

dependencies {
    minecraft("net.minecraft:minecraft:$minecraftVersion")
    mappings(loom.officialMojangMappings())
    forge("net.minecraftforge:forge:$minecraftVersion-$forgeVersion")

    implementation(project(":common-core"))
    implementation(kotlin("stdlib"))

    modImplementation("software.bernie.geckolib:geckolib-forge-$minecraftVersion:${rootProject.property("geckolib_version")}")
    implementation("net.lingala.zip4j:zip4j:2.11.5")
    forgeRuntimeLibrary(kotlin("stdlib"))
    forgeRuntimeLibrary("net.lingala.zip4j:zip4j:2.11.5")
    include(kotlin("stdlib"))
    include("net.lingala.zip4j:zip4j:2.11.5")
}

tasks {
    withType<org.gradle.api.tasks.bundling.AbstractArchiveTask>().configureEach {
        archiveVersion.set("")
    }

    named<org.gradle.jvm.tasks.Jar>("jar") {
        from(commonCoreOutput)
    }

    processResources {
        inputs.property("version", project.version)
        inputs.property("minecraft_version", minecraftVersion)
        from(rootProject.file("src/main/resources")) {
            exclude("fabric.mod.json", "lantern.mixins.json", "pack.mcmeta")
        }
        filesMatching("META-INF/mods.toml") {
            expand(
                "version" to project.version,
                "minecraft_version" to minecraftVersion,
                "forge_version" to forgeVersion
            )
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
