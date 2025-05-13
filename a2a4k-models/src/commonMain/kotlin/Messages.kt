// SPDX-FileCopyrightText: 2025
//
// SPDX-License-Identifier: Apache-2.0
package io.github.a2a_4k.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
data class Artifact(
    val name: String? = null,
    val description: String? = null,
    val parts: List<Part>,
    val metadata: Map<String, String> = emptyMap(),
    val index: Int? = null,
    val append: Boolean? = null,
    val lastChunk: Boolean? = null,
)

fun textArtifact(text: String): Artifact {
    return Artifact(parts = listOf(TextPart(text)))
}

@Serializable
data class Message(
    val role: String,
    val parts: List<Part>,
    val metadata: Map<String, String> = emptyMap(),
)

fun assistantMessage(text: String, metadata: Map<String, String> = emptyMap()): Message {
    return Message(role = "assistant", parts = listOf(TextPart(text)), metadata = metadata)
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class Part {
    abstract val metadata: Map<String, String>
}

@Serializable
@SerialName("text")
data class TextPart(
    val text: String,
    override val metadata: Map<String, String> = emptyMap(),
) : Part()

@Serializable
@SerialName("file")
data class FilePart(
    val file: FileData,
    override val metadata: Map<String, String> = emptyMap(),
) : Part()

@Serializable
@SerialName("data")
data class DataPart(
    val data: Map<String, String>,
    override val metadata: Map<String, String> = emptyMap(),
) : Part()

@Serializable
data class FileData(
    val name: String? = null,
    val mimeType: String? = null,
    val bytes: String? = null,
    val uri: String? = null,
)

@Serializable
data class Authentication(
    val schemes: List<String>,
    val credentials: String? = null,
)

@Serializable
data class Provider(
    val organization: String,
    val url: String,
)

@Serializable
data class Capabilities(
    val streaming: Boolean = false,
    val pushNotifications: Boolean = false,
    val stateTransitionHistory: Boolean = false,
)
