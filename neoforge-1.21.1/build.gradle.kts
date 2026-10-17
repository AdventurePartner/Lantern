import org.gradle.api.tasks.SourceSetContainer

plugins {
    kotlin("jvm")
    id("net.neoforged.moddev")
    id("maven-publish")
}

val minecraftVersion = "1.21.1"
val neoForgeVersion = rootProject.property("neoforge_1211_version").toString()
val geckoLibVersion = rootProject.property("neoforge_1211_geckolib_version").toString()
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
        // 不能叫 client：任务名会是 runClient，根目录裸 ./gradlew runClient 会连带启动本模块
        create("neoforge1211Client") {
            client()
            gameDirectory = file("run")
        }
    }

    mods {
        create("lantern") {
            sourceSet(sourceSets.main.get())
            sourceSet(commonCoreSourceSet)
        }
    }
}

// 与 Fabric 1.21.1 同一版 MC：共享 src/main 的 java（Mixin）与 kotlin 全部参与编译，
// 只排除 Fabric API 专属文件和被 NeoForge GUI 图层改写过的 HUD 注入
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
            // NeoForge 把 Gui 拆成按 ID 注册的图层（血量/护甲/饥饿/氧气各自一层），
            // HUD 挂载、隐藏与摆放改走 RegisterGuiLayersEvent / RenderGuiLayerEvent
            "org/lantern/internal/mixin/hud/GuiHudRenderMixin.java",
            "org/lantern/internal/mixin/hud/GuiVanillaHudMixin.java"
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
    // FML 4 不把类路径上的普通库放进游戏模块层，dev 运行时要显式加上（发布 jar 走 jarJar）
    "additionalRuntimeClasspath"(kotlin("stdlib"))
    "additionalRuntimeClasspath"("net.lingala.zip4j:zip4j:2.11.5")
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

        from(rootProject.file("src/main/resources")) {
            exclude("fabric.mod.json", "lantern.mixins.json", "pack.mcmeta")
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
