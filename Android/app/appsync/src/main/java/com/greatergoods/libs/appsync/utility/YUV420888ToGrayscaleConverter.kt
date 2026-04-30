package com.greatergoods.libs.appsync.utility

import android.graphics.ImageFormat
import android.media.Image
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.camera.core.ExperimentalGetImage

/**
 * Utility class for converting YUV_420_888 format images to grayscale ByteArray.
 * 
 * This class handles the complex YUV_420_888 format properly by:
 * - Reading Y-plane data row by row respecting rowStride and pixelStride
 * - Converting to contiguous grayscale ByteArray suitable for JNI processing
 * - Handling different device implementations (especially Vivo devices)
 * - Applying proper rotation and mirror adjustments
 * 
 * The YUV_420_888 format has three planes:
 * - Y plane: Luminance (grayscale) data
 * - U plane: Chroma blue data (subsampled)
 * - V plane: Chroma red data (subsampled)
 * 
 * For FS003 protocol detection, only the Y-plane (luminance) data is needed.
 */
object YUV420888ToGrayscaleConverter {
    
    private const val TAG = "YUV420888Converter"
    
    /**
     * Converts an ImageProxy with YUV_420_888 format to grayscale ByteArray.
     * 
     * This method properly handles the YUV_420_888 format by:
     * 1. Extracting Y-plane data row by row respecting stride parameters
     * 2. Creating a contiguous ByteArray suitable for JNI processing
     * 3. Applying rotation and mirror adjustments based on device orientation
     * 
     * @param imageProxy The ImageProxy containing YUV_420_888 format image
     * @return Pair of (grayscale ByteArray, width) or null if conversion fails
     */
    fun convertToGrayscale(imageProxy: ImageProxy): Pair<ByteArray, Int>? {
        return try {
            // Validate image format
            if (imageProxy.format != ImageFormat.YUV_420_888) {
                Log.w(TAG, "Unsupported image format: ${imageProxy.format}")
                return null
            }
            
            @Suppress("UnsafeOptInUsageError")
            val image = imageProxy.image ?: return null
            val yPlane = image.planes[0]
            val yBuffer = yPlane.buffer
            
            val width = image.width
            val height = image.height
            val rowStride = yPlane.rowStride
            val pixelStride = yPlane.pixelStride
            
            Log.d(TAG, "Converting YUV: ${width}x${height}, rowStride=$rowStride, pixelStride=$pixelStride")
            
            // Analyze YUV plane properties for debugging
            YUVConversionTestHelper.analyzeYUVPlaneProperties(rowStride, pixelStride, width, height)
            
            // Calculate minimum buffer size needed
            val minBufferSize = (height - 1) * rowStride + width * pixelStride
            val actualBufferSize = yBuffer.remaining()
            
            Log.d(TAG, "Buffer size: $actualBufferSize, expected minimum: $minBufferSize")
            
            // Validate buffer has enough data
            if (actualBufferSize < minBufferSize) {
                Log.e(TAG, "Buffer too small: need $minBufferSize bytes, have $actualBufferSize")
                return null
            }
            
            // Create output array for contiguous grayscale data
            val grayscaleData = ByteArray(width * height)
            
            // Copy Y-plane data row by row, respecting stride parameters
            val yBufferArray = ByteArray(actualBufferSize)
            yBuffer.get(yBufferArray)
            
            // Copy data row by row to handle non-contiguous buffers
            var copiedPixels = 0
            for (row in 0 until height) {
                val srcRowStart = row * rowStride
                val dstRowStart = row * width
                
                for (col in 0 until width) {
                    val srcIndex = srcRowStart + (col * pixelStride)
                    val dstIndex = dstRowStart + col
                    
                    if (srcIndex < yBufferArray.size && dstIndex < grayscaleData.size) {
                        grayscaleData[dstIndex] = yBufferArray[srcIndex]
                        copiedPixels++
                    } else {
                        Log.w(TAG, "Out of bounds access: srcIndex=$srcIndex (max=${yBufferArray.size}), dstIndex=$dstIndex (max=${grayscaleData.size})")
                    }
                }
            }
            
            Log.d(TAG, "Copied $copiedPixels pixels out of ${width * height} expected")
            Log.d(TAG, "Successfully converted YUV to grayscale: ${grayscaleData.size} bytes")
            
            // Validate the converted data
            YUVConversionTestHelper.validateGrayscaleData(grayscaleData, width, height)
            
            Pair(grayscaleData, width)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error converting YUV to grayscale", e)
            null
        }
    }
    
