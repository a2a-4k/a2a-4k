// SPDX-FileCopyrightText: 2025 Deutsche Telekom AG and others
//
// SPDX-License-Identifier: Apache-2.0
package org.a2a4k.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * A2A Protocol Request types
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("method")
sealed class JsonRpcRequest {
    abstract val jsonrpc: String
    abstract val id: String?
}

@Serializable
data class UnknownMethodRequest(
    override val jsonrpc: String = "2.0",
    override val id: String? = null,
) : JsonRpcRequest()

@Serializable
@SerialName("tasks/send")
data class SendTaskRequest(
    override val jsonrpc: String = "2.0",
    override val id: String? = null,
    val params: TaskSendParams,
) : JsonRpcRequest()

@Serializable
@SerialName("tasks/get")
data class GetTaskRequest(
    override val jsonrpc: String = "2.0",
    override val id: String? = null,
    val params: TaskQueryParams,
) : JsonRpcRequest()

@Serializable
@SerialName("tasks/cancel")
data class CancelTaskRequest(
    override val jsonrpc: String = "2.0",
    override val id: String? = null,
    val params: TaskIdParams,
) : JsonRpcRequest()

@Serializable
@SerialName("tasks/pushNotification/set")
data class SetTaskPushNotificationRequest(
    override val jsonrpc: String = "2.0",
    override val id: String? = null,
    val params: TaskPushNotificationConfig,
) : JsonRpcRequest()

@Serializable
@SerialName("tasks/pushNotification/get")
data class GetTaskPushNotificationRequest(
    override val jsonrpc: String = "2.0",
    override val id: String? = null,
    val params: TaskIdParams,
) : JsonRpcRequest()

@Serializable
@SerialName("tasks/resubscribe")
data class TaskResubscriptionRequest(
    override val jsonrpc: String = "2.0",
    override val id: String? = null,
    val params: TaskQueryParams,
) : JsonRpcRequest()

@Serializable
@SerialName("tasks/sendSubscribe")
data class SendTaskStreamingRequest(
    override val jsonrpc: String = "2.0",
    override val id: String? = null,
    val params: TaskSendParams,
) : JsonRpcRequest()

@Serializable
data class TaskIdParams(
    val id: String,
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class TaskQueryParams(
    val id: String,
    val historyLength: Int? = null,
    val metadata: Map<String, String> = emptyMap(),
)
