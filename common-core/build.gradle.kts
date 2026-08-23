plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(8)
}

dependencies {
    testImplementation(kotlin("test-junit5"))
}

tasks.test {
    useJUnitPlatform()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(8)
    }
}
