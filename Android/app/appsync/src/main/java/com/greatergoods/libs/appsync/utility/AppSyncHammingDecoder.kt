package com.greatergoods.libs.appsync.utility

import com.greatergoods.libs.appsync.config.AppSyncConstants

/**
 * Hamming code decoder for FS003 protocol error detection and correction.
 *
 * This object implements a (7,4) Hamming code decoder that can detect and
 * correct single-bit errors in the FS003 protocol data transmission. The
 * Hamming code adds 3 parity bits to 4 data bits, creating a 7-bit code
 * that can detect and correct one bit error per code word.
 *
 * The decoder uses matrix operations to:
 * 1. Detect errors by computing syndrome bits
 * 2. Correct single-bit errors by flipping the erroneous bit
 * 3. Extract the original 4-bit data from the corrected 7-bit code
 *
 * This error correction is crucial for reliable data extraction from
 * smart scale displays, which may have transmission errors due to
 * lighting conditions, camera noise, or display artifacts.
 */
object AppSyncHammingDecoder {
    private var errorFoundInLastCorrection: Boolean = false

    /**
     * Extracts 4-bit data from a 7-bit Hamming code with error correction.
     *
     * This is the main entry point for Hamming code decoding. It takes a
     * 7-bit Hamming code that may contain transmission errors and returns
     * the corrected 4-bit data value.
     *
     * The process involves:
     * 1. Error detection and correction using syndrome computation
     * 2. Data extraction using the extraction matrix
     * 3. Tracking whether errors were detected for quality assessment
     *
     * @param input 7-bit Hamming code containing 4 data bits and 3 parity bits
     * @return 4-bit data value extracted from the corrected Hamming code
     */
    fun extractData(input: Int): Int {
        val corrected = correctErrors(input)
        return multiply(AppSyncConstants.Hamming.EXTRACTION_MATRIX, corrected)
    }

    /**
     * Checks if an error was found and corrected in the last correction operation.
     *
     * This method provides feedback about the quality of the received data.
     * It can be used to track error rates and assess the reliability of
     * the scanning process.
     *
     * @return true if a single-bit error was detected and corrected in the last operation,
     *         false if no errors were found
     */
    fun wasErrorFoundInLastCorrection(): Boolean = errorFoundInLastCorrection

    /**
     * Corrects single-bit errors in the Hamming code using syndrome computation.
     *
     * This method implements the core Hamming code error correction algorithm:
     * 1. Computes the syndrome by multiplying the received code with the parity check matrix
     * 2. If the syndrome is non-zero, it indicates an error
     * 3. The syndrome value directly indicates which bit position has the error
     * 4. The error is corrected by flipping the bit at the indicated position
     *
     * The method can only correct single-bit errors. Multiple bit errors will
     * either be incorrectly corrected or remain undetected.
     *
     * @param input 7-bit Hamming code that may contain a single-bit error
     * @return Corrected 7-bit Hamming code with the error fixed (if any)
     */
    private fun correctErrors(input: Int): Int {
        var inputVar = input
        // Compute syndrome using parity check matrix
        val check = multiply(AppSyncConstants.Hamming.H_MATRIX, inputVar)

        // If syndrome is non-zero, correct the error
        if (check != 0) {
            // Flip the bit at the position indicated by the syndrome
            inputVar = inputVar xor (1 shl (check - 1))
        }

        // Track whether an error was found for quality assessment
        errorFoundInLastCorrection = (check != 0)
        return inputVar
    }

    /**
     * Performs matrix-vector multiplication for Hamming code operations.
     *
     * This method implements matrix-vector multiplication using bitwise operations
     * for efficiency. It's used for both syndrome computation (error detection)
     * and data extraction from corrected codes.
     *
     * The multiplication is performed in GF(2) (binary field), where addition
     * is XOR and multiplication is AND. The result is computed by taking the
     * parity of the bitwise AND between the vector and each row of the matrix.
     *
     * @param matrix The matrix to multiply with (parity check or extraction matrix)
     * @param vector The vector to multiply (Hamming code)
     * @return Result of matrix-vector multiplication in GF(2)
     */
    private fun multiply(
        matrix: IntArray,
        vector: Int,
    ): Int {
        var result = 0
        for (i in matrix.indices) {
            // Compute parity of bitwise AND for each matrix row
            if (parity(vector and matrix[i])) {
                result = result or (1 shl i)
            }
        }
        return result
    }

    /**
     * Calculates the parity (number of 1 bits modulo 2) of a number.
     *
     * This method efficiently computes the parity of a binary number by
     * counting the number of 1 bits and returning true if the count is odd,
     * false if even.
     *
     * The implementation uses a clever bit manipulation technique that
     * repeatedly removes the least significant 1 bit until the number
     * becomes zero, counting the number of iterations.
     *
     * @param n The number to calculate parity for
     * @return true if the number has an odd number of 1 bits, false if even
     */
    private fun parity(n: Int): Boolean {
        var nVar = n
        var parity = false
        while (nVar != 0) {
            parity = !parity
            // Remove the least significant 1 bit
            nVar = nVar and (nVar - 1)
        }
        return parity
    }
}
