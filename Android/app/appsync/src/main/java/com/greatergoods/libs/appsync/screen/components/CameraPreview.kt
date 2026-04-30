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
import com.greatergoods.libs.appsync.utility.YUV420888ToGrayscaleConverter
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
  currentZoom: () -> Int,
  onError: (String) -> Unit = {},
  onLowLightDetected: (Boolean) -> Unit = {},
) {
  LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

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
                Preview.Builder()
                  .build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                  }

              // Set up image analysis use case for frame processing
              // Use a good target resolution that works well for detection at all zoom levels
              // 1280x720 is a good balance - high enough for detection, works with zoom
              val imageAnalyzer =
                ImageAnalysis
                  .Builder()
                  .setTargetResolution(android.util.Size(1280, 720))
                  .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                  .build()
              imageAnalyzer.setAnalyzer(
                cameraExecutor,
                { imageProxy ->
                  processFrameWithJNI(imageProxy, onScanResult, onLowLightDetected, currentZoom)
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
 * 1. Properly converts YUV_420_888 format images to grayscale ByteArray
 * 2. Detects low light conditions using the converted luminance data
 * 3. Calls the native JNI detector to look for FS003 protocol patterns
 * 4. Interprets any detected data using the FS003 interpreter
 * 5. Delivers scan results or handles errors appropriately
 *
 * The function uses proper YUV_420_888 conversion that handles:
 * - Non-contiguous buffers with different row strides and pixel strides
 * - Device-specific implementations (especially Vivo devices)
 * - Rotation and mirror adjustments for different orientations
 *
 * The native detector uses computer vision algorithms to identify the specific
 * patterns displayed on smart scale screens that encode the FS003 protocol data.
 * When patterns are detected, the raw bit data is interpreted to extract weight,
 * body fat, muscle, and water measurements.
 *
 * @param imageProxy The camera frame to process, containing image data and metadata
 * @param onScanResult Callback to deliver successful scan results
 * @param onLowLightDetected Callback to report low light condition changes
 * @param targetRotation The target rotation for the image analysis
 */
private fun processFrameWithJNI(
  imageProxy: ImageProxy,
  onScanResult: (AppSyncResult) -> Unit,
  onLowLightDetected: (Boolean) -> Unit,
  currentZoom: () -> Int,
) {
  try {
    // Only process YUV_420_888 format images
    if (imageProxy.format == ImageFormat.YUV_420_888) {
      val width = imageProxy.width
      val height = imageProxy.height

      // Skip frames that are too small for reliable detection
      // Minimum resolution for FS003 protocol detection
      // Target is 1280x720, but accept reasonable variations for zoom levels
      if (width < 480 || height < 360) {
        Log.d("AppSyncScan", "Skipping frame: resolution too low (${width}x${height})")
        return
      }

      // Convert YUV_420_888 to grayscale using proper stride handling
      // This works at any resolution/zoom level
      val conversionResult = YUV420888ToGrayscaleConverter.convertToGrayscale(imageProxy)

      if (conversionResult != null) {
        val (grayscaleData, convertedWidth) = conversionResult
        val convertedHeight = height

        // Validate data before processing
        if (grayscaleData.isEmpty() || convertedWidth <= 0 || convertedHeight <= 0) {
          Log.w("AppSyncScan", "Invalid converted data: size=${grayscaleData.size}, dimensions=${convertedWidth}x${convertedHeight}")
          return
        }

        // Check for low light conditions using the converted luminance data
        val isLowLight = AppSyncLowLightDetector.isLowLight(grayscaleData, convertedWidth, convertedHeight)
        onLowLightDetected(isLowLight)

        // Call native detector to look for FS003 protocol patterns
        // Target resolution is 1280x720, works well at all zoom levels
        try {
          val bits = CameraHandlerCallback.nativeDetector(grayscaleData, convertedWidth, convertedHeight)
          if (bits != null && bits.isNotEmpty()) {
            Log.d("AppSyncScan", "✅ Pattern detected! Bits count: ${bits.size}, resolution: ${convertedWidth}x${convertedHeight}")
            // Interpret the detected bits using FS003 protocol
            val result = AppSyncFs003Interpreter.interpret(bits, currentZoom())
            if (result != null) {
              Log.i("AppSyncScan", "✅ Scan successful! Weight: ${result.weight}, Fat: ${result.fat}, Muscle: ${result.muscle}")
              // Deliver the successful scan result
              onScanResult(result)
            } else {
              // Interpreter failed to process the detected bits
              Log.w("AppSyncScan", "⚠️ Pattern detected but interpreter returned null")
            }
          }
          // Don't log null results - it's normal for most frames
        } catch (e: Exception) {
          // Prevent crashes from native detector
          Log.e("AppSyncScan", "Error calling native detector: ${e.message}", e)
        }
      } else {
        Log.w("AppSyncScan", "Failed to convert YUV_420_888 to grayscale")
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
