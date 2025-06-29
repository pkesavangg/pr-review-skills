package com.greatergoods.libs.appsync.utility

import android.graphics.ImageFormat
import android.media.Image
import androidx.camera.core.ImageProxy
import android.util.Log
import com.greatergoods.libs.appsync.strings.AppSyncStrings
import kotlin.math.sqrt

/**
 * Utility for detecting low light conditions in camera frames.
 * Analyzes the luminance of camera frames to determine if lighting is sufficient for scanning.
 */
object AppSyncLowLightDetector {
    private const val TAG = "AppSyncLowLightDetector"

    // Threshold values for low light detection
    private const val LOW_LIGHT_THRESHOLD = 30.0 // Average luminance threshold
    private const val VERY_LOW_LIGHT_THRESHOLD = 15.0 // Very low light threshold
    private const val SAMPLE_RATE = 0.1 // Sample 10% of pixels for performance

    /**
     * Detects if the current frame has low light conditions using pre-extracted Y-plane data.
     * This method should be used when the Y-plane data has already been extracted for scanning.
     *
     * @param yBytes Pre-extracted Y-plane bytes (luminance data)
     * @param width Image width
     * @param height Image height
     * @return true if low light is detected, false otherwise
     */
    fun isLowLight(yBytes: ByteArray, width: Int, height: Int): Boolean {
        return try {
            val averageLuminance = calculateAverageLuminance(yBytes, width, height)
            val isLowLight = averageLuminance < LOW_LIGHT_THRESHOLD

            if (isLowLight) {
                Log.d(TAG, "Low light detected: average luminance = $averageLuminance")
            }

            isLowLight
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting low light", e)
            false
        }
    }

    /**
     * Detects if the current frame has low light conditions.
     * Note: This method consumes the ImageProxy buffer and should not be used
     * when the same buffer is needed for scanning.
     *
     * @param imageProxy The camera image proxy to analyze
     * @return true if low light is detected, false otherwise
     */
    fun isLowLight(imageProxy: ImageProxy): Boolean {
        return try {
            if (imageProxy.format != ImageFormat.YUV_420_888) {
                Log.w(TAG, "Unsupported image format: ${imageProxy.format}")
                return false
            }

            val yPlane = imageProxy.planes[0]
            val yBuffer = yPlane.buffer
            val ySize = yBuffer.remaining()
            val yBytes = ByteArray(ySize)
            yBuffer.get(yBytes)

            val averageLuminance = calculateAverageLuminance(yBytes, imageProxy.width, imageProxy.height)
            val isLowLight = averageLuminance < LOW_LIGHT_THRESHOLD

            if (isLowLight) {
                Log.d(TAG, "Low light detected: average luminance = $averageLuminance")
            }

            isLowLight
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting low light", e)
            false
        }
    }

    /**
     * Detects if the current frame has low light conditions.
     * Note: This method consumes the Image buffer and should not be used
     * when the same buffer is needed for scanning.
     *
     * @param image The camera image to analyze
     * @return true if low light is detected, false otherwise
     */
    fun isLowLight(image: Image): Boolean {
        return try {
            if (image.format != ImageFormat.YUV_420_888) {
                Log.w(TAG, "Unsupported image format: ${image.format}")
                return false
            }

            val yPlane = image.planes[0]
            val yBuffer = yPlane.buffer
            val ySize = yBuffer.remaining()
            val yBytes = ByteArray(ySize)
            yBuffer.get(yBytes)

            val averageLuminance = calculateAverageLuminance(yBytes, image.width, image.height)
            val isLowLight = averageLuminance < LOW_LIGHT_THRESHOLD

            if (isLowLight) {
                Log.d(TAG, "Low light detected: average luminance = $averageLuminance")
            }

            isLowLight
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting low light", e)
            false
        }
    }

    /**
     * Detects if the current frame has very low light conditions.
     * Note: This method consumes the Image buffer and should not be used
     * when the same buffer is needed for scanning.
     *
     * @param image The camera image to analyze
     * @return true if very low light is detected, false otherwise
     */
    fun isVeryLowLight(image: Image): Boolean {
        return try {
            if (image.format != ImageFormat.YUV_420_888) {
                return false
            }

            val yPlane = image.planes[0]
            val yBuffer = yPlane.buffer
            val ySize = yBuffer.remaining()
            val yBytes = ByteArray(ySize)
            yBuffer.get(yBytes)

            val averageLuminance = calculateAverageLuminance(yBytes, image.width, image.height)
            val isVeryLowLight = averageLuminance < VERY_LOW_LIGHT_THRESHOLD

            if (isVeryLowLight) {
                Log.d(TAG, "Very low light detected: average luminance = $averageLuminance")
            }

            isVeryLowLight
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting very low light", e)
            false
        }
    }

    /**
     * Calculates the average luminance of the image.
     * Uses sampling for performance optimization.
     *
     * @param yBytes Y-plane bytes (luminance data)
     * @param width Image width
     * @param height Image height
     * @return Average luminance value (0-255)
     */
    private fun calculateAverageLuminance(yBytes: ByteArray, width: Int, height: Int): Double {
        val totalPixels = width * height
        val sampleSize = (totalPixels * SAMPLE_RATE).toInt()
        val step = totalPixels / sampleSize

        var sum = 0.0
        var count = 0

        // Sample pixels for performance
        for (i in 0 until totalPixels step step) {
            if (i < yBytes.size) {
                val luminance = yBytes[i].toInt() and 0xFF // Convert to unsigned byte
                sum += luminance
                count++
            }
        }

        return if (count > 0) sum / count else 0.0
    }

    /**
     * Gets the light level description based on luminance value.
     *
     * @param luminance Average luminance value
     * @return Description of light level
     */
    fun getLightLevelDescription(luminance: Double): String {
        return when {
            luminance < VERY_LOW_LIGHT_THRESHOLD -> "Very Low Light"
            luminance < LOW_LIGHT_THRESHOLD -> "Low Light"
            else -> "Good Light"
        }
    }
}
