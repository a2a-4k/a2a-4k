// SPDX-FileCopyrightText: 2025 Deutsche Telekom AG and others
//
// SPDX-License-Identifier: Apache-2.0
package org.a2a4k.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import kotlinx.datetime.Clock


// Marker interface for streaming task results
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
)

@Serializable
data class TaskStatus(
    val state: TaskState,
    val message: Message? = null,
    val timestamp: String = Clock.System.now().toString(), // TODO
)

@Serializable
data class TaskStatusUpdateEvent(
    val id: String,
    val status: TaskStatus,
    val final: Boolean,
    val metadata: Map<String, String> = emptyMap(),
) : TaskStreamingResult()

@Serializable
data class TaskArtifactUpdateEvent(
    val id: String,
    val artifact: Artifact,
    val metadata: Map<String, String> = emptyMap(),
) : TaskStreamingResult()

@Serializable
data class TaskSendParams(
    val id: String,
    val sessionId: String? = null,
    val message: Message,
    val historyLength: Int? = null,
    val pushNotification: PushNotificationConfig? = null,
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
enum class TaskState {
    @SerialName("submitted")
    SUBMITTED,

    @SerialName("working")
    WORKING,

    @SerialName("input-required")
    INPUT_REQUIRED,

    @SerialName("completed")
    COMPLETED,

    @SerialName("canceled")
    CANCELED,

    @SerialName("failed")
    FAILED,

    @SerialName("unknown")
    UNKNOWN,
}
