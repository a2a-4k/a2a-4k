// SPDX-FileCopyrightText: 2025
//
// SPDX-License-Identifier: Apache-2.0

import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.System.getenv
import java.net.URI

group = "io.github.a2a-4k"
version = project.findProperty("version") as String

plugins {
    kotlin("multiplatform") version "2.1.10" apply false
    kotlin("plugin.serialization") version "2.2.0" apply false
    id("org.jetbrains.dokka") version "2.0.0"
    id("org.cyclonedx.bom") version "2.0.0"
    id("org.jlleitschuh.gradle.ktlint") version "12.2.0"
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
    id("net.researchgate.release") version "3.1.0"
    id("com.vanniktech.maven.publish") version "0.31.0"
}

subprojects {
    group = "io.github.a2a-4k"

    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "org.jetbrains.kotlin.multiplatform")
    apply(plugin = "kotlinx-serialization")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "org.jetbrains.kotlinx.kover")
    apply(plugin = "com.vanniktech.maven.publish")

    java {
        sourceCompatibility = JavaVersion.VERSION_21
    }

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        debug.set(true)
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs += "-Xjsr305=strict"
            freeCompilerArgs += "-Xcontext-receivers"
            jvmTarget = "21"
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    val javadocJar: TaskProvider<Jar> by tasks.registering(Jar::class) {
        dependsOn(tasks.dokkaJavadoc)
        from(tasks.dokkaJavadoc.flatMap { it.outputDirectory })
        archiveClassifier.set("javadoc")
    }

    if (project.name != "arc-gradle-plugin") {
        mavenPublishing {
            publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
            signAllPublications()

            pom {
                name = "A2A4K"
                description = "ARC is an AI framework."
                url = "https://github.com/a2a-4k/a2a-4k"
                licenses {
                    license {
                        name = "Apache-2.0"
                        distribution = "repo"
                        url = "https://github.com/a2a-4k/a2a-4k/blob/main/LICENSES/Apache-2.0.txt"
                    }
                }
                developers {
                    developer {
                        id = "pat"
                        name = "Patrick Whelan"
                        email = "opensource@telekom.de"
                    }
                }
                scm {
                    url = "https://github.com/a2a-4k/a2a-4k.git"
                }
            }

            repositories {
                maven {
                    name = "GitHubPackages"
                    url = URI("https://maven.pkg.github.com/a2a-4k/a2a-4k")
                    credentials {
                        username = findProperty("GITHUB_USER")?.toString() ?: getenv("GITHUB_USER")
                        password = findProperty("GITHUB_TOKEN")?.toString() ?: getenv("GITHUB_TOKEN")
                    }
                }
            }
        }
    }

    repositories {
        // mavenLocal() This somehow causes with js compilation
        mavenCentral()
        google()
    }

    tasks.named("dokkaJavadoc") {
        mustRunAfter("checksum")
    }
}

dependencies {
    kover(project("a2a4k-models"))
    kover(project("a2a4k-server"))
    kover(project("a2a4k-server-ktor"))
    kover(project("a2a4k-storage-redis"))
    kover(project("a2a4k-client"))
}

repositories {
    mavenLocal()
    mavenCentral()
}

fun Project.java(configure: Action<JavaPluginExtension>): Unit =
    (this as ExtensionAware).extensions.configure("java", configure)

fun String.execWithCode(workingDir: File? = null): Pair<CommandResult, Sequence<String>> {
    ProcessBuilder().apply {
        workingDir?.let { directory(it) }
        command(split(" "))
        redirectErrorStream(true)
        val process = start()
        val result = process.readStream()
        val code = process.waitFor()
        return CommandResult(code) to result
    }
}

class CommandResult(val code: Int) {

    val isFailed = code != 0
    val isSuccess = !isFailed

    fun ifFailed(block: () -> Unit) {
        if (isFailed) block()
    }
}

/**
 * Executes a string as a command.
 */
fun String.exec(workingDir: File? = null) = execWithCode(workingDir).second

private fun Process.readStream() = sequence<String> {
    val reader = BufferedReader(InputStreamReader(inputStream))
    try {
        var line: String?
        while (true) {
            line = reader.readLine()
            if (line == null) {
                break
            }
            yield(line)
        }
    } finally {
        reader.close()
    }
}

release {
    ignoredSnapshotDependencies = listOf("org.springframework.ai:spring-ai-bom")
    newVersionCommitMessage = "New Snapshot-Version:"
    preTagCommitMessage = "Release:"
}
