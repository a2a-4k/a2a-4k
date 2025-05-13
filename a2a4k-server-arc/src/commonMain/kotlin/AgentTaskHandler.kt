// SPDX-FileCopyrightText: 2025
//
// SPDX-License-Identifier: Apache-2.0
package io.github.a2a_4k.arc

import io.github.a2a_4k.TaskHandler
import io.github.a2a_4k.TaskUpdate
import io.github.a2a_4k.emitArtifact
import io.github.a2a_4k.emitAssistantCompleted
import io.github.a2a_4k.emitFailed
import io.github.a2a_4k.models.Task
import io.github.a2a_4k.models.content
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.eclipse.lmos.arc.agents.AgentProvider
import org.eclipse.lmos.arc.agents.ConversationAgent
import org.eclipse.lmos.arc.agents.User
import org.eclipse.lmos.arc.agents.agent.executeWithHandover
import org.eclipse.lmos.arc.agents.conversation.AssistantMessage
import org.eclipse.lmos.arc.agents.conversation.Conversation
import org.eclipse.lmos.arc.agents.conversation.UserMessage
import org.eclipse.lmos.arc.agents.conversation.latest
import org.eclipse.lmos.arc.core.Success
import java.util.*

/**
 * A TaskHandler that provides the bridge between the A2A4K task system and the Arc agent system.
 */
class AgentTaskHandler(private val agent: ConversationAgent, private val agentProvider: AgentProvider) : TaskHandler {

    override fun handle(task: Task): Flow<TaskUpdate> = flow {
        // Convert the Task to a Conversation
        val conversation = Conversation(
            conversationId = task.sessionId ?: UUID.randomUUID().toString(),
            user = (task.metadata["user_id"])?.let { User(it.toString()) },
            currentTurnId = (task.metadata["turn_id"])?.toString() ?: UUID.randomUUID().toString(),
            transcript = task.history?.map { m ->
                when (m.role) {
                    "user" -> UserMessage(m.content())
                    "agent" -> AssistantMessage(m.content())
                    else -> error("Unknown role: ${m.role}")
                }
            } ?: emptyList(),
        )

        // Execute the Agent
        val result = agent.executeWithHandover(
            conversation,
            setOf(
                ContextProvider(task),
            ),
            agentProvider,
        )

        // Handle the result
        when (result) {
            is Success -> {
                val assistantMessage = result.value.latest<AssistantMessage>()?.content ?: ""
                emitArtifact(assistantMessage)
                emitAssistantCompleted(assistantMessage)
            }

            else -> emitFailed()
        }
    }
}
