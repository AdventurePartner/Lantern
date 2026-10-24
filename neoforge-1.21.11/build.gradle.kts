import org.gradle.api.tasks.SourceSetContainer

plugins {
    kotlin("jvm")
    id("net.neoforged.moddev")
    id("maven-publish")
}

val minecraftVersion = "1.21.11"
val neoForgeVersion = rootProject.property("neoforge_12111_version").toString()
val geckoLibVersion = rootProject.property("neoforge_12111_geckolib_version").toString()
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
        create("neoforge12111Client") {
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
            "org/lantern/action/**",
            "org/lantern/animation/**",
            "org/lantern/camera/**",
            "org/lantern/feature/**",
            "org/lantern/internal/atlas/**",
            "org/lantern/internal/listen/**",
            "org/lantern/internal/network/PacketNetwork.kt",
            "org/lantern/internal/network/packet/**",
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
            "org/lantern/costume/attach/**",
            "org/lantern/costume/bone/PlayerBoneSnapshot.kt",
            "org/lantern/costume/entity/**",
            "org/lantern/costume/geo/**",
            "org/lantern/costume/handler/**",
            "org/lantern/costume/renderer/**",
            "org/lantern/uix/canvas/impl/GuiCanvas.kt",
            "org/lantern/uix/hud/VanillaHudPoses.kt",
            "org/lantern/uix/renderer/ScissorClip.kt",
            "org/lantern/uix/renderer/TooltipRenderer.kt",
            "org/lantern/uix/renderer/impl/ImageRenderer.kt",
            "org/lantern/uix/renderer/impl/PanelRenderer.kt",
            "org/lantern/worldimage/WorldImageRenderer.kt"
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

    // 共享代码仍写 ResourceLocation / net.minecraft.Util，本模块在 net/minecraft 下用 typealias
    // 映射到 1.21.11 的新名字；Kotlin 会为这些文件生成空门面类，留在 minecraft 的包里就是拆分包。
    // Lantern 自己没有 net 包下的类，整个目录删掉
    named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileKotlin") {
        doLast {
            destinationDirectory.get().asFile.resolve("net").deleteRecursively()
        }
    }

    // clean 会删掉 build/moddev 下 IDEA devlaunch 配置引用的 args 文件，
    // build 收尾时立即重建，保证 clean build 之后 IDEA 的运行配置仍可用。
    named("build") { finalizedBy("prepareNeoforge12111ClientRun") }

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
