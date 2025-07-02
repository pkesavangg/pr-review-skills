package com.greatergoods.lib.wificonnect.helper

/**
 * Converts a hex string to a byte array.
 * @return ByteArray representation of the hex string.
 */
fun String.hexToByteArray(): ByteArray {
    val len = this.length
    val data = ByteArray(len / 2)
    var i = 0
    while (i < len) {
        data[i / 2] = ((Character.digit(this[i], 16) shl 4) + Character.digit(this[i + 1], 16)).toByte()
        i += 2
    }
    return data
}
