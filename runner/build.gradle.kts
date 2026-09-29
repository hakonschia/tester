plugins {
    kotlin("jvm") version "2.4.20"
}

group = "com.hakonschia.tester"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.malinskiy.adam:adam:0.5.10")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("com.github.ajalt.clikt:clikt:5.1.0")

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(26)
}

tasks.test {
    useJUnitPlatform()
}