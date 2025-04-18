// SPDX-FileCopyrightText: 2025 Deutsche Telekom AG and others
//
// SPDX-License-Identifier: Apache-2.0
package org.a2a4k

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.a2a4k.models.*
import org.a2a4k.models.GetTaskResponse
import org.a2a4k.notifications.BasicNotificationPublisher
import org.a2a4k.notifications.NotificationPublisher
import org.slf4j.LoggerFactory
import java.util.*
import java.util.Collections.synchronizedList
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of the TaskManager interface.
 *
 * This class provides an implementation of the TaskManager interface that stores
 * tasks and their associated data in memory. It supports all operations defined
 * in the TaskManager interface, including task creation, retrieval, cancellation,
 * and subscription to task updates.
 */
class BasicTaskManager(
    private val taskHandler: TaskHandler,
    private val taskStorage: TaskStorage = InMemoryTaskStorage(),
    private val notificationPublisher: NotificationPublisher? = BasicNotificationPublisher(),
) : TaskManager {

    private val log = LoggerFactory.getLogger(this::class.java)

    /** Map of task IDs to lists of subscribers for server-sent events */
    private val taskSseSubscribers: MutableMap<String, MutableList<Channel<Any>>> = ConcurrentHashMap()

    /**
     * {@inheritDoc}
     *
     * This implementation retrieves a task from the in-memory store by its ID.
     * If the task is not found, it returns an error response.
     */
    override suspend fun onGetTask(request: GetTaskRequest): GetTaskResponse {
        log.info("Getting task ${request.params.id}")
        val taskQueryParams: TaskQueryParams = request.params
        val task = taskStorage.fetch(taskQueryParams.id) ?: return GetTaskResponse(
            id = request.id,
            error = TaskNotFoundError(),
        )
        val taskResult = appendTaskHistory(task, taskQueryParams.historyLength)
        return GetTaskResponse(id = request.id, result = taskResult)
    }

    /**
     * {@inheritDoc}
     *
     * This implementation attempts to cancel a task.
     * Currently, tasks are not cancelable, so it always returns a TaskNotCancelableError.
     * If the task is not found, it returns a TaskNotFoundError.
     */
    override suspend fun onCancelTask(request: CancelTaskRequest): CancelTaskResponse {
        log.info("Cancelling task ${request.params.id}")
        val taskIdParams: TaskIdParams = request.params
        taskStorage.fetch(taskIdParams.id) ?: return CancelTaskResponse(
            id = request.id,
            error = TaskNotFoundError(),
        )
        return CancelTaskResponse(id = request.id, error = TaskNotCancelableError())
    }

    /**
     * {@inheritDoc}
     *
     * This implementation creates or updates a task in the in-memory store.
     * If a push notification configuration is provided, it is stored as well.
     * If an error occurs during the operation, an InternalError is returned.
     */
    override suspend fun onSendTask(request: SendTaskRequest): SendTaskResponse {
        log.info("Sending task ${request.params.id}")
        val taskSendParams: TaskSendParams = request.params

        return try {
            // Create or update the task
            val task = upsertTask(taskSendParams)

            // Set push notification if provided
            taskSendParams.pushNotification?.let {
                setPushNotificationInfo(taskSendParams.id, it)
            }

            // Send Task to Agent
            val handledTask = taskHandler.handle(task)
            taskStorage.store(handledTask)

            // Send push notification if configured
            taskStorage.fetchNotificationConfig(task.id)?.let {
                notificationPublisher?.publish(handledTask, it)
            }

            // Return the task with appropriate history length
            val taskResult = appendTaskHistory(handledTask, taskSendParams.historyLength)
            SendTaskResponse(id = request.id, result = taskResult)
        } catch (e: Exception) {
            log.error("Error while sending task: ${e.message}")
            SendTaskResponse(
                id = request.id,
                error = InternalError(),
            )
        }
    }

    /**
     * {@inheritDoc}
     *
     * This implementation creates or updates a task and sets up
     * a subscription for streaming updates. It returns a flow of streaming responses
     * with task updates.
     */
    override suspend fun onSendTaskSubscribe(request: SendTaskStreamingRequest): Flow<SendTaskStreamingResponse> {
        log.info("Sending task with subscription ${request.params.id}")
        val taskSendParams: TaskSendParams = request.params

        try {
            // Create or update the task
            val task = upsertTask(taskSendParams)

            // Set push notification if provided
            taskSendParams.pushNotification?.let {
                setPushNotificationInfo(taskSendParams.id, it)
            }

            // Set up SSE consumer
            val sseEventQueue = setupSseConsumer(taskSendParams.id)

            // Send initial task status update
            val initialStatusEvent = TaskStatusUpdateEvent(id = task.id, status = task.status, final = false)
            sendSseEvent(task.id, initialStatusEvent)

            // Send Task to Agent
            val handledTask = taskHandler.handle(task)
            taskStorage.store(handledTask)

            // Send push notification if configured
            taskStorage.fetchNotificationConfig(task.id)?.let {
                notificationPublisher?.publish(handledTask, it)
            }

            // Send task status update
            task.artifacts?.forEach { artifact ->
                sendSseEvent(task.id, TaskArtifactUpdateEvent(task.id, artifact = artifact))
            }

            // Send final task status update
            sendSseEvent(task.id, TaskStatusUpdateEvent(status = task.status, id = task.id, final = true))

            // Return the flow of events
            return dequeueEventsForSse(request.id!!, task.id, sseEventQueue)
        } catch (e: Exception) {
            log.error("Error while setting up task subscription: ${e.message}")
            return flow { emit(SendTaskStreamingResponse(id = request.id, error = InternalError())) }
        }
    }

    /**
     * Sets the push notification configuration for a task.
     *
     * @param taskId The ID of the task
     * @param notificationConfig The push notification configuration to set
     * @throws IllegalArgumentException if the task is not found
     */
    private suspend fun setPushNotificationInfo(taskId: String, notificationConfig: PushNotificationConfig) {
        taskStorage.storeNotificationConfig(taskId, notificationConfig)
    }

    /**
     * Retrieves the push notification configuration for a task.
     *
     * @param taskId The ID of the task
     * @return The push notification configuration, or null if not set
     */
    private suspend fun getPushNotificationInfo(taskId: String): PushNotificationConfig? {
        return taskStorage.fetchNotificationConfig(taskId)
    }

    /**
     * {@inheritDoc}
     *
     * This implementation sets the push notification configuration for a task.
     * If the task is not found or an error occurs, it returns an error response.
     */
    override suspend fun onSetTaskPushNotification(request: SetTaskPushNotificationRequest): SetTaskPushNotificationResponse {
        log.info("Setting task push notification ${request.params.id}")
        val taskNotificationParams: TaskPushNotificationConfig = request.params

        return try {
            setPushNotificationInfo(taskNotificationParams.id, taskNotificationParams.pushNotificationConfig)
            SetTaskPushNotificationResponse(id = request.id, result = taskNotificationParams)
        } catch (e: Exception) {
            log.error("Error while setting push notification info: ${e.message}")
            SetTaskPushNotificationResponse(id = request.id, error = InternalError())
        }
    }

    /**
     * {@inheritDoc}
     *
     * This implementation retrieves the push notification configuration for a task.
     * If the task is not found, the configuration is not set, or an error occurs, it returns an error response.
     */
    override suspend fun onGetTaskPushNotification(request: GetTaskPushNotificationRequest): GetTaskPushNotificationResponse {
        log.info("Getting task push notification ${request.params.id}")
        val taskParams: TaskIdParams = request.params

        return try {
            val notificationInfo = getPushNotificationInfo(taskParams.id)
            if (notificationInfo != null) {
                GetTaskPushNotificationResponse(
                    id = request.id,
                    result = TaskPushNotificationConfig(id = taskParams.id, pushNotificationConfig = notificationInfo),
                )
            } else {
                GetTaskPushNotificationResponse(id = request.id, error = InternalError())
            }
        } catch (e: Exception) {
            log.error("Error while getting push notification info: ${e.message}")
            GetTaskPushNotificationResponse(id = request.id, error = InternalError())
        }
    }

    /**
     * Creates a new task or updates an existing one.
     *
     * @param taskSendParams The parameters for creating or updating the task
     * @return The created or updated task
     */
    private suspend fun upsertTask(taskSendParams: TaskSendParams): Task {
        log.info("Upserting task ${taskSendParams.id}")
        val task = taskStorage.fetch(taskSendParams.id)
        return if (task == null) {
            val newTask = Task(
                id = taskSendParams.id,
                sessionId = taskSendParams.sessionId ?: UUID.randomUUID().toString(),
                status = TaskStatus(state = TaskState.SUBMITTED),
                history = listOf(taskSendParams.message),
            )
            taskStorage.store(newTask)
            newTask
        } else {
            // Create a new task with updated history
            val updatedHistory = task.history?.toMutableList() ?: mutableListOf()
            updatedHistory.add(taskSendParams.message)
            val updatedTask = task.copy(history = updatedHistory)
            taskStorage.store(updatedTask)
            updatedTask
        }
    }

    /**
     * {@inheritDoc}
     *
     * This implementation resubscribes to a task in the in-memory store and sets up
     * a subscription for streaming updates. It returns a flow of streaming responses
     * with task updates.
     */
    override suspend fun onResubscribeToTask(request: TaskResubscriptionRequest): Flow<SendTaskStreamingResponse> {
        log.info("Resubscribing to task ${request.params.id}")
        val taskQueryParams: TaskQueryParams = request.params

        try {
            val task = taskStorage.fetch(taskQueryParams.id)
                ?: return flow { emit(SendTaskStreamingResponse(id = request.id, error = TaskNotFoundError())) }

            // Set up SSE consumer with resubscribe flag
            val sseEventQueue = setupSseConsumer(taskQueryParams.id, isResubscribe = true)

            // Send current task status update
            val statusEvent = TaskStatusUpdateEvent(id = task.id, status = task.status, final = false)
            sendSseEvent(task.id, statusEvent)

            // Return the flow of events
            return dequeueEventsForSse(request.id, task.id, sseEventQueue)
        } catch (e: Exception) {
            log.error("Error while resubscribing to task: ${e.message}")
            return flow {
                emit(
                    SendTaskStreamingResponse(
                        id = request.id,
                        error = InternalError(),
                    ),
                )
            }
        }
    }

    /**
     * Creates a copy of the task with a limited history length.
     *
     * @param task The task to process
     * @param historyLength The maximum number of history items to include, or null for no history
     * @return A copy of the task with limited history
     */
    private fun appendTaskHistory(task: Task, historyLength: Int?): Task {
        val updatedHistory = if (historyLength != null && historyLength > 0) {
            task.history?.takeLast(historyLength)?.toMutableList() ?: emptyList()
        } else {
            null
        }
        return task.copy(history = updatedHistory)
    }

    /**
     * Sets up a subscriber for server-sent events for a task.
     *
     * @param taskId The ID of the task to subscribe to
     * @param isResubscribe Whether this is a resubscription to an existing task
     * @return A channel for receiving events
     * @throws IllegalArgumentException if resubscribing to a non-existent task
     */
    private fun setupSseConsumer(taskId: String, isResubscribe: Boolean = false): Channel<Any> {
        if (!taskSseSubscribers.containsKey(taskId) && isResubscribe) {
            throw IllegalArgumentException("Task not found for resubscription")
        }
        val sseEventQueue = Channel<Any>(Channel.UNLIMITED)
        taskSseSubscribers.computeIfAbsent(taskId) { synchronizedList(mutableListOf()) }.add(sseEventQueue)
        return sseEventQueue
    }

    /**
     * Sends an event to all subscribers of a task.
     *
     * @param taskId The ID of the task
     * @param taskUpdateEvent The event to send
     */
    private suspend fun sendSseEvent(taskId: String, taskUpdateEvent: TaskStreamingResult) {
        taskSseSubscribers[taskId]?.forEach { subscriber ->
            subscriber.send(SendTaskStreamingResponse(result = taskUpdateEvent))
        }
    }

    /**
     * Creates a flow of events from a subscriber channel.
     *
     * @param requestId The ID of the request
     * @param taskId The ID of the task
     * @param sseEventQueue The channel to receive events from
     * @return A flow of streaming responses
     */
    private fun dequeueEventsForSse(
        requestId: String?,
        taskId: String,
        sseEventQueue: Channel<Any>,
    ): Flow<SendTaskStreamingResponse> = flow {
        try {
            for (event in sseEventQueue) {
                if (event is JsonRpcError) {
                    emit(SendTaskStreamingResponse(id = requestId, error = event))
                    break
                }
                if (event is TaskStreamingResult) {
                    emit(SendTaskStreamingResponse(id = requestId, result = event))
                    if (event is TaskStatusUpdateEvent && event.final) {
                        break
                    }
                }
            }
        } finally {
            taskSseSubscribers[taskId]?.remove(sseEventQueue)
        }
    }
}
