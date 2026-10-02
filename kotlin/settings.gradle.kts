pluginManagement {
    plugins {
        kotlin("plugin.spring") version "2.4.20"
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "kotlin"
include("runner")
include("common")
include("backend")