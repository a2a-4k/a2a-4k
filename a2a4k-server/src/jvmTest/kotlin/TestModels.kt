// SPDX-FileCopyrightText: 2025
//
// SPDX-License-Identifier: Apache-2.0
package io.github.a2a_4k

import io.github.a2a_4k.models.AgentCard
import io.github.a2a_4k.models.Authentication
import io.github.a2a_4k.models.Capabilities
import io.github.a2a_4k.models.Skill
import io.github.a2a_4k.models.Task
import io.github.a2a_4k.models.TaskState
import io.github.a2a_4k.models.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

val capabilities = Capabilities(
    streaming = true,
    pushNotifications = true,
    stateTransitionHistory = true,
)

val authentication = Authentication(
    schemes = listOf("none"),
)

val skill = Skill(
    id = "test-skill",
    name = "Test Skill",
    description = "A test skill",
    tags = listOf("test"),
    examples = listOf("Example 1"),
    inputModes = listOf("text"),
    outputModes = listOf("text"),
)

val agentCard = AgentCard(
    name = "Test Agent",
    description = "Test Agent Description",
    url = "https://example.com",
    version = "1.0.0",
    capabilities = capabilities,
    authentication = authentication,
    defaultInputModes = listOf("text"),
    defaultOutputModes = listOf("text"),
    skills = listOf(skill),
)

class NoopTaskHandler : TaskHandler {
    override fun handle(task: Task): Flow<TaskUpdate> {
        return flowOf(StatusUpdate(status = TaskStatus(TaskState.COMPLETED)))
    }
}
