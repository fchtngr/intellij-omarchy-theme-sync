import groovy.json.JsonSlurper
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.3.20"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

val manifestVersion = (JsonSlurper().parse(file("manifest.json")) as Map<*, *>)["version"].toString()

group = providers.gradleProperty("pluginGroup").get()
version = manifestVersion

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        val version = providers.gradleProperty("platformVersion")
        intellijIdea(version)
    }
}

kotlin {
    jvmToolchain(17)
}

intellijPlatform {
    buildSearchableOptions = false
    pluginConfiguration {
        version.set(manifestVersion)
        ideaVersion {
            sinceBuild.set("261")
            untilBuild.set(provider { null })
        }
    }
    pluginVerification {
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
