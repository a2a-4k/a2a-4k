// SPDX-FileCopyrightText: 2025
//
// SPDX-License-Identifier: Apache-2.0

kotlin {
    jvm()
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":a2a4k-server"))
                implementation(libs.bundles.kotlinx)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(libs.redis.lettuce)
                implementation(libs.kotlinx.serialization.json)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
                implementation("org.junit.jupiter:junit-jupiter-engine:5.10.2")
                implementation("org.jetbrains.kotlin:kotlin-test:2.2.0")
                implementation("io.mockk:mockk:1.13.10")
                implementation("com.redis:testcontainers-redis:2.2.4")
                implementation("org.testcontainers:junit-jupiter:1.20.4")
            }
        }
    }
}
