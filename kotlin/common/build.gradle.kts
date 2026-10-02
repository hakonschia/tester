plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
}

group = "com.hakonschia.tester.common"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0-RC")
    api("com.malinskiy.adam:adam:0.5.10")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(26)
}