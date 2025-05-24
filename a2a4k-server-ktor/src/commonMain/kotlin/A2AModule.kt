// SPDX-FileCopyrightText: 2025
//
// SPDX-License-Identifier: Apache-2.0
package io.github.a2a_4k

import io.github.a2a_4k.models.AgentCard
import io.github.a2a_4k.models.CancelTaskRequest
import io.github.a2a_4k.models.ErrorResponse
import io.github.a2a_4k.models.GetTaskPushNotificationRequest
import io.github.a2a_4k.models.GetTaskRequest
import io.github.a2a_4k.models.InternalError
import io.github.a2a_4k.models.InvalidRequestError
import io.github.a2a_4k.models.JsonParseError
import io.github.a2a_4k.models.MethodNotFoundError
import io.github.a2a_4k.models.SendTaskRequest
import io.github.a2a_4k.models.SendTaskStreamingRequest
import io.github.a2a_4k.models.SetTaskPushNotificationRequest
import io.github.a2a_4k.models.TaskResubscriptionRequest
import io.github.a2a_4k.models.UnknownMethodRequest
import io.github.a2a_4k.models.toJson
import io.github.a2a_4k.models.toJsonRpcRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.sse.*
import kotlinx.serialization.SerializationException

/**
 * Configures the application routing for the A2A server.
 *
 * This extension function sets up two main routes:
 * 1. An SSE (Server-Sent Events) endpoint that handles various JSON-RPC requests for task management
 * 2. A well-known endpoint (/.well-known/agent.json) that returns the agent's metadata card
 *
 * The SSE endpoint processes different types of requests and either responds immediately or
 * establishes a streaming connection for long-running tasks.
 *
 * @receiver The Ktor Application instance to configure
 */

private val log = KotlinLogging.logger {}


fun Application.a2aModule(endpoint: String, taskManager: TaskManager, agentCard: AgentCard, devMode: Boolean) {
    install(SSE)
    if (devMode) {
        install(CORS) {
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            anyHost()
        }
    }

    routing {
        sse {
            try {
                val streamingResult = when (val jsonRpcRequest = call.receiveText().toJsonRpcRequest()) {
                    is SendTaskStreamingRequest -> taskManager.onSendTaskSubscribe(jsonRpcRequest)
                    is TaskResubscriptionRequest -> taskManager.onResubscribeToTask(jsonRpcRequest)
                    else -> throw IllegalArgumentException("Unexpected request type: ${jsonRpcRequest::class.simpleName}")
                }

                streamingResult.collect {
                    send(ServerSentEvent(it.toJson()))
                }
            } catch (e: Exception) {
                handleException(call, e)
            }
        }
        post(endpoint) {
            try {
                val result = when (val jsonRpcRequest = call.receiveText().toJsonRpcRequest()) {
                    is GetTaskRequest -> taskManager.onGetTask(jsonRpcRequest)
                    is SendTaskRequest -> taskManager.onSendTask(jsonRpcRequest)
                    is CancelTaskRequest -> taskManager.onCancelTask(jsonRpcRequest)
                    is SetTaskPushNotificationRequest -> taskManager.onSetTaskPushNotification(jsonRpcRequest)
                    is GetTaskPushNotificationRequest -> taskManager.onGetTaskPushNotification(jsonRpcRequest)
                    is UnknownMethodRequest -> ErrorResponse(id = jsonRpcRequest.id, error = MethodNotFoundError())
                    else -> throw IllegalArgumentException("Unexpected request type: ${jsonRpcRequest::class.simpleName}")
                }
                call.respondText(
                    result.toJson(),
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.OK,
                )
            } catch (e: Exception) {
                handleException(call, e)
            }
        }

        get("/.well-known/agent.json") {
            call.respond(HttpStatusCode.OK, agentCard.toJson())
        }
    }
}

/**
 * Handles exceptions that occur during request processing and returns appropriate error responses.
 *
 * This method maps different types of exceptions to specific JSON-RPC error types:
 * - IllegalArgumentException is mapped to InvalidRequestError
 * - All other exceptions are logged and mapped to InternalError
 *
 * The method then creates an ErrorResponse and sends it back to the client with a BadRequest status code.
 *
 * @param call The ApplicationCall context for the current request
 * @param e The exception that was thrown during request processing
 */
private suspend fun handleException(call: ApplicationCall, e: Exception) {
    log.error("Exception detected: $e")
    val jsonRpcError = when (e) {
        is SerializationException -> JsonParseError()
        is IllegalArgumentException -> InvalidRequestError()
        else -> {
            log.error("Unhandled exception: $e")
            InternalError()
        }
    }

    val response = ErrorResponse(id = null, error = jsonRpcError)
    call.respond(HttpStatusCode.OK, response.toJson())
}
