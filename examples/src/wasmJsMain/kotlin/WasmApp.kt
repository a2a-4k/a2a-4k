package io.github.a2a_4k

import androidx.compose.runtime.*
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.jetbrains.compose.web.renderComposable
import io.github.a2a_4k.models.*
import io.github.a2a_4k.models.TaskState
import io.github.a2a_4k.org.a2a4k.utils.UuidUtil

fun main() {
    val scope = MainScope()
    var clientInput by mutableStateOf("")
    var serverOutput by mutableStateOf("")

    val handler = BasicTaskHandler { message ->
        "Echo from server: ${'$'}{message.content()}"
    }

    renderComposable(rootElementId = "root") {
        Div({ style { display(DisplayStyle.Flex) } }) {
            Div({ style { flex(1) } }) {
                H3 { Text("Client") }
                TextArea(value = clientInput, attrs = {
                    onInput { clientInput = it.value }
                })
                Button(attrs = {
                    onClick {
                        val task = Task(
                            id = UuidUtil.instance.generateUuid(),
                            sessionId = "session-1",
                            status = TaskStatus(TaskState.WORKING),
                            history = listOf(clientInput.toUserMessage())
                        )
                        scope.launch {
                            handler.handle(task).collect { update ->
                                when (update) {
                                    is ArtifactUpdate -> {
                                        val text = (update.artifacts.first().parts.first() as TextPart).text
                                        serverOutput = text
                                    }
                                    is StatusUpdate -> {
                                        update.status.message?.let {
                                            serverOutput += "\n" + (it.parts.first() as TextPart).text
                                        }
                                    }
                                }
                            }
                        }
                    }
                }) { Text("Send") }
            }
            Div({ style { flex(1); marginLeft(16.px) } }) {
                H3 { Text("Server") }
                P { Text(serverOutput) }
            }
        }
    }
}
