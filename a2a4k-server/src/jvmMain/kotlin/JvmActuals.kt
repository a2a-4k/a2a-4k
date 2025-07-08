// SPDX-FileCopyrightText: 2025
//
// SPDX-License-Identifier: Apache-2.0
package io.github.a2a_4k

import io.github.a2a_4k.models.PushNotificationConfig
import io.github.a2a_4k.models.Task
import kotlinx.coroutines.channels.Channel
import java.util.Collections.synchronizedList
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

actual fun createSafeTaskMap(): MutableMap<String, Task> = ConcurrentHashMap<String, Task>()

actual fun createSafeNotificationConfigMap(): MutableMap<String, PushNotificationConfig> = ConcurrentHashMap()

actual fun createSafeChannelMap(): MutableMap<String, MutableList<Channel<Any>>> = ConcurrentHashMap()

actual fun createSafeChannelList(): MutableList<Channel<Any>> = synchronizedList(mutableListOf())

actual fun randomUUID(): String = UUID.randomUUID().toString()