package com.greatergoods.libs.appsync.utility

import android.util.Log

/**
 * Test helper utility for debugging YUV conversion issues.
 * 
 * This utility provides methods to validate YUV conversion results
 * and help debug issues with different device implementations.
 */
object YUVConversionTestHelper {
    
    private const val TAG = "YUVConversionTest"
    
    /**
     * Validates that the converted grayscale data has expected properties.
     * 
     * @param data The converted grayscale data
     * @param width Expected width
     * @param height Expected height
     * @return true if validation passes, false otherwise
     */
    fun validateGrayscaleData(data: ByteArray, width: Int, height: Int): Boolean {
        val expectedSize = width * height
        val actualSize = data.size
        
        Log.d(TAG, "Validating grayscale data: expected size=$expectedSize, actual size=$actualSize")
        
        if (actualSize != expectedSize) {
            Log.e(TAG, "Size mismatch: expected $expectedSize, got $actualSize")
            return false
        }
        
        // Check for all-zero data (indicates conversion failure)
        val nonZeroCount = data.count { it != 0.toByte() }
        val zeroPercentage = ((data.size - nonZeroCount) * 100) / data.size
        
        Log.d(TAG, "Data analysis: $nonZeroCount non-zero bytes, $zeroPercentage% zeros")
        
        if (zeroPercentage > 95) {
            Log.w(TAG, "Warning: Data appears to be mostly zeros, conversion may have failed")
        }
        
        // Check for reasonable luminance distribution
        val minLuminance = data.minOrNull()?.toInt()?.and(0xFF) ?: 0
        val maxLuminance = data.maxOrNull()?.toInt()?.and(0xFF) ?: 0
        val avgLuminance = data.map { it.toInt().and(0xFF) }.average()
        
        Log.d(TAG, "Luminance range: $minLuminance-$maxLuminance, average: $avgLuminance")
        
        return true
    }
    
    /**
     * Analyzes the YUV plane properties for debugging.
     * 
     * @param rowStride Row stride of the Y plane
     * @param pixelStride Pixel stride of the Y plane
     * @param width Image width
     * @param height Image height
     */
    fun analyzeYUVPlaneProperties(rowStride: Int, pixelStride: Int, width: Int, height: Int) {
        Log.d(TAG, "YUV Plane Analysis:")
        Log.d(TAG, "  Dimensions: ${width}x${height}")
        Log.d(TAG, "  Row stride: $rowStride")
        Log.d(TAG, "  Pixel stride: $pixelStride")
        Log.d(TAG, "  Expected row stride: $width")
        Log.d(TAG, "  Expected pixel stride: 1")
        
        val isContiguous = (rowStride == width && pixelStride == 1)
        Log.d(TAG, "  Is contiguous: $isContiguous")
        
        if (!isContiguous) {
            Log.d(TAG, "  Non-contiguous buffer detected - using row-by-row conversion")
        }
    }
    
    /**
     * Logs conversion statistics for debugging.
     * 
     * @param originalSize Original YUV data size
     * @param convertedSize Converted grayscale data size
     * @param conversionTimeMs Conversion time in milliseconds
     */
    fun logConversionStats(originalSize: Int, convertedSize: Int, conversionTimeMs: Long) {
        Log.d(TAG, "Conversion Statistics:")
        Log.d(TAG, "  Original size: $originalSize bytes")
        Log.d(TAG, "  Converted size: $convertedSize bytes")
        Log.d(TAG, "  Conversion time: ${conversionTimeMs}ms")
        Log.d(TAG, "  Compression ratio: ${formatDouble(originalSize.toDouble() / convertedSize, 2)}x")
    }
    
    /**
     * Formats double values to specified decimal places.
     */
    private fun formatDouble(value: Double, digits: Int) = "%.${digits}f".format(value)
}
