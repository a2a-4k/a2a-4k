// SPDX-FileCopyrightText: 2025
//
// SPDX-License-Identifier: Apache-2.0
package io.github.a2a_4k.notifications

import io.github.a2a_4k.createLogger
import io.github.a2a_4k.models.PushNotificationConfig
import io.github.a2a_4k.models.Task
import io.github.a2a_4k.models.a2aJson
import io.github.a2a_4k.models.toJson
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*

/**
 * BasicNotificationPublisher implements the NotificationPublisher interface
 * using HttpURLConnection to send notifications to the specified URL.
 */
class BasicNotificationPublisher(httpClient: HttpClient? = null) : NotificationPublisher {

    /** Logger for this class */
    private val log = createLogger(this::class)

    /**
     * The HTTP client used for making requests to the server.
     */
    private val client: HttpClient = httpClient ?: HttpClient {
        defaultRequest {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }
        install(ContentNegotiation) {
            json(a2aJson)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
            connectTimeoutMillis = 15000
            socketTimeoutMillis = 60000
        }
    }

    /**
     * Publishes a notification about a task to the specified URL.
     *
     * @param task The task to publish a notification about.
     * @param config The configuration for the push notification, including URL and authentication.
     */
    override suspend fun publish(task: Task, config: PushNotificationConfig) {
        log.info("Publishing notification for task ${task.id} to ${config.url}")

        val response = client.post(config.url) {
            config.token?.let {
                header("Authorization", "Bearer $it")
            }
            setBody(task.toJson())
        }

        if (response.status != HttpStatusCode.OK) {
            log.warn("Failed to publish notification for task ${task.id}: ${response.status}")
        }
    }
}
