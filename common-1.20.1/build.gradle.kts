plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":common-core"))
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

