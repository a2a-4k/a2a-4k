// SPDX-FileCopyrightText: 2025
//
// SPDX-License-Identifier: Apache-2.0

kotlin {
    jvm()
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":a2a4k-models"))
                implementation(project(":a2a4k-client"))
                implementation(project(":a2a4k-server-ktor"))
                implementation(project(":a2a4k-server-arc"))
                implementation(libs.ktor.client.core)
                implementation(libs.bundles.kotlinx)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation("dev.langchain4j:langchain4j-open-ai:1.0.0-beta3")
                implementation("org.eclipse.lmos:arc-agents:0.137.0")
                implementation("org.eclipse.lmos:arc-azure-client:0.137.0")
                implementation(libs.slf4j.jdk14)
            }
        }
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    maven(url = "https://oss.sonatype.org/content/repositories/snapshots/")
}
