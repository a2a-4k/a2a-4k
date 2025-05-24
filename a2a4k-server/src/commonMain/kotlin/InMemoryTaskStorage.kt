// SPDX-FileCopyrightText: 2025
//
// SPDX-License-Identifier: Apache-2.0
package io.github.a2a_4k

import co.touchlab.stately.collections.ConcurrentMutableMap
import io.github.a2a_4k.models.PushNotificationConfig
import io.github.a2a_4k.models.Task
import io.github.a2a_4k.storage.TaskStorage

/**
 * In-memory implementation of the TaskStorage interface.
 * Stores tasks and their associated push notification configurations in memory using concurrent hash maps.
 * This implementation is thread-safe but does not persist data across application restarts.
 */
class InMemoryTaskStorage : TaskStorage {

    /** Map of task IDs to their corresponding Task objects */
    private val tasks = ConcurrentMutableMap<String, Task>()

    /** Map of task IDs to their push notification configurations */
    private val pushNotificationInfos: MutableMap<String, PushNotificationConfig> = ConcurrentMutableMap()

    /**
     * Stores a task in the in-memory storage.
     * If a task with the same ID already exists, it will be overwritten.
     *
     * @param task The task to store
     */
    override suspend fun store(task: Task) {
        tasks[task.id] = task
    }

    /**
     * Retrieves a task by its ID from the in-memory storage.
     *
     * @param taskId The ID of the task to retrieve
     * @return The task if found, null otherwise
     */
    override suspend fun fetch(taskId: String): Task? {
        return tasks[taskId]
    }

    /**
     * Stores a push notification configuration for a task.
     * If a configuration for the same task ID already exists, it will be overwritten.
     *
     * @param taskId The ID of the task to associate with the configuration
     * @param config The push notification configuration to store
     * @throws IllegalArgumentException if the task ID does not exist
     */
    override suspend fun storeNotificationConfig(taskId: String, config: PushNotificationConfig) {
        fetch(taskId) ?: throw IllegalArgumentException("Task not found for $taskId")
        pushNotificationInfos[taskId] = config
    }

    /**
     * Retrieves a push notification configuration for a task by its ID.
     *
     * @param taskId The ID of the task whose configuration to retrieve
     * @return The push notification configuration if found, null otherwise
     */
    override suspend fun fetchNotificationConfig(taskId: String): PushNotificationConfig? {
        return pushNotificationInfos[taskId]
    }
}
