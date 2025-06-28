package com.dmdbrands.appsync

/**
 * JNI bridge for the native AppSync library.
 * This matches the original Cordova plugin's JNI signature.
 */
object CameraHandlerCallback {
    init {
        System.loadLibrary("appsync")
    }

    /**
     * Native detector function.
     * This must match the signature in the .so file.
     */
    external fun nativeDetector(
        data: ByteArray,
        width: Int,
        height: Int,
    ): IntArray?
}
