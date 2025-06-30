package com.greatergoods.libs.appsync.utility

import androidx.camera.core.ImageProxy
import android.graphics.ImageFormat
import android.media.Image
import android.util.Log

/**
 * Utility for detecting low light conditions in camera frames.
 *
 * This object provides functionality to analyze the luminance of camera frames
 * to determine if lighting conditions are sufficient for successful scanning.
 * Poor lighting can significantly impact the accuracy of FS003 protocol detection,
 * so this utility helps provide user feedback about lighting conditions.
 *
 * The detector works by analyzing the Y-plane (luminance) data from YUV_420_888
 * format images. It calculates the average luminance across a sampled subset
 * of pixels for performance optimization and compares it against configurable
 * thresholds to determine light levels.
 *
 * The utility provides multiple detection methods to accommodate different
 * use cases and performance requirements.
 */
object AppSyncLowLightDetector {
    private const val TAG = "AppSyncLowLightDetector"

    // Threshold values for low light detection
    private const val LOW_LIGHT_THRESHOLD = 30.0 // Average luminance threshold
    private const val VERY_LOW_LIGHT_THRESHOLD = 15.0 // Very low light threshold
    private const val SAMPLE_RATE = 0.1 // Sample 10% of pixels for performance

    /**
     * Detects if the current frame has low light conditions using pre-extracted Y-plane data.
     *
     * This method is optimized for use in the scanning pipeline where Y-plane data
     * has already been extracted for FS003 protocol detection. It reuses the same
     * luminance data to avoid duplicate processing and improve performance.
     *
     * The method samples 10% of the pixels to calculate average luminance and
     * compares it against the low light threshold.
     *
     * @param yBytes Pre-extracted Y-plane bytes (luminance data) from the camera frame
     * @param width Image width in pixels
     * @param height Image height in pixels
     * @return true if low light conditions are detected (average luminance < 30),
     *         false if lighting is sufficient
     */
    fun isLowLight(
        yBytes: ByteArray,
        width: Int,
        height: Int,
    ): Boolean =
        try {
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

    /**
     * Detects if the current frame has low light conditions using ImageProxy.
     *
     * This method analyzes the luminance of a camera frame provided as an ImageProxy.
     * It extracts the Y-plane data and calculates the average luminance to determine
     * if lighting conditions are adequate for scanning.
     *
     * **Important**: This method consumes the ImageProxy buffer. Do not use this
     * method if the same buffer is needed for FS003 protocol scanning, as the
     * buffer will be consumed and unavailable for further processing.
     *
     * @param imageProxy The camera image proxy to analyze for light conditions
     * @return true if low light conditions are detected, false if lighting is sufficient
     */
    fun isLowLight(imageProxy: ImageProxy): Boolean {
        return try {
            // Validate image format
            if (imageProxy.format != ImageFormat.YUV_420_888) {
                Log.w(TAG, "Unsupported image format: ${imageProxy.format}")
                return false
            }

            // Extract Y-plane (luminance) data
            val yPlane = imageProxy.planes[0]
            val yBuffer = yPlane.buffer
            val ySize = yBuffer.remaining()
            val yBytes = ByteArray(ySize)
            yBuffer.get(yBytes)

            // Calculate average luminance and determine light level
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
     * Detects if the current frame has low light conditions using Image.
     *
     * This method analyzes the luminance of a camera frame provided as an Image.
     * It extracts the Y-plane data and calculates the average luminance to determine
     * if lighting conditions are adequate for scanning.
     *
     * **Important**: This method consumes the Image buffer. Do not use this
     * method if the same buffer is needed for FS003 protocol scanning, as the
     * buffer will be consumed and unavailable for further processing.
     *
     * @param image The camera image to analyze for light conditions
     * @return true if low light conditions are detected, false if lighting is sufficient
     */
    fun isLowLight(image: Image): Boolean {
        return try {
            // Validate image format
            if (image.format != ImageFormat.YUV_420_888) {
                Log.w(TAG, "Unsupported image format: ${image.format}")
                return false
            }

            // Extract Y-plane (luminance) data
            val yPlane = image.planes[0]
            val yBuffer = yPlane.buffer
            val ySize = yBuffer.remaining()
            val yBytes = ByteArray(ySize)
            yBuffer.get(yBytes)

            // Calculate average luminance and determine light level
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
     *
     * This method provides a more sensitive detection for extremely poor lighting
     * conditions. It uses a lower threshold (15 vs 30) to identify situations
     * where scanning is likely to fail due to insufficient light.
     *
     * **Important**: This method consumes the Image buffer. Do not use this
     * method if the same buffer is needed for FS003 protocol scanning, as the
     * buffer will be consumed and unavailable for further processing.
     *
     * @param image The camera image to analyze for very low light conditions
     * @return true if very low light conditions are detected (average luminance < 15),
     *         false if lighting is adequate
     */
    fun isVeryLowLight(image: Image): Boolean {
        return try {
            // Validate image format
            if (image.format != ImageFormat.YUV_420_888) {
                return false
            }

            // Extract Y-plane (luminance) data
            val yPlane = image.planes[0]
            val yBuffer = yPlane.buffer
            val ySize = yBuffer.remaining()
            val yBytes = ByteArray(ySize)
            yBuffer.get(yBytes)

            // Calculate average luminance and determine very low light level
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
     * Calculates the average luminance of the image using sampling for performance.
     *
     * This method analyzes the Y-plane (luminance) data to determine the average
     * brightness of the image. To maintain performance, it samples only 10% of
     * the pixels rather than processing the entire image.
     *
     * The sampling approach provides a good approximation of the overall light
     * level while significantly reducing computational overhead, which is
     * important for real-time camera frame processing.
     *
     * @param yBytes Y-plane bytes (luminance data) from the camera frame
     * @param width Image width in pixels
     * @param height Image height in pixels
     * @return Average luminance value ranging from 0 (black) to 255 (white)
     */
    private fun calculateAverageLuminance(
        yBytes: ByteArray,
        width: Int,
        height: Int,
    ): Double {
        val totalPixels = width * height
        val sampleSize = (totalPixels * SAMPLE_RATE).toInt()
        val step = totalPixels / sampleSize

        var sum = 0.0
        var count = 0

        // Sample pixels for performance optimization
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
     * Gets a human-readable description of the light level based on luminance value.
     *
     * This utility method converts a numeric luminance value into a descriptive
     * string that can be used for user feedback or logging purposes.
     *
     * @param luminance Average luminance value (0-255)
     * @return Description of the light level: "Very Low Light", "Low Light", or "Good Light"
     */
    fun getLightLevelDescription(luminance: Double): String =
        when {
            luminance < VERY_LOW_LIGHT_THRESHOLD -> "Very Low Light"
            luminance < LOW_LIGHT_THRESHOLD -> "Low Light"
            else -> "Good Light"
        }
}
