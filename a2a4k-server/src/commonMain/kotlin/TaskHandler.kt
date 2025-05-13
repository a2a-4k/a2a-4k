// SPDX-FileCopyrightText: 2025
//
// SPDX-License-Identifier: Apache-2.0
package io.github.a2a_4k

import io.github.a2a_4k.models.*
import io.github.a2a_4k.models.TaskState.COMPLETED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector

/**
 * Interface for handling tasks in the A2A system.
 *
 * Implementations of this interface are responsible for processing tasks and emitting
 * updates about their status and any artifacts they produce. The updates are emitted
 * as a flow of [TaskUpdate] objects.
 */
interface TaskHandler {

    /**
     * Handles a task and returns a flow of updates about its progress.
     *
     * @param task The task to handle
     * @return A flow of [TaskUpdate] objects representing updates about the task's status and artifacts
     */
    fun handle(task: Task): Flow<TaskUpdate>
}

/**
 * Sealed interface for handling task updates in the A2A system.
 *
 * This interface represents different types of updates that can be emitted during task processing.
 * There are two main types of updates:
 * - [ArtifactUpdate]: Represents new artifacts produced by the task
 * - [StatusUpdate]: Represents changes in the task's status
 */
sealed interface TaskUpdate

/**
 * Represents an update about new artifacts produced by a task.
 *
 * @property artifacts The list of artifacts produced by the task
 */
data class ArtifactUpdate(val artifacts: List<Artifact>) : TaskUpdate

/**
 * Represents an update about the status of a task.
 *
 * @property status The new status of the task
 * @property final Whether this is the final status update for the task (default: false)
 */
data class StatusUpdate(val status: TaskStatus, val final: Boolean = false) : TaskUpdate

/**
 * Extension function to emit a completed status update with an optional message.
 *
 * This function creates a [StatusUpdate] with a [COMPLETED] state and an optional
 * assistant message, and emits it through the flow collector.
 *
 * @param message Optional message to include with the completed status
 */
suspend fun FlowCollector<TaskUpdate>.emitAssistantCompleted(message: String? = null) {
    emit(StatusUpdate(TaskStatus(COMPLETED, message = message?.let { assistantMessage(message) })))
}

/**
 * Extension function to emit an artifact update with a text artifact.
 *
 * This function creates an [ArtifactUpdate] with a text artifact created from the
 * provided string, and emits it through the flow collector.
 *
 * @param artifact The text content to include as an artifact
 */
suspend fun FlowCollector<TaskUpdate>.emitArtifact(artifact: String) {
    emit(ArtifactUpdate(listOf(textArtifact(artifact))))
}

/**
 * Extension function to emit a failed status update.
 *
 * This function creates a [StatusUpdate] with a [FAILED] state and emits it
 * through the flow collector.
 */
suspend fun FlowCollector<TaskUpdate>.emitFailed() {
    emit(StatusUpdate(TaskStatus(TaskState.FAILED)))
}
