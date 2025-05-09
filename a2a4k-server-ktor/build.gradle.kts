// SPDX-FileCopyrightText: 2025
//
// SPDX-License-Identifier: Apache-2.0

kotlin {
    jvm()
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":a2a4k-server"))
                api(project(":a2a4k-models"))
                implementation(libs.bundles.kotlinx)
                implementation(libs.ktor.server.sse)
                implementation(libs.slf4j.api)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(libs.ktor.server.core.jvm)
                implementation(libs.ktor.server.netty.jvm)
            }
        }

        val jvmTest by getting {
            dependencies {

                // Test dependencies
                implementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
                implementation("org.junit.jupiter:junit-jupiter-engine:5.10.2")
                implementation("org.jetbrains.kotlin:kotlin-test:2.1.20")
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.cio.jvm)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.ktor.client.content.negotiation)
                implementation("io.mockk:mockk:1.13.10")
            }
        }
    }
}
