import org.gradle.api.tasks.SourceSetContainer
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm")
    id("dev.architectury.loom")
    id("com.github.johnrengelman.shadow")
    id("maven-publish")
}

val minecraftVersion = "1.20.1"
val forgeVersion = rootProject.property("forge_1201_version").toString()
val geckolibDependency =
    "software.bernie.geckolib:geckolib-forge-$minecraftVersion:${rootProject.property("geckolib_1201_version")}"

val commonCoreOutput = project(":common-core")
    .extensions
    .getByType<SourceSetContainer>()["main"]
    .output

base {
    archivesName.set("${rootProject.property("archives_name")}-${project.version}-${minecraftVersion}-forge")
}

kotlin {
    jvmToolchain(17)
}

loom {
    silentMojangMappingsLicense()
    forge {
        mixinConfig("lantern-forge-1201.mixins.json")
    }
}

val shade by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
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
            "org/lantern/internal/mixin/item/ItemRendererMixin.java",
            "org/lantern/internal/mixin/costume/ItemInHandRendererMixin.java",
            "org/lantern/internal/mixin/block/BlockItemMixin.java",
            "org/lantern/internal/mixin/block/BarrelBlockMixin.java",
            "org/lantern/internal/mixin/hud/GuiHudRenderMixin.java",
            "org/lantern/internal/mixin/model/ModelBakeryMixin.java",
            "org/lantern/internal/mixin/font/StringRenderOutputMixin.java",
            "org/lantern/internal/mixin/renderer/EntityRendererMixin.java",
            "org/lantern/internal/mixin/resource/FallbackResourceManagerMixin.java",
            "org/lantern/internal/mixin/accessor/ChatComponentAccessor.java",
            "org/lantern/internal/mixin/chat/ChatComponentMixin.java",
            "org/lantern/internal/pack/LanternVirtualPackResources.java",
            "org/lantern/costume/entity/CostumeAnimatable.kt",
            "org/lantern/model/block/LanternBlockEntity.kt",
            "org/lantern/model/entity/GenericReplacedEntity.kt",
            "org/lantern/model/renderer/GenericGeoRenderer.kt",
            "org/lantern/model/block/LanternBlockRenderer.kt",
            "org/lantern/costume/renderer/CostumeRenderer.kt",
            "org/lantern/costume/renderer/CostumeItemRenderer.kt",
            "org/lantern/uix/canvas/impl/GuiCanvas.kt",
            "org/lantern/ui/mixed/impl/PlayerInventoryMixed.kt"
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

    modImplementation(geckolibDependency)
    shade(geckolibDependency)
    shade("com.eliotlash.mclib:mclib:20")
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

    named<ShadowJar>("shadowJar") {
        configurations = listOf(shade)
        archiveClassifier.set("dev-shadow")
        relocate("software.bernie.geckolib", "org.lantern.shadow.geckolib")
        relocate("com.eliotlash.mclib", "org.lantern.shadow.mclib")
        exclude("geckolib.mixins.json")
        exclude("geckolib.refmap.json")
        exclude("software/bernie/geckolib/mixin/**")
        exclude("software/bernie/example/**")
        exclude("assets/geckolib/**")
        exclude("META-INF/jarjar/**")
        // common-core 不在 shade 配置中，需显式注入到 shadowJar，否则 remapJar 产物会缺少其 class。
        from(commonCoreOutput)
    }

    named("remapJar") {
        dependsOn(named("shadowJar"))
        val shadowJar = named<ShadowJar>("shadowJar")
        this as net.fabricmc.loom.task.RemapJarTask
        inputFile.set(shadowJar.flatMap { it.archiveFile })
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
        languageVersion = JavaLanguageVersion.of(17)
    }
}
