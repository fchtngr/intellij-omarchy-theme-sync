plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.24"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24"
    id("org.jetbrains.intellij") version "1.17.4"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}

intellij {
    version.set(providers.gradleProperty("platformVersion"))
    type.set(providers.gradleProperty("platformType"))
    plugins.set(
        providers.gradleProperty("platformPlugins")
            .map { value -> value.split(',').map(String::trim).filter(String::isNotEmpty) }
    )
}

kotlin {
    jvmToolchain(17)
}

tasks {
    patchPluginXml {
        version.set(providers.gradleProperty("pluginVersion"))
        sinceBuild.set("241")
        untilBuild.set("")
    }

    buildSearchableOptions {
        enabled = false
    }
}
