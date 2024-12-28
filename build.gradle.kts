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
    implementation(libs.simbot.spring)
    implementation(libs.simbot.component.onebot)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    runtimeOnly(libs.ktor.client.java)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }

}

tasks.withType<Test> {
    useJUnitPlatform()
}

