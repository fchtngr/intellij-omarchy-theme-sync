import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.20"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.20"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation(kotlin("stdlib"))
    intellijPlatform {
        val type = providers.gradleProperty("platformType")
        val version = providers.gradleProperty("platformVersion")
        create(type, version)
        bundledPlugins(
            providers.gradleProperty("platformPlugins")
                .map { value -> value.split(',').map(String::trim).filter(String::isNotEmpty) }
        )
    }
}

kotlin {
    jvmToolchain(17)
}

intellijPlatform {
    buildSearchableOptions = false
    pluginConfiguration {
        version.set(providers.gradleProperty("pluginVersion"))
        ideaVersion {
            sinceBuild.set("241")
            untilBuild.set(provider { null })
        }
    }
    pluginVerification {
        freeArgs.add("-mute")
        freeArgs.add("TemplateWordInPluginId")
        failureLevel = listOf(
            FailureLevel.COMPATIBILITY_PROBLEMS,
            FailureLevel.OVERRIDE_ONLY_API_USAGES,
        )
        ides {
            current()
            latest()
        }
    }
}
