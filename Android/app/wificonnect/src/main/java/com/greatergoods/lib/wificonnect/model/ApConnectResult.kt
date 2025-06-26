package com.greatergoods.lib.wificonnect.model

/**
 * Result of AP mode smart connect.
 */
sealed class ApConnectResult {
    /** Success result. */
    class Success(
        val buffer: ByteArray,
    ) : ApConnectResult() {
        override fun equals(other: Any?): Boolean = other is Success && buffer.contentEquals(other.buffer)

        override fun hashCode(): Int = buffer.contentHashCode()

        override fun toString(): String = "Success(buffer=" + buffer.contentToString() + ")"
    }

    /** Failure result. */
    data class Failure(
        val errorMessage: String,
    ) : ApConnectResult()
}
