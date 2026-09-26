plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(8)
}

dependencies {
    // 各 MC 平台与 Bukkit 运行时均自带 gson，这里只要编译期类型
    compileOnly("com.google.code.gson:gson:2.10.1")
    testImplementation("com.google.code.gson:gson:2.10.1")
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
