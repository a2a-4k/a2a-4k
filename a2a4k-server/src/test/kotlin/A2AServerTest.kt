// SPDX-FileCopyrightText: 2025 Deutsche Telekom AG and others
//
// SPDX-License-Identifier: Apache-2.0
package org.a2a4k

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.a2a4k.models.AgentCard
import org.a2a4k.models.CancelTaskRequest
import org.a2a4k.models.CancelTaskResponse
import org.a2a4k.models.GetTaskPushNotificationRequest
import org.a2a4k.models.GetTaskPushNotificationResponse
import org.a2a4k.models.GetTaskRequest
import org.a2a4k.models.GetTaskResponse
import org.a2a4k.models.JsonRpcRequest
import org.a2a4k.models.Message
import org.a2a4k.models.PushNotificationConfig
import org.a2a4k.models.SendTaskRequest
import org.a2a4k.models.SendTaskResponse
import org.a2a4k.models.SetTaskPushNotificationRequest
import org.a2a4k.models.SetTaskPushNotificationResponse
import org.a2a4k.models.TaskIdParams
import org.a2a4k.models.TaskPushNotificationConfig
import org.a2a4k.models.TaskQueryParams
import org.a2a4k.models.TaskSendParams
import org.a2a4k.models.TaskState
import org.a2a4k.models.TextPart
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class A2AServerTest {
    private val host = "localhost"
    private val port = 8080
    private val endpoint = "/api"
    private val baseUrl = "http://$host:$port"
    private val apiUrl = "$baseUrl$endpoint"
    private val agentCardUrl = "$baseUrl/.well-known/agent.json"

    private lateinit var taskManager: InMemoryTaskManager
    private lateinit var server: A2AServer
    private lateinit var client: HttpClient

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @BeforeEach
    fun setup() {
        taskManager = InMemoryTaskManager(NoopTaskHandler())
        server = A2AServer(
            host = host,
            port = port,
            endpoint = endpoint,
            agentCard = agentCard,
            taskManager = taskManager
        )

        // Start server
        server.start(wait = false)

        // Create HTTP client
        client = HttpClient(CIO) {
            defaultRequest {
                contentType(ContentType.Application.Json)
            }
            install(ContentNegotiation) {
                json(json)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 5000
            }
            install(SSE)
        }

        // Wait for server to start
        Thread.sleep(1000)
    }

    @AfterEach
    fun teardown() {
        client.close()
        server.stop()
    }

    @Test
    fun `test GetAgentCard`(): Unit = runBlocking {
        // When
        val response = client.get(agentCardUrl)

        // Then
        assertEquals(HttpStatusCode.OK, response.status)
        val responseBody = response.bodyAsText()
        val responseAgentCard = json.decodeFromString<AgentCard>(responseBody)

        assertEquals(agentCard.name, responseAgentCard.name)
        assertEquals(agentCard.description, responseAgentCard.description)
        assertEquals(agentCard.version, responseAgentCard.version)
    }

    @Test
     fun `test GetTaskRequest for non-existent task`(): Unit = runBlocking {
        // Given
        val taskId = "non-existent-task"
        val requestId = "req-123"
        val request = GetTaskRequest(
            id = requestId,
            params = TaskQueryParams(id = taskId, historyLength = 10)
        )

        // When
        val response = client.post(apiUrl) {
            setBody(json.encodeToString(JsonRpcRequest.serializer(), request))
        }

        // Then
        assertEquals(HttpStatusCode.OK, response.status)
        val responseBody = response.bodyAsText()
        val getTaskResponse = json.decodeFromString<GetTaskResponse>(responseBody)

        assertEquals(requestId, getTaskResponse.id)
        assertNull(getTaskResponse.result)
        assertNotNull(getTaskResponse.error)
        assertEquals(-32001, getTaskResponse.error?.code)
    }

    @Test
    fun `test SendTaskRequest and GetTaskRequest`(): Unit = runBlocking {
        // Given
        val taskId = "test-task-123"
        val sessionId = "session-123"
        val requestId = "req-456"
        val textPart = TextPart(text = "Hello", metadata = emptyMap())
        val message = Message(role = "user", parts = listOf(textPart), metadata = null)

        // Create task
        val sendRequest = SendTaskRequest(
            id = requestId,
            params = TaskSendParams(
                id = taskId,
                sessionId = sessionId,
                message = message,
                historyLength = 10
            )
        )

        // When - Send task
        val sendResponse = client.post(apiUrl) {
            setBody(json.encodeToString(JsonRpcRequest.serializer(), sendRequest))
        }

        // Then - Send task response
        assertEquals(HttpStatusCode.OK, sendResponse.status)
        val sendResponseBody = sendResponse.bodyAsText()
        val sendTaskResponse = json.decodeFromString<SendTaskResponse>(sendResponseBody)

        assertEquals(requestId, sendTaskResponse.id)
        assertNotNull(sendTaskResponse.result)
        assertNull(sendTaskResponse.error)
        assertEquals(taskId, sendTaskResponse.result?.id)
        assertEquals(sessionId, sendTaskResponse.result?.sessionId)
        assertEquals(TaskState.submitted, sendTaskResponse.result?.status?.state)
        assertEquals(1, sendTaskResponse.result?.history?.size)

        // When - Get task
        val getRequest = GetTaskRequest(
            id = "get-req-123",
            params = TaskQueryParams(id = taskId, historyLength = 10)
        )

        val getResponse = client.post(apiUrl) {
            setBody(json.encodeToString(JsonRpcRequest.serializer(), getRequest))
        }

        // Then - Get task response
        assertEquals(HttpStatusCode.OK, getResponse.status)
        val getResponseBody = getResponse.bodyAsText()
        val getTaskResponse = json.decodeFromString<GetTaskResponse>(getResponseBody)

        assertEquals("get-req-123", getTaskResponse.id)
        assertNotNull(getTaskResponse.result)
        assertNull(getTaskResponse.error)
        assertEquals(taskId, getTaskResponse.result?.id)
        assertEquals(sessionId, getTaskResponse.result?.sessionId)
        assertEquals(TaskState.submitted, getTaskResponse.result?.status?.state)
        assertEquals(1, getTaskResponse.result?.history?.size)
    }

    @Test
    fun `test CancelTaskRequest for non-existent task`(): Unit = runBlocking {
        // Given
        val taskId = "non-existent-task"
        val requestId = "req-cancel"
        val request = CancelTaskRequest(
            id = requestId,
            params = TaskIdParams(id = taskId)
        )

        // When
        val response = client.post(apiUrl) {
            setBody(json.encodeToString(JsonRpcRequest.serializer(), request))
        }

        // Then
        assertEquals(HttpStatusCode.OK, response.status)
        val responseBody = response.bodyAsText()
        val cancelTaskResponse = json.decodeFromString<CancelTaskResponse>(responseBody)

        assertEquals(requestId, cancelTaskResponse.id)
        assertNull(cancelTaskResponse.result)
        assertNotNull(cancelTaskResponse.error)
        assertEquals(-32001, cancelTaskResponse.error?.code)
    }

    @Test
    fun `test SetTaskPushNotification and GetTaskPushNotification`(): Unit = runBlocking {
        // Given
        val taskId = "push-task-123"
        val sessionId = "session-push"
        val requestId = "req-push"
        val textPart = TextPart(text = "Hello", metadata = emptyMap())
        val message = Message(role = "user", parts = listOf(textPart), metadata = null)

        // Create task
        val sendRequest = SendTaskRequest(
            id = "send-req-123",
            params = TaskSendParams(
                id = taskId,
                sessionId = sessionId,
                message = message,
                historyLength = 10
            )
        )

        client.post(apiUrl) {
            setBody(json.encodeToString(JsonRpcRequest.serializer(), sendRequest))
        }

        // Set push notification
        val pushConfig = PushNotificationConfig(url = "https://example.com/push")
        val setPushRequest = SetTaskPushNotificationRequest(
            id = requestId,
            params = TaskPushNotificationConfig(id = taskId, pushNotificationConfig = pushConfig)
        )

        // When - Set push notification
        val setPushResponse = client.post(apiUrl) {
            setBody(json.encodeToString(JsonRpcRequest.serializer(), setPushRequest))
        }

        // Then - Set push notification response
        assertEquals(HttpStatusCode.OK, setPushResponse.status)
        val setPushResponseBody = setPushResponse.bodyAsText()
        val setTaskPushResponse = json.decodeFromString<SetTaskPushNotificationResponse>(setPushResponseBody)

        assertEquals(requestId, setTaskPushResponse.id)
        assertNotNull(setTaskPushResponse.result)
        assertNull(setTaskPushResponse.error)
        assertEquals(taskId, setTaskPushResponse.result?.id)
        assertEquals(pushConfig.url, setTaskPushResponse.result?.pushNotificationConfig?.url)

        // When - Get push notification
        val getPushRequest = GetTaskPushNotificationRequest(
            id = "get-push-req-123",
            params = TaskIdParams(id = taskId)
        )

        val getPushResponse = client.post(apiUrl) {
            setBody(json.encodeToString(JsonRpcRequest.serializer(), getPushRequest))
        }

        // Then - Get push notification response
        assertEquals(HttpStatusCode.OK, getPushResponse.status)
        val getPushResponseBody = getPushResponse.bodyAsText()
        val getTaskPushResponse = json.decodeFromString<GetTaskPushNotificationResponse>(getPushResponseBody)

        assertEquals("get-push-req-123", getTaskPushResponse.id)
        assertNotNull(getTaskPushResponse.result)
        assertNull(getTaskPushResponse.error)
        assertEquals(taskId, getTaskPushResponse.result?.id)
        assertEquals(pushConfig.url, getTaskPushResponse.result?.pushNotificationConfig?.url)
    }
}
