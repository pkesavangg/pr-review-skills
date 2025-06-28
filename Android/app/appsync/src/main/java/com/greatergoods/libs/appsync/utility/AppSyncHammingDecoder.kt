package com.greatergoods.libs.appsync.utility

import com.greatergoods.libs.appsync.config.AppSyncConstants

/**
 * Hamming code decoder for FS003 protocol.
 * Handles error detection and correction for the 7-bit Hamming codes.
 */
object AppSyncHammingDecoder {
    private var errorFoundInLastCorrection: Boolean = false

    /**
     * Extracts data from a 7-bit Hamming code with error correction.
     *
     * @param input 7-bit Hamming code
     * @return 4-bit data value
     */
    fun extractData(input: Int): Int {
        val corrected = correctErrors(input)
        return multiply(AppSyncConstants.Hamming.EXTRACTION_MATRIX, corrected)
    }

    /**
     * Checks if an error was found in the last correction operation.
     *
     * @return true if an error was detected and corrected
     */
    fun wasErrorFoundInLastCorrection(): Boolean = errorFoundInLastCorrection

    /**
     * Corrects single-bit errors in the Hamming code.
     *
     * @param input 7-bit Hamming code
     * @return Corrected 7-bit Hamming code
     */
    private fun correctErrors(input: Int): Int {
        var inputVar = input
        val check = multiply(AppSyncConstants.Hamming.H_MATRIX, inputVar)

        if (check != 0) {
            inputVar = inputVar xor (1 shl (check - 1))
        }

        errorFoundInLastCorrection = (check != 0)
        return inputVar
    }

    /**
     * Multiplies a matrix with a vector (bitwise operations).
     *
     * @param matrix The matrix to multiply with
     * @param vector The vector to multiply
     * @return Result of matrix-vector multiplication
     */
    private fun multiply(
        matrix: IntArray,
        vector: Int,
    ): Int {
        var result = 0
        for (i in matrix.indices) {
            if (parity(vector and matrix[i])) {
                result = result or (1 shl i)
            }
        }
        return result
    }

    /**
     * Calculates the parity of a number (number of 1 bits modulo 2).
     *
     * @param n The number to calculate parity for
     * @return true if odd number of 1 bits, false if even
     */
    private fun parity(n: Int): Boolean {
        var nVar = n
        var parity = false
        while (nVar != 0) {
            parity = !parity
            nVar = nVar and (nVar - 1)
        }
        return parity
    }
}
