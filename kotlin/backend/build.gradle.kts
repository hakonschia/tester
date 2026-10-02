plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    kotlin("plugin.spring")
}

group = "com.hakonschia.tester.backend"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":common"))
    implementation("org.springframework.boot:spring-boot-starter-websocket:4.1.1")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc:4.1.1")
    implementation("org.springframework.boot:spring-boot-h2console:4.1.1")
    implementation("org.springframework.boot:spring-boot-starter-webmvc:4.1.1")
}

tasks.test {
    useJUnitPlatform()
}