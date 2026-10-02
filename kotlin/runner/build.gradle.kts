plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.hakonschia.tester.runner"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":common"))
    implementation(libs.clickt)
    implementation(libs.okhttp)
}

tasks.test {
    useJUnitPlatform()
}