import org.gradle.api.tasks.SourceSetContainer

plugins {
    kotlin("jvm")
    id("net.neoforged.moddev")
    id("maven-publish")
}

val minecraftVersion = rootProject.property("neoforge_minecraft_version").toString()
val neoForgeVersion = rootProject.property("neoforge_version").toString()
val geckoLibVersion = rootProject.property("neoforge_geckolib_version").toString()
val commonCoreSourceSet = project(":common-core")
    .extensions
    .getByType<SourceSetContainer>()["main"]
val commonCoreOutput = commonCoreSourceSet.output

base {
    archivesName.set("${rootProject.property("archives_name")}-${project.version}-$minecraftVersion-neoforge")
}

kotlin {
    jvmToolchain(21)
}

neoForge {
    version = neoForgeVersion

    runs {
        create("client") {
            client()
            gameDirectory = file("run")
            systemProperty("neoforge.enabledGameTestNamespaces", "lantern")
        }
    }

    mods {
        create("lantern") {
            sourceSet(sourceSets.main.get())
            sourceSet(commonCoreSourceSet)
        }
    }
}

sourceSets {
    main {
        java.setSrcDirs(
            listOf(
                rootProject.file("src/main/kotlin"),
                project.file("src/main/java"),
                project.file("src/main/kotlin")
            )
        )
        java.exclude(
            "org/lantern/LanternFabric.kt",
            "org/lantern/internal/atlas/**",
            "org/lantern/internal/listen/**",
            "org/lantern/internal/network/PacketNetwork.kt",
            "org/lantern/internal/network/packet/**",
            "org/lantern/internal/chat/ChatChannelTabsRenderer.kt",
            "org/lantern/internal/handler/AnimatedGifTexture.kt",
            "org/lantern/internal/handler/TextureHandler.kt",
            "org/lantern/internal/handler/ResourceHandler.kt",
            "org/lantern/item/**",
            "org/lantern/ui/**",
            "org/lantern/model/block/**",
            "org/lantern/model/entity/**",
            "org/lantern/model/geo/**",
            "org/lantern/model/handler/**",
            "org/lantern/model/renderer/**",
            "org/lantern/model/util/**",
            "org/lantern/costume/bone/PlayerBoneSnapshot.kt",
            "org/lantern/costume/entity/**",
            "org/lantern/costume/geo/**",
            "org/lantern/costume/handler/**",
            "org/lantern/costume/renderer/**",
            "org/lantern/uix/canvas/impl/GuiCanvas.kt",
            "org/lantern/uix/renderer/TooltipRenderer.kt",
            "org/lantern/uix/renderer/impl/ImageRenderer.kt",
            "org/lantern/uix/renderer/impl/PanelRenderer.kt"
        )
        resources.setSrcDirs(listOf(project.file("src/main/resources")))
    }
}

dependencies {
    implementation(project(":common-core"))
    implementation(kotlin("stdlib"))
    implementation("net.lingala.zip4j:zip4j:2.11.5")
    implementation("software.bernie.geckolib:geckolib-neoforge-$minecraftVersion:$geckoLibVersion")

    jarJar(implementation(kotlin("stdlib"))!!)
    jarJar(implementation("net.lingala.zip4j:zip4j:2.11.5")!!)
}

tasks {
    jar {
        archiveVersion.set("")
        from(commonCoreOutput)
    }

    processResources {
        inputs.property("version", project.version)
        inputs.property("minecraft_version", minecraftVersion)
        inputs.property("neoforge_version", neoForgeVersion)
        inputs.property("geckolib_version", geckoLibVersion)

        from(rootProject.file("src/main/resources/assets")) {
            into("assets")
        }
        filesMatching("META-INF/neoforge.mods.toml") {
            expand(
                "version" to project.version,
                "minecraft_version" to minecraftVersion,
                "neoforge_version" to neoForgeVersion,
                "geckolib_version" to geckoLibVersion
            )
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
