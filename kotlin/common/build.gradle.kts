plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.hakonschia.tester.common"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api(libs.kotlinx.coroutines)
    api(libs.kotlinx.serialization)
    api(libs.adam)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(26)
}