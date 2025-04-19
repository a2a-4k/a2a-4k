package org.a2a4k.utils

/**
 * Multiplatform UUID class that is compatible with Java's UUID format.
 */
expect class Uuid {
    /**
     * Returns the string representation of this UUID.
     */
    override fun toString(): String

    companion object {
        /**
         * Creates a UUID from the string standard representation.
         * @param uuidString A string representing a UUID.
         * @return A UUID instance.
         */
        fun fromString(uuidString: String): Uuid

        /**
         * Generates a random UUID.
         * @return A randomly generated UUID.
         */
        fun randomUUID(): Uuid
    }
}

/**
 * Utility class for generating UUIDs in a Kotlin Multiplatform way.
 */
expect class UuidUtil() {
    /**
     * Generates a random UUID.
     * @return A string representation of a UUID.
     */
    fun generateUuid(): String

    /**
     * Generates a random UUID object.
     * @return A UUID object.
     */
    fun generateUuidObject(): Uuid

    companion object {
        /**
         * Singleton instance of UuidUtil.
         */
        val instance: UuidUtil
    }
}
