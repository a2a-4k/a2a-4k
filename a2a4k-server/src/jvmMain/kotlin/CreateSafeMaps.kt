// SPDX-FileCopyrightText: 2025
//
// SPDX-License-Identifier: Apache-2.0
package io.github.a2a_4k

import io.github.a2a_4k.models.PushNotificationConfig
import io.github.a2a_4k.models.Task
import io.github.a2a_4k.storage.TaskStorage
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.ConcurrentHashMap

actual fun createSafeTaskMap(): MutableMap<String, Task> = ConcurrentHashMap<String, Task>()

actual fun createSafeNotificationConfigMap(): MutableMap<String, PushNotificationConfig> = ConcurrentHashMap()

actual fun createSafeChannelMap(): MutableMap<String, MutableList<Channel<Any>>> = ConcurrentHashMap()