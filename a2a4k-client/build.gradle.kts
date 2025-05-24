// SPDX-FileCopyrightText: 2025
//
// SPDX-License-Identifier: Apache-2.0

kotlin {
    jvm()

    wasmJs().nodejs()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":a2a4k-models"))
                implementation(libs.bundles.kotlinx)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.ktor.client.content.negotiation)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(libs.ktor.client.cio)
            }
        }

        val wasmJsMain by getting {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }


        val jvmTest by getting {
            dependencies {
                implementation(project(":a2a4k-server-ktor"))
                implementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
                implementation("org.junit.jupiter:junit-jupiter-engine:5.10.2")
                implementation("org.jetbrains.kotlin:kotlin-test:2.1.10")
            }
        }
    }
}
