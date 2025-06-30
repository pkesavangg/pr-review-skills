package com.greatergoods.libs.appsync.screen.components

import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dmdbrands.appsync.CameraHandlerCallback
import com.greatergoods.libs.appsync.model.AppSyncResult
import com.greatergoods.libs.appsync.strings.AppSyncStrings
import com.greatergoods.libs.appsync.utility.AppSyncFs003Interpreter
import com.greatergoods.libs.appsync.utility.AppSyncLowLightDetector
import java.util.concurrent.ExecutorService
import android.graphics.ImageFormat
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * Camera preview component that provides real-time camera feed and image analysis.
 *
 * This composable integrates CameraX with Jetpack Compose to provide:
 * - Real-time camera preview using the back camera
 * - Image analysis for FS003 protocol detection
 * - Low light condition detection
 * - Error handling for camera initialization failures
 * - Overlay graphics for scan targeting
 *
 * The component uses AndroidView to wrap the CameraX PreviewView and sets up
 * an ImageAnalysis use case to process camera frames in real-time. Each frame
 * is analyzed using native JNI code to detect FS003 protocol data from smart scales.
 *
 * The component automatically handles camera lifecycle management and provides
 * callbacks for various events including camera readiness, scan results, errors,
 * and low light conditions.
 *
 * @param onCameraReady Callback invoked when the camera is successfully initialized
 *                      and ready for use. Provides access to the camera, camera control,
 *                      and camera info objects for external manipulation.
 * @param cameraExecutor Executor service used for image analysis processing. This
 *                       should be a dedicated executor to avoid blocking the main thread.
 * @param onScanResult Callback invoked when a valid FS003 scan result is detected.
 *                     This is called with the interpreted scan data including weight,
 *                     body fat, muscle, and water percentages.
 * @param onError Callback invoked when camera initialization fails or other errors
 *                occur. Provides an error message describing the issue.
 * @param onLowLightDetected Callback invoked when low light conditions are detected
 *                           or resolved. This helps the UI provide appropriate feedback
 *                           to the user about lighting conditions.
 */
@Composable
fun CameraPreview(
    onCameraReady: (Camera, CameraControl, CameraInfo) -> Unit,
    cameraExecutor: ExecutorService,
    onScanResult: (AppSyncResult) -> Unit,
    onError: (String) -> Unit = {},
    onLowLightDetected: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    Log.i("CHECK", AppSyncStrings.Initializes)

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                // Create PreviewView for camera display
                val previewView =
                    PreviewView(ctx).apply {
                        layoutParams =
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                    }

                // Initialize CameraX provider
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener(
                    {
                        try {
                            val cameraProvider = cameraProviderFuture.get()

                            // Set up camera preview use case
                            val preview =
                                Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }

                            // Set up image analysis use case for frame processing
                            val imageAnalyzer =
                                ImageAnalysis
                                    .Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                            imageAnalyzer.setAnalyzer(
                                cameraExecutor,
                                { imageProxy ->
                                    processFrameWithJNI(imageProxy, onScanResult, onLowLightDetected)
                                },
                            )

                            // Unbind any existing use cases and bind new ones
                            cameraProvider.unbindAll()
                            val camera =
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageAnalyzer,
                                )

                            // Notify that camera is ready
                            onCameraReady(camera, camera.cameraControl, camera.cameraInfo)
                        } catch (exc: Exception) {
                            Log.e("AppSyncScan", AppSyncStrings.CameraBindingFailed, exc)
                            onError(AppSyncStrings.CameraInitializationFailed)
                        }
                    },
                    ContextCompat.getMainExecutor(ctx),
                )
                previewView
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Add overlay graphics for scan targeting
        CameraOverlayBox()
    }
}

/**
 * Processes a camera frame using JNI/native detector for FS003 protocol detection.
 *
 * This function is called for each camera frame and performs the following operations:
 * 1. Extracts Y-plane (luminance) data from YUV_420_888 format images
 * 2. Detects low light conditions using the luminance data
 * 3. Calls the native JNI detector to look for FS003 protocol patterns
 * 4. Interprets any detected data using the FS003 interpreter
 * 5. Delivers scan results or handles errors appropriately
 *
 * The function only processes YUV_420_888 format images as this is the standard
 * format for camera preview frames. It extracts the Y-plane (luminance) data
 * which contains the grayscale information needed for both low light detection
 * and FS003 pattern recognition.
 *
 * The native detector uses computer vision algorithms to identify the specific
 * patterns displayed on smart scale screens that encode the FS003 protocol data.
 * When patterns are detected, the raw bit data is interpreted to extract weight,
 * body fat, muscle, and water measurements.
 *
 * @param imageProxy The camera frame to process, containing image data and metadata
 * @param onScanResult Callback to deliver successful scan results
 * @param onLowLightDetected Callback to report low light condition changes
 */
private fun processFrameWithJNI(
    imageProxy: ImageProxy,
    onScanResult: (AppSyncResult) -> Unit,
    onLowLightDetected: (Boolean) -> Unit,
) {
    try {
        // Only process YUV_420_888 format images
        if (imageProxy.format == ImageFormat.YUV_420_888) {
            // Extract Y-plane (luminance) data for processing
            val yBuffer = imageProxy.planes[0].buffer
            val width = imageProxy.width
            val height = imageProxy.height
            val ySize = yBuffer.remaining()
            val yBytes = ByteArray(ySize)
            yBuffer.get(yBytes)

            // Check for low light conditions using the extracted luminance data
            val isLowLight = AppSyncLowLightDetector.isLowLight(yBytes, width, height)
            onLowLightDetected(isLowLight)

            // Call native detector to look for FS003 protocol patterns
            val bits = CameraHandlerCallback.nativeDetector(yBytes, width, height)
            if (bits != null && bits.isNotEmpty()) {
                // Interpret the detected bits using FS003 protocol
                val result = AppSyncFs003Interpreter.interpret(bits)
                if (result != null) {
                    // Deliver the successful scan result
                    onScanResult(result)
                } else {
                    // Interpreter failed to process the detected bits
                    Log.w("AppSyncScan", AppSyncStrings.InterpreterReturnedNull)
                }
            } else {
                // Native detector did not find any valid patterns
                // This is normal and expected for most frames
                Log.w("AppSyncScan", AppSyncStrings.NativeDetectorReturnedNull)
            }
        }
    } catch (e: Exception) {
        // Log any errors that occur during frame processing
        Log.e("AppSyncScan", AppSyncStrings.JniFrameProcessingFailed, e)
    } finally {
        // Always close the image proxy to release resources
        imageProxy.close()
    }
}
