plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.spring)
}

group = "com.hakonschia.tester.backend"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":common"))
    implementation(libs.spring.webmvc)
    implementation(libs.spring.websocket)
}

tasks.test {
    useJUnitPlatform()
}