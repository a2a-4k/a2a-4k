// SPDX-FileCopyrightText: 2025
//
// SPDX-License-Identifier: Apache-2.0
package io.github.a2a_4k

import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

actual fun createLogger(kClass: KClass<*>): Logger = JvmLogger(kClass)

class JvmLogger(kClass: KClass<*>) : Logger {

    private val log = LoggerFactory.getLogger(kClass.java)

    override fun info(message: String) {
        log.info(message)
    }

    override fun warn(message: String) {
        log.warn(message)
    }

    override fun warn(message: String, throwable: Throwable?) {
        log.warn(message, throwable)
    }

    override fun debug(message: String) {
        log.debug(message)
    }

    override fun error(message: String, throwable: Throwable?) {
        log.error(message, throwable)
    }
}
