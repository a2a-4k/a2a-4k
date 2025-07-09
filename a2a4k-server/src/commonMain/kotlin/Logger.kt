// SPDX-FileCopyrightText: 2025
//
// SPDX-License-Identifier: Apache-2.0
package io.github.a2a_4k

import kotlin.reflect.KClass

/**
 * Creates a platform-specific logger instance for the given class.
 *
 * @param kClass The class for which the logger should be created.
 * @return A logger instance.
 */
expect fun createLogger(kClass: KClass<*>): Logger

/**
 * Interface for cross-platform logging.
 */
interface Logger {

    /**
     * Logs an info message.
     *
     * @param message The message to log.
     */
    fun info(message: String)

    /**
     * Logs a warning message.
     *
     * @param message The warning message to log.
     */
    fun warn(message: String)

    /**
     * Logs a warning message with an optional throwable.
     *
     * @param message The warning message to log.
     * @param throwable Optional throwable to log.
     */
    fun warn(message: String, throwable: Throwable? = null)

    /**
     * Logs a debug message.
     *
     * @param message The debug message to log.
     */
    fun debug(message: String)

    /**
     * Logs an error message with an optional throwable.
     *
     * @param message The error message to log.
     * @param throwable Optional throwable to log.
     */
    fun error(message: String, throwable: Throwable? = null)
}
