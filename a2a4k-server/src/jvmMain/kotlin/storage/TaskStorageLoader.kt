// SPDX-FileCopyrightText: 2025
//
// SPDX-License-Identifier: Apache-2.0
package io.github.a2a_4k.storage

import io.github.a2a_4k.InMemoryTaskStorage
import org.slf4j.LoggerFactory
import java.util.*

/**
 * JVM implementation of the loadTaskStorage function that uses Java's ServiceLoader
 * mechanism to discover and load TaskStorage implementations.
 *
 * This function:
 * 1. Uses ServiceLoader to find all available TaskStorageProvider implementations
 * 2. Iterates through the providers and calls their provide() method
 * 3. Returns the first non-null TaskStorage instance provided
 * 4. Falls back to an InMemoryTaskStorage if no provider returns a valid TaskStorage
 *
 * The ServiceLoader pattern allows for extensibility, enabling different storage
 * implementations to be plugged in without modifying the core code.
 *
 * @return A TaskStorage implementation from the first successful provider,
 *         or an InMemoryTaskStorage if no provider is available or successful
 */

private val log = LoggerFactory.getLogger("loadTaskStorage")

actual fun loadTaskStorage(): TaskStorage {
    val loader = ServiceLoader.load(TaskStorageProvider::class.java)
    loader.forEach {
        val taskStorage = it.provide()
        if (taskStorage != null) {
            log.info("Loading TaskStorage from ${it.javaClass.name}")
            return taskStorage
        }
    }
    log.info("Using InMemoryTaskStorage...")
    return InMemoryTaskStorage()
}
