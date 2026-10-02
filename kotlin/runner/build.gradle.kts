plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.hakonschia.tester.runner"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":common"))
    implementation("com.github.ajalt.clikt:clikt:5.0.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
}

tasks.test {
    useJUnitPlatform()
}