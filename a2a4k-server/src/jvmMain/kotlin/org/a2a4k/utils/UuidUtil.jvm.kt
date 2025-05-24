package org.a2a4k.utils

import java.util.UUID as JavaUUID

/**
 * JVM implementation of Uuid class that wraps Java's UUID.
 */
actual class Uuid internal constructor(private val javaUuid: JavaUUID) {
    /**
     * Returns the string representation of this UUID.
     */
    actual override fun toString(): String = javaUuid.toString()

    actual companion object {
        /**
         * Creates a UUID from the string standard representation.
         * @param uuidString A string representing a UUID.
         * @return A UUID instance.
         */
        actual fun fromString(uuidString: String): Uuid = Uuid(JavaUUID.fromString(uuidString))

        /**
         * Generates a random UUID.
         * @return A randomly generated UUID.
         */
        actual fun randomUUID(): Uuid = Uuid(JavaUUID.randomUUID())
    }
}

/**
 * JVM implementation of UuidUtil.
 */
actual class UuidUtil {
    /**
     * Generates a random UUID using java.util.UUID.
     * @return A string representation of a UUID.
     */
    actual fun generateUuid(): String = JavaUUID.randomUUID().toString()

    /**
     * Generates a random UUID object.
     * @return A UUID object.
     */
    actual fun generateUuidObject(): Uuid = Uuid.randomUUID()

    actual companion object {
        /**
         * Singleton instance of UuidUtil.
         */
        actual val instance: UuidUtil by lazy { UuidUtil() }
    }
}
