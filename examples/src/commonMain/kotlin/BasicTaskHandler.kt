// SPDX-FileCopyrightText: 2025
//
// SPDX-License-Identifier: Apache-2.0
package io.github.a2a_4k

import io.github.a2a_4k.models.Artifact
import io.github.a2a_4k.models.Message
import io.github.a2a_4k.models.Task
import io.github.a2a_4k.models.TaskState
import io.github.a2a_4k.models.TaskStatus
import io.github.a2a_4k.models.TextPart
import io.github.a2a_4k.models.assistantMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking

/**
 * A simple TaskHandler that calls your Agent.
 */
class BasicTaskHandler(private val completer: suspend (Message) -> String) : TaskHandler {

    override fun handle(task: Task): Flow<TaskUpdate> = flow {
        // Extract the message from the task
        val message = task.history?.last() ?: error("Message was not found!")

        // Call your Agent with the message
        val response = runBlocking { completer(message) }

        // Create a response Artifact
        val responseArtifact = Artifact(
            name = "agent-response",
            parts = listOf(TextPart(text = response)),
        )

        // Return the updated task
        emit(ArtifactUpdate(listOf(responseArtifact)))
        emit(StatusUpdate(TaskStatus(TaskState.COMPLETED, message = assistantMessage(response))))
    }
}

/**
 * Helper function to get message content.
 */
fun Message.content() = (this.parts.firstOrNull() as TextPart).text
