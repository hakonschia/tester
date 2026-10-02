plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
}

group = "com.hakonschia.tester"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {

}

kotlin {
    jvmToolchain(26)
}

tasks.test {
    useJUnitPlatform()
}