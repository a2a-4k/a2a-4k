// SPDX-FileCopyrightText: 2025
//
// SPDX-License-Identifier: Apache-2.0
package io.github.a2a_4k

import io.github.a2a_4k.models.CancelTaskRequest
import io.github.a2a_4k.models.CancelTaskResponse
import io.github.a2a_4k.models.GetTaskPushNotificationRequest
import io.github.a2a_4k.models.GetTaskPushNotificationResponse
import io.github.a2a_4k.models.GetTaskRequest
import io.github.a2a_4k.models.GetTaskResponse
import io.github.a2a_4k.models.InternalError
import io.github.a2a_4k.models.JsonRpcError
import io.github.a2a_4k.models.PushNotificationConfig
import io.github.a2a_4k.models.SendTaskRequest
import io.github.a2a_4k.models.SendTaskResponse
import io.github.a2a_4k.models.SendTaskStreamingRequest
import io.github.a2a_4k.models.SendTaskStreamingResponse
import io.github.a2a_4k.models.SetTaskPushNotificationRequest
import io.github.a2a_4k.models.SetTaskPushNotificationResponse
import io.github.a2a_4k.models.StringOrInt
import io.github.a2a_4k.models.Task
import io.github.a2a_4k.models.TaskArtifactUpdateEvent
import io.github.a2a_4k.models.TaskIdParams
import io.github.a2a_4k.models.TaskNotCancelableError
import io.github.a2a_4k.models.TaskNotFoundError
import io.github.a2a_4k.models.TaskPushNotificationConfig
import io.github.a2a_4k.models.TaskQueryParams
import io.github.a2a_4k.models.TaskResubscriptionRequest
import io.github.a2a_4k.models.TaskSendParams
import io.github.a2a_4k.models.TaskState
import io.github.a2a_4k.models.TaskStatus
import io.github.a2a_4k.models.TaskStatusUpdateEvent
import io.github.a2a_4k.models.TaskStreamingResult
import io.github.a2a_4k.notifications.BasicNotificationPublisher
import io.github.a2a_4k.notifications.NotificationPublisher
import io.github.a2a_4k.storage.TaskStorage
import io.github.a2a_4k.storage.loadTaskStorage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * In-memory implementation of the TaskManager interface.
 *
 * This class provides an implementation of the TaskManager interface that stores
 * tasks and their associated data in memory. It supports all operations defined
 * in the TaskManager interface, including task creation, retrieval, cancellation,
 * and subscription to task updates.
 */

expect fun createSafeChannelMap(): MutableMap<String, MutableList<Channel<Any>>>
expect fun createSafeChannelList(): MutableList<Channel<Any>>

class BasicTaskManager(
    private val taskHandler: TaskHandler,
    private val taskStorage: TaskStorage = loadTaskStorage(),
    private val notificationPublisher: NotificationPublisher? = BasicNotificationPublisher(),
) : TaskManager {

    private val log = createLogger(this::class)

    /** Map of task IDs to lists of subscribers for server-sent events */
    private val taskSseSubscribers: MutableMap<String, MutableList<Channel<Any>>> = createSafeChannelMap()

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
        val taskResult = task.limitHistory(taskQueryParams.historyLength)
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
     * This implementation creates or updates a task in the memory store.
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
            val pushNotificationConfig = taskSendParams.pushNotification?.let {
                setPushNotificationInfo(taskSendParams.id, it)
            } ?: taskStorage.fetchNotificationConfig(task.id)

            // Send push notification WORKING
            pushNotificationConfig?.let { notificationPublisher?.publish(task.working(), it) }

            // Send Task to Agent
            var updatedTask: Task = task
            taskHandler.handle(task).collect { update ->
                when (update) {
                    // Send task status update
                    is ArtifactUpdate -> {
                        updatedTask = updatedTask.addArtifacts(update.artifacts)
                        update.artifacts.forEach { artifact ->
                            sendSseEvent(task.id, TaskArtifactUpdateEvent(task.id, artifact = artifact))
                        }
                    }

                    // Send final task status update
                    is StatusUpdate -> {
                        updatedTask = updatedTask.updateStatus(update.status)
                        sendSseEvent(task.id, TaskStatusUpdateEvent(task.id, update.status, update.final))
                    }
                }
            }

            // Send push notification after processing has completed
            pushNotificationConfig?.let { notificationPublisher?.publish(updatedTask, it) }

            // Store the task after processing has completed
            taskStorage.store(updatedTask)

            // Return the task with appropriate history length
            SendTaskResponse(id = request.id, result = updatedTask.limitHistory(taskSendParams.historyLength))
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
            val pushNotificationConfig = taskSendParams.pushNotification?.let {
                setPushNotificationInfo(taskSendParams.id, it)
            } ?: taskStorage.fetchNotificationConfig(task.id)

            // Set up SSE consumer
            val sseEventQueue = setupSseConsumer(taskSendParams.id)

            // Send push notification WORKING
            pushNotificationConfig?.let { notificationPublisher?.publish(task.working(), it) }

            // Send Task to Agent
            var updatedTask: Task = task
            taskHandler.handle(task).collect { update ->
                when (update) {
                    // Send task status update
                    is ArtifactUpdate -> {
                        updatedTask = updatedTask.addArtifacts(update.artifacts)
                        update.artifacts.forEach { artifact ->
                            sendSseEvent(task.id, TaskArtifactUpdateEvent(task.id, artifact = artifact))
                        }
                    }

                    // Send final task status update
                    is StatusUpdate -> {
                        updatedTask = updatedTask.updateStatus(update.status)
                        sendSseEvent(task.id, TaskStatusUpdateEvent(task.id, update.status, update.final))
                    }
                }
            }

            // Send push notification after processing has completed
            pushNotificationConfig?.let { notificationPublisher?.publish(updatedTask, it) }

            // Store the task after processing has completed
            taskStorage.store(updatedTask)

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
     * @return The push notification configuration that was set
     * @throws IllegalArgumentException if the task is not found
     */
    private suspend fun setPushNotificationInfo(
        taskId: String,
        notificationConfig: PushNotificationConfig,
    ): PushNotificationConfig {
        taskStorage.storeNotificationConfig(taskId, notificationConfig)
        return notificationConfig
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
                sessionId = taskSendParams.sessionId ?: randomUUID(),
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
        taskSseSubscribers.computeIfAbsent(taskId) { createSafeChannelList() }.add(sseEventQueue)
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
        requestId: StringOrInt?,
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
