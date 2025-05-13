// SPDX-FileCopyrightText: 2025
//
// SPDX-License-Identifier: Apache-2.0
package io.github.a2a_4k.models

import io.github.a2a_4k.models.TaskState.COMPLETED
import io.github.a2a_4k.models.TaskState.FAILED
import io.github.a2a_4k.models.TaskState.WORKING
import kotlinx.datetime.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Marker interface for streaming task results.
 * This sealed class serves as a base for all classes that represent streaming updates for tasks.
 */
@Serializable
sealed class TaskStreamingResult

// Core Objects
@Serializable
data class Task(
    val id: String,
    val sessionId: String? = null,
    val status: TaskStatus,
    val history: List<Message>? = null,
    val artifacts: List<Artifact>? = null,
    val metadata: Map<String, String> = emptyMap(),
) {
    /**
     * Creates a copy of this task with the status set to WORKING.
     *
     * @return A new Task instance with updated status
     */
    fun working(): Task {
        return copy(status = TaskStatus(WORKING))
    }

    /**
     * Creates a copy of this task with the status set to COMPLETED and adds a text artifact.
     *
     * @param artifact The text content to add as an artifact
     * @return A new Task instance with updated status and artifacts
     */
    fun completed(artifact: String): Task {
        return copy(artifacts = listOf(textArtifact(artifact)), status = TaskStatus(COMPLETED))
    }

    /**
     * Creates a copy of this task with the status set to FAILED.
     *
     * @return A new Task instance with updated status
     */
    fun failed(): Task {
        return copy(status = TaskStatus(FAILED))
    }

    /**
     * Updates the status of this task and manages the history of messages.
     * If the update contains a message, the current status message is added to history.
     *
     * @param update The new status to apply to the task
     * @return A new Task instance with updated status and history
     */
    fun updateStatus(update: TaskStatus): Task {
        if (update.message == null) return copy(status = update)
        return copy(status = update, history = listOf(status.message!!) + (history ?: listOf()))
    }

    /**
     * Adds artifacts to the task's existing artifacts list.
     *
     * @param updates List of artifacts to add
     * @return A new Task instance with updated artifacts
     */
    fun addArtifacts(updates: List<Artifact>): Task {
        return copy(artifacts = updates + (artifacts ?: listOf()))
    }

    /**
     * Creates a copy of the task with a limited history length.
     *
     * @param historyLength The maximum number of history items to include, or null for no history
     * @return A copy of the task with limited history
     */
    fun limitHistory(historyLength: Int?): Task {
        val updatedHistory = if (historyLength != null && historyLength > 0) {
            history?.takeLast(historyLength)?.toMutableList() ?: emptyList()
        } else {
            null
        }
        return copy(history = updatedHistory)
    }
}

/**
 * Represents the current status of a task.
 *
 * @property state The current state of the task (e.g., WORKING, COMPLETED, FAILED)
 * @property message Optional message associated with this status
 * @property timestamp When this status was created, as an ISO-8601 string
 */
@Serializable
data class TaskStatus(
    val state: TaskState,
    val message: Message? = null,
    val timestamp: String = Clock.System.now().toString(),
)

/**
 * Event representing a status update for a task.
 * This is used for streaming updates about task status changes.
 *
 * @property id The identifier of the task being updated
 * @property status The new status of the task
 * @property final Whether this is the final status update for the task
 * @property metadata Additional key-value pairs for storing custom information
 */
@Serializable
data class TaskStatusUpdateEvent(
    val id: String,
    val status: TaskStatus,
    val final: Boolean,
    val metadata: Map<String, String> = emptyMap(),
) : TaskStreamingResult()

/**
 * Event representing an artifact update for a task.
 * This is used for streaming updates about new artifacts produced by a task.
 *
 * @property id The identifier of the task being updated
 * @property artifact The new artifact being added to the task
 * @property metadata Additional key-value pairs for storing custom information
 */
@Serializable
data class TaskArtifactUpdateEvent(
    val id: String,
    val artifact: Artifact,
    val metadata: Map<String, String> = emptyMap(),
) : TaskStreamingResult()

/**
 * Parameters for sending a task to be processed.
 *
 * @property id Unique identifier for the task
 * @property sessionId Optional identifier for the session this task belongs to
 * @property message The message to be processed by the task
 * @property historyLength Optional limit on the number of history items to include
 * @property pushNotification Optional configuration for push notifications
 * @property metadata Additional key-value pairs for storing custom information
 */
@Serializable
data class TaskSendParams(
    val id: String,
    val sessionId: String? = null,
    val message: Message,
    val historyLength: Int? = null,
    val pushNotification: PushNotificationConfig? = null,
    val metadata: Map<String, String> = emptyMap(),
)

/**
 * Enumeration of possible states for a task.
 * Each state represents a different phase in the task lifecycle.
 */
@Serializable
enum class TaskState {
    /**
     * Task has been submitted but processing has not yet started.
     */
    @SerialName("submitted")
    SUBMITTED,

    /**
     * Task is currently being processed.
     */
    @SerialName("working")
    WORKING,

    /**
     * Task requires additional input to continue processing.
     */
    @SerialName("input-required")
    INPUT_REQUIRED,

    /**
     * Task has been successfully completed.
     */
    @SerialName("completed")
    COMPLETED,

    /**
     * Task was canceled before completion.
     */
    @SerialName("canceled")
    CANCELED,

    /**
     * Task encountered an error and failed to complete.
     */
    @SerialName("failed")
    FAILED,

    /**
     * Task is in an unknown state.
     */
    @SerialName("unknown")
    UNKNOWN,
}
