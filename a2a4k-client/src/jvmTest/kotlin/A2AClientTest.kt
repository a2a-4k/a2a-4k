// SPDX-FileCopyrightText: 2025
//
// SPDX-License-Identifier: Apache-2.0
package io.github.a2a_4k

import io.github.a2a_4k.models.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class A2AClientTest {
    private lateinit var server: A2AServer
    private lateinit var client: A2AClient
    private lateinit var taskManager: TaskManager
    private lateinit var agentCard: AgentCard

    private val serverHost = "localhost"
    private val serverPort = 5001
    private val serverUrl = "http://$serverHost:$serverPort"
    private val apiEndpoint = "/"

    @BeforeEach
    fun setup() {
        // Create agent card
        agentCard = AgentCard(
            name = "Test Agent",
            description = "A test agent for A2AClient",
            url = serverUrl,
            version = "1.0.0",
            capabilities = Capabilities(
                streaming = true,
                pushNotifications = true,
            ),
            defaultInputModes = listOf("text"),
            defaultOutputModes = listOf("text"),
            skills = emptyList(),
        )

        // Create task manager
        taskManager = BasicTaskManager(NoopTaskHandler())

        // Create and start server
        server = A2AServer(
            host = serverHost,
            port = serverPort,
            endpoint = apiEndpoint,
            agentCard = agentCard,
            taskManager = taskManager,
        )

        // Start server in a separate thread
        Thread {
            server.start()
        }.start()

        // Create client
        client = A2AClient(
            baseUrl = serverUrl,
            endpoint = apiEndpoint,
        )

        // Wait for server to start
        Thread.sleep(1000)
    }

    @AfterEach
    fun teardown() {
        client.close()
        server.stop()
    }

    @Test
    fun `test getAgentCard`() = runBlocking {
        // When
        val responseAgentCard = client.getAgentCard()

        // Then
        assertEquals(agentCard.name, responseAgentCard.name)
        assertEquals(agentCard.description, responseAgentCard.description)
        assertEquals(agentCard.version, responseAgentCard.version)
    }

    @Test
    fun `test getTask for non-existent task`() = runBlocking {
        // Given
        val taskId = "non-existent-task"

        // When
        val response = client.getTask(taskId)

        // Then
        assertNull(response.result)
        assertNotNull(response.error)
        assertEquals(-32001, response.error?.code)
    }

    @Test
    fun `test sendTask and getTask`() = runBlocking {
        // Given
        val taskId = "test-task-123"
        val sessionId = "session-123"
        val textPart = TextPart(text = "Hello", metadata = emptyMap())
        val message = Message(role = "user", parts = listOf(textPart))

        // When - Send task
        val sendResponse = client.sendTask(message, taskId, sessionId)

        // Then - Send task response
        assertNotNull(sendResponse.result)
        assertNull(sendResponse.error)
        assertEquals(taskId, sendResponse.result?.id)
        assertEquals(sessionId, sendResponse.result?.sessionId)
        assertEquals(TaskState.SUBMITTED, sendResponse.result?.status?.state)
        assertEquals(1, sendResponse.result?.history?.size)

        // When - Get task
        val getResponse = client.getTask(taskId)

        // Then - Get task response
        assertNotNull(getResponse.result)
        assertNull(getResponse.error)
        assertEquals(taskId, getResponse.result?.id)
        assertEquals(sessionId, getResponse.result?.sessionId)
        assertEquals(TaskState.SUBMITTED, getResponse.result?.status?.state)
        assertEquals(1, getResponse.result?.history?.size)
    }

    @Test
    fun `test cancelTask for non-existent task`() = runBlocking {
        // Given
        val taskId = "non-existent-task"

        // When
        val response = client.cancelTask(taskId)

        // Then
        assertNull(response.result)
        assertNotNull(response.error)
        assertEquals(-32001, response.error?.code)
    }

    @Test
    fun `test setTaskPushNotification and getTaskPushNotification`() = runBlocking {
        // Given
        val taskId = "test-task-456"
        val sessionId = "session-456"
        val textPart = TextPart(text = "Hello", metadata = emptyMap())
        val message = Message(role = "user", parts = listOf(textPart))

        // Create task first
        client.sendTask(message, taskId, sessionId)

        // Create push notification config
        val config = PushNotificationConfig(
            url = "https://example.com/webhook",
        )

        // When - Set push notification
        val setResponse = client.setTaskPushNotification(taskId, config)

        // Then - Set push notification response
        assertNotNull(setResponse.result)
        assertNull(setResponse.error)
        assertEquals(taskId, setResponse.result?.id)

        // When - Get push notification
        val getResponse = client.getTaskPushNotification(taskId)

        // Then - Get push notification response
        assertNotNull(getResponse.result)
        assertNull(getResponse.error)
        assertEquals(taskId, getResponse.result?.id)
        assertEquals(config.url, getResponse.result?.pushNotificationConfig?.url)
    }
}

class NoopTaskHandler : TaskHandler {
    override fun handle(task: Task): Flow<TaskUpdate> {
        return flowOf(StatusUpdate(status = TaskStatus(TaskState.COMPLETED)))
    }
}
