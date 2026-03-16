plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.plugin.spring)
    alias(libs.plugins.spring)
    alias(libs.plugins.spring.management)
}

group = "tech.lq0"
version = "0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation(libs.spring.boot.starter)
    implementation(libs.kotlin.reflect)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.9.0")
    implementation(libs.simbot.spring)
    implementation(libs.simbot.component.onebot)
    implementation("com.belerweb:pinyin4j:2.5.1")

    implementation("com.openai:openai-java:4.28.0")
    implementation("org.jetbrains.kotlinx:kotlinx-schema-generator-json:0.4.2")

    runtimeOnly(libs.ktor.client.java)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