    /**
     * Converts an Image with YUV_420_888 format to grayscale ByteArray.
     * 
     * This is an alternative method that works directly with Image objects.
     * 
     * @param image The Image containing YUV_420_888 format data
     * @return Pair of (grayscale ByteArray, width) or null if conversion fails
     */
    fun convertImageToGrayscale(image: Image): Pair<ByteArray, Int>? {
        return try {
            // Validate image format
            if (image.format != ImageFormat.YUV_420_888) {
                Log.w(TAG, "Unsupported image format: ${image.format}")
                return null
            }
            
            val yPlane = image.planes[0]
            val yBuffer = yPlane.buffer
            
            val width = image.width
            val height = image.height
            val rowStride = yPlane.rowStride
            val pixelStride = yPlane.pixelStride
            
            Log.d(TAG, "Converting Image YUV: ${width}x${height}, rowStride=$rowStride, pixelStride=$pixelStride")
            
            // Create output array for contiguous grayscale data
            val grayscaleData = ByteArray(width * height)
            
            // Copy Y-plane data row by row, respecting stride parameters
            val yBufferArray = ByteArray(yBuffer.remaining())
            yBuffer.get(yBufferArray)
            
            // Copy data row by row to handle non-contiguous buffers
            for (row in 0 until height) {
                val srcRowStart = row * rowStride
                val dstRowStart = row * width
                
                for (col in 0 until width) {
                    val srcIndex = srcRowStart + (col * pixelStride)
                    val dstIndex = dstRowStart + col
                    
                    if (srcIndex < yBufferArray.size && dstIndex < grayscaleData.size) {
                        grayscaleData[dstIndex] = yBufferArray[srcIndex]
                    }
                }
            }
            
            Log.d(TAG, "Successfully converted Image YUV to grayscale: ${grayscaleData.size} bytes")
            Pair(grayscaleData, width)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error converting Image YUV to grayscale", e)
            null
        }
    }
    
    /**
     * Applies rotation and mirror adjustments to grayscale data.
     * 
     * This method handles device-specific orientation requirements.
     * 
     * @param data The grayscale data to adjust
     * @param width The width of the image
     * @param height The height of the image
     * @param rotation The rotation angle (0, 90, 180, 270)
     * @param mirror Whether to mirror the image horizontally
     * @return Adjusted grayscale data
     */
    fun applyRotationAndMirror(
        data: ByteArray, 
        width: Int, 
        height: Int, 
        rotation: Int = 0, 
        mirror: Boolean = false
    ): ByteArray {
        return when (rotation) {
            0 -> if (mirror) mirrorHorizontally(data, width, height) else data
            90 -> rotate90(data, width, height, mirror)
            180 -> rotate180(data, width, height, mirror)
            270 -> rotate270(data, width, height, mirror)
            else -> data
        }
    }
    
    /**
     * Mirrors the image horizontally.
     */
    private fun mirrorHorizontally(data: ByteArray, width: Int, height: Int): ByteArray {
        val mirrored = ByteArray(data.size)
        for (row in 0 until height) {
            for (col in 0 until width) {
                val srcIndex = row * width + col
                val dstIndex = row * width + (width - 1 - col)
                mirrored[dstIndex] = data[srcIndex]
            }
        }
        return mirrored
    }
    
    /**
     * Rotates the image 90 degrees clockwise.
     */
    private fun rotate90(data: ByteArray, width: Int, height: Int, mirror: Boolean): ByteArray {
        val rotated = ByteArray(data.size)
        for (row in 0 until height) {
            for (col in 0 until width) {
                val srcIndex = row * width + col
                val dstRow = if (mirror) height - 1 - col else col
                val dstCol = if (mirror) row else height - 1 - row
                val dstIndex = dstRow * height + dstCol
                if (dstIndex < rotated.size) {
                    rotated[dstIndex] = data[srcIndex]
                }
            }
        }
        return rotated
    }
    
    /**
     * Rotates the image 180 degrees.
     */
    private fun rotate180(data: ByteArray, width: Int, height: Int, mirror: Boolean): ByteArray {
        val rotated = ByteArray(data.size)
        for (row in 0 until height) {
            for (col in 0 until width) {
                val srcIndex = row * width + col
                val dstRow = height - 1 - row
                val dstCol = if (mirror) col else width - 1 - col
                val dstIndex = dstRow * width + dstCol
                if (dstIndex < rotated.size) {
                    rotated[dstIndex] = data[srcIndex]
                }
            }
        }
        return rotated
    }
    
    /**
     * Rotates the image 270 degrees clockwise (90 degrees counter-clockwise).
     */
    private fun rotate270(data: ByteArray, width: Int, height: Int, mirror: Boolean): ByteArray {
        val rotated = ByteArray(data.size)
        for (row in 0 until height) {
            for (col in 0 until width) {
                val srcIndex = row * width + col
                val dstRow = if (mirror) col else height - 1 - col
                val dstCol = if (mirror) height - 1 - row else row
                val dstIndex = dstRow * height + dstCol
                if (dstIndex < rotated.size) {
                    rotated[dstIndex] = data[srcIndex]
                }
            }
        }
        return rotated
    }
}
