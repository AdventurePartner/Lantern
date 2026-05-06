plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":common-core"))
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

