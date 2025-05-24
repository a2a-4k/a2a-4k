package org.a2a4k.utils

import kotlin.js.*

// Top-level external declaration
@JsName("crypto")
external object Crypto {
    fun randomUUID(): String
}

actual class Uuid private constructor(private val uuidString: String) {
    actual override fun toString(): String = uuidString

    actual companion object {
        actual fun fromString(uuidString: String): Uuid = Uuid(uuidString)

        actual fun randomUUID(): Uuid = Uuid(Crypto.randomUUID())
    }
}

actual class UuidUtil actual constructor() {
    actual fun generateUuid(): String = Uuid.randomUUID().toString()

    actual fun generateUuidObject(): Uuid = Uuid.randomUUID()

    actual companion object {
        actual val instance: UuidUtil by lazy { UuidUtil() }
    }
}
