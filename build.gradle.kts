plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.20"
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = "com.darshan"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        val configuredPath = providers.gradleProperty("localIdePath").orNull
        val defaultLocal = file("C:/Program Files/JetBrains/PyCharm 2026.2.3")
        val discoveredLocal = sequenceOf(
            configuredPath?.let(::file),
            defaultLocal,
            file("C:/Program Files/JetBrains").listFiles()?.filter { it.isDirectory && it.name.startsWith("PyCharm") }?.maxByOrNull { it.name },
            System.getenv("LOCALAPPDATA")?.let { file("$it/Programs") }?.listFiles()?.filter { it.isDirectory && it.name.startsWith("PyCharm") }?.maxByOrNull { it.name }
        ).filterNotNull().firstOrNull { it.exists() }

        if (discoveredLocal != null) {
            local(discoveredLocal)
        } else {
            pycharmCommunity("2024.2.4")
        }
    }
}

intellijPlatform {
    instrumentCode = false
    buildSearchableOptions = false
    pluginConfiguration {
        id = "com.darshan.notes"
        name = "SideCode Notes - Sidebar Markdown Notes & Checklists"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "242"
            untilBuild = provider { null }
        }
    }
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "21"
    targetCompatibility = "21"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
