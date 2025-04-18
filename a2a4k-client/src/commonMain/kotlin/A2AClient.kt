// SPDX-FileCopyrightText: 2025 Deutsche Telekom AG and others
//
// SPDX-License-Identifier: Apache-2.0
package org.a2a4k

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import org.a2a4k.models.*
import java.util.UUID

/**
 * A2AClient implements an Agent-to-Agent communication client based on the A2A protocol.
 *
 * This client provides methods for interacting with an A2A server, including retrieving
 * the agent's metadata (agent card), sending and retrieving tasks, canceling tasks,
 * and managing push notification configurations.
 */
class A2AClient(
    baseUrl: String,
    endpoint: String = "/",
    httpClient: HttpClient? = null,
) : Closeable {

    /**
     * The HTTP client used for making requests to the server.
     */
    private val client: HttpClient = httpClient ?: HttpClient {
        defaultRequest {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }
        install(SSE)
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
            connectTimeoutMillis = 15000
            socketTimeoutMillis = 60000
        }
    }

    /**
     * The full URL for the API endpoint.
     */
    private val apiUrl = "$baseUrl$endpoint"

    /**
     * The URL for retrieving the agent card.
     */
    private val agentCardUrl = "$baseUrl/.well-known/agent.json"

    /**
     * Retrieves the agent card from the server.
     *
     * @return The agent card containing metadata about the agent.
     * @throws Exception if the request fails or the response cannot be parsed.
     */
    suspend fun getAgentCard(): AgentCard {
        val response = client.get(agentCardUrl)
        if (response.status != HttpStatusCode.OK) {
            throw ServerException("Failed to get agent card: ${response.status}!")
        }
        return a2aJson.decodeFromString(response.bodyAsText())
    }

    /**
     * Retrieves a task by its ID.
     *
     * @param taskId The ID of the task to retrieve.
     * @param historyLength The maximum number of history entries to include in the response.
     * @param requestId A unique identifier for this request.
     * @return The response containing the requested task or an error if the task is not found.
     * @throws Exception if the request fails or the response cannot be parsed.
     */
    suspend fun getTask(
        taskId: String,
        historyLength: Int = 10,
        requestId: String = generateRequestId(),
    ): GetTaskResponse {
        val request = GetTaskRequest(
            id = requestId,
            params = TaskQueryParams(id = taskId, historyLength = historyLength),
        )

        val response = client.post(apiUrl) {
            setBody(request.toJson())
        }

        if (response.status != HttpStatusCode.OK) {
            throw ServerException("Failed to get task: ${response.status}")
        }

        return a2aJson.decodeFromString(response.bodyAsText())
    }

    /**
     * Creates or updates a task.
     *
     * @param taskId The ID of the task to create or update.
     * @param sessionId The session ID for this task.
     * @param message The message to include in the task.
     * @param historyLength The maximum number of history entries to include in the response.
     * @param requestId A unique identifier for this request.
     * @return The response containing the created or updated task.
     * @throws Exception if the request fails or the response cannot be parsed.
     */
    suspend fun sendTask(
        message: Message,
        taskId: String = "task::${UUID.randomUUID()}",
        sessionId: String = "session::${UUID.randomUUID()}",
        historyLength: Int = 10,
        requestId: String = generateRequestId(),
    ): SendTaskResponse {
        val request = SendTaskRequest(
            id = requestId,
            params = TaskSendParams(
                id = taskId,
                sessionId = sessionId,
                message = message,
                historyLength = historyLength,
            ),
        )

        val response = client.post(apiUrl) {
            setBody(request.toJson())
        }

        if (response.status != HttpStatusCode.OK) {
            throw ServerException("Failed to send task: ${response.status}")
        }

        return a2aJson.decodeFromString(response.bodyAsText())
    }

    /**
     * Subscribes to streaming updates for a task.
     *
     * @param taskId The ID of the task to subscribe to.
     * @param sessionId The session ID for this task.
     * @param message The message to include in the task.
     * @param historyLength The maximum number of history entries to include in the response.
     * @param requestId A unique identifier for this request.
     * @return A flow of streaming responses with task updates.
     * @throws Exception if the request fails or the response cannot be parsed.
     */
    fun sendTaskStreaming(
        taskId: String,
        sessionId: String,
        message: Message,
        historyLength: Int = 10,
        requestId: String = generateRequestId(),
    ): Flow<SendTaskStreamingResponse> = flow {
        val request = SendTaskStreamingRequest(
            id = requestId,
            params = TaskSendParams(
                id = taskId,
                sessionId = sessionId,
                message = message,
                historyLength = historyLength,
            ),
        )
        client.sse(request = {
            url(apiUrl)
            setBody(request.toJson())
        }) {
            incoming.collect { serverSentEvent ->
                val responseBody = serverSentEvent.data ?: return@collect
                val response = a2aJson.decodeFromString<SendTaskStreamingResponse>(responseBody)
                emit(response)
            }
        }
    }

    /**
     * Attempts to cancel a task.
     *
     * @param taskId The ID of the task to cancel.
     * @param requestId A unique identifier for this request.
     * @return The response indicating success or failure of the cancellation.
     * @throws Exception if the request fails or the response cannot be parsed.
     */
    suspend fun cancelTask(taskId: String, requestId: String = generateRequestId()): CancelTaskResponse {
        val request = CancelTaskRequest(
            id = requestId,
            params = TaskIdParams(id = taskId),
        )

        val response = client.post(apiUrl) {
            setBody(request.toJson())
        }

        if (response.status != HttpStatusCode.OK) {
            throw Exception("Failed to cancel task: ${response.status}")
        }

        return a2aJson.decodeFromString(response.bodyAsText())
    }

    /**
     * Sets push notification configuration for a task.
     *
     * @param taskId The ID of the task to configure.
     * @param config The push notification configuration.
     * @param requestId A unique identifier for this request.
     * @return The response indicating success or failure of the operation.
     * @throws Exception if the request fails or the response cannot be parsed.
     */
    suspend fun setTaskPushNotification(
        taskId: String,
        config: PushNotificationConfig,
        requestId: String = generateRequestId(),
    ): SetTaskPushNotificationResponse {
        val request = SetTaskPushNotificationRequest(
            id = requestId,
            params = TaskPushNotificationConfig(
                id = taskId,
                pushNotificationConfig = config,
            ),
        )

        val response = client.post(apiUrl) {
            setBody(request.toJson())
        }

        if (response.status != HttpStatusCode.OK) {
            throw Exception("Failed to set task push notification: ${response.status}")
        }

        return a2aJson.decodeFromString(response.bodyAsText())
    }

    /**
     * Retrieves the push notification configuration for a task.
     *
     * @param taskId The ID of the task.
     * @param requestId A unique identifier for this request.
     * @return The response containing the push notification configuration or an error if not found.
     * @throws Exception if the request fails or the response cannot be parsed.
     */
    suspend fun getTaskPushNotification(
        taskId: String,
        requestId: String = generateRequestId(),
    ): GetTaskPushNotificationResponse {
        val request = GetTaskPushNotificationRequest(
            id = requestId,
            params = TaskIdParams(id = taskId),
        )

        val response = client.post(apiUrl) {
            setBody(request.toJson())
        }

        if (response.status != HttpStatusCode.OK) {
            throw Exception("Failed to get task push notification: ${response.status}")
        }

        return a2aJson.decodeFromString(response.bodyAsText())
    }

    /**
     * Resubscribes to a task to receive streaming updates.
     *
     * @param taskId The ID of the task to resubscribe to.
     * @param requestId A unique identifier for this request.
     * @return A flow of streaming responses with task updates.
     * @throws Exception if the request fails or the response cannot be parsed.
     */
    fun resubscribeToTask(
        taskId: String,
        requestId: String = generateRequestId(),
    ): Flow<SendTaskStreamingResponse> = flow {
        val request = TaskResubscriptionRequest(id = requestId, params = TaskQueryParams(id = taskId))

        client.sse(request = {
            url(apiUrl)
            setBody(request.toJson())
        }) {
            incoming.collect { serverSentEvent ->
                val responseBody = serverSentEvent.data ?: return@collect
                val response = a2aJson.decodeFromString<SendTaskStreamingResponse>(responseBody)
                emit(response)
            }
        }
    }

    /**
     * Closes the HTTP client and releases resources.
     */
    override fun close() {
        client.close()
    }

    /**
     * Generates a unique request ID.
     *
     * @return A unique string ID.
     */
    private fun generateRequestId(): String {
        return "${UUID.randomUUID()}"
    }
}
