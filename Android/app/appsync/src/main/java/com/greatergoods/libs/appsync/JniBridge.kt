package com.greatergoods.libs.appsync

/**
 * JNI bridge for the native AppSync library.
 * Loads the native library and exposes native methods.
 */
object JniBridge {
    init {
        System.loadLibrary("appsync")
    }

    /**
     * Native detector function (signature to be updated as needed).
     */
    external fun nativeDetector(
        data: ByteArray,
        width: Int,
        height: Int,
    ): IntArray
}
