package com.greatergoods.libs.appsync.screen.components

import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dmdbrands.appsync.CameraHandlerCallback
import com.greatergoods.libs.appsync.AppSyncLogger
import com.greatergoods.libs.appsync.config.AppSyncConstants
import com.greatergoods.libs.appsync.model.AppSyncResult
import com.greatergoods.libs.appsync.strings.AppSyncStrings
import com.greatergoods.libs.appsync.utility.AppSyncFs003Interpreter
import com.greatergoods.libs.appsync.utility.AppSyncLowLightDetector
import com.greatergoods.libs.appsync.utility.YUV420888ToGrayscaleConverter
import java.util.concurrent.ExecutorService
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.MeteringRectangle
import android.view.ViewGroup
import android.widget.FrameLayout

private const val TAG = "AppSyncScan"

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
        val previewView =
          PreviewView(ctx).apply {
            layoutParams =
              FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
              )
          }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
        cameraProviderFuture.addListener(
          {
            bindCameraUseCases(
              ctx = ctx,
              previewView = previewView,
              lifecycleOwner = lifecycleOwner,
              cameraProvider = cameraProviderFuture.get(),
              cameraExecutor = cameraExecutor,
              onScanResult = onScanResult,
              onLowLightDetected = onLowLightDetected,
              currentZoom = currentZoom,
              onCameraReady = onCameraReady,
              onError = onError,
            )
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
 * Builds and binds the preview + image-analysis use cases to the lifecycle.
 *
 * The preview is configured for continuous, centre-weighted autofocus/exposure via
 * Camera2 (MOB-869): the scale display is small and bright, and the camera binds the
 * instant the scan screen opens — before the user has aimed. Relying on the default 3A
 * let focus/exposure settle on the background, leaving the display soft; a blurry pattern
 * fails to decode, so the scan only worked sometimes and needed several attempts.
 * [applyContinuousCenterFocus] keeps the HAL refocusing on the display for the whole
 * session. Binding failures are surfaced via [onError]; scanning then can't proceed.
 */
private fun bindCameraUseCases(
  ctx: Context,
  previewView: PreviewView,
  lifecycleOwner: LifecycleOwner,
  cameraProvider: ProcessCameraProvider,
  cameraExecutor: ExecutorService,
  onScanResult: (AppSyncResult) -> Unit,
  onLowLightDetected: (Boolean) -> Unit,
  currentZoom: () -> Int,
  onCameraReady: (Camera, CameraControl, CameraInfo) -> Unit,
  onError: (String) -> Unit,
) {
  try {
    val previewBuilder = Preview.Builder()
    applyContinuousCenterFocus(previewBuilder, ctx)
    val preview =
      previewBuilder.build().also {
        it.surfaceProvider = previewView.surfaceProvider
      }

    // 1280x720 is a good balance — high enough for FS003 detection, works with zoom.
    // ResolutionSelector replaces the deprecated setTargetResolution: it makes the
    // fallback rule explicit (closest higher, then lower) and pins the 16:9 aspect ratio
    // so frames don't get reshaped to the sensor's default 4:3.
    val resolutionSelector =
      ResolutionSelector.Builder()
        .setResolutionStrategy(
          ResolutionStrategy(
            android.util.Size(1280, 720),
            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
          ),
        )
        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
        .build()
    val imageAnalyzer =
      ImageAnalysis.Builder()
        .setResolutionSelector(resolutionSelector)
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setTargetRotation(previewView.display.rotation)
        .build()
    imageAnalyzer.setAnalyzer(cameraExecutor) { imageProxy ->
      processFrameWithJNI(imageProxy, onScanResult, onLowLightDetected, currentZoom)
    }

    cameraProvider.unbindAll()
    val camera =
      cameraProvider.bindToLifecycle(
        lifecycleOwner,
        CameraSelector.DEFAULT_BACK_CAMERA,
        preview,
        imageAnalyzer,
      )
    onCameraReady(camera, camera.cameraControl, camera.cameraInfo)
  } catch (exc: Exception) {
    AppSyncLogger.e(TAG, AppSyncStrings.CameraBindingFailed, exc)
    onError(AppSyncStrings.CameraInitializationFailed)
  }
}

/**
 * Configures the camera for continuous, centre-weighted autofocus and auto-exposure via
 * the Camera2 interop layer, so the scale display stays sharp throughout the scan.
 *
 * The FS003 pattern is small and bright; relying on CameraX's default 3A can let the
 * camera settle focus/exposure on the background, leaving the display soft, and a blurry
 * pattern fails to decode — the intermittent "needs several attempts / only works when
 * held perfectly still" behaviour reported in MOB-869 / MOB-392.
 *
 * This sets [CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE] so the HAL keeps
 * refocusing for the whole session (no timer, no manual re-trigger), and pins AF/AE
 * metering regions to the centre of the sensor where the display is framed. Centre
 * regions remain correct under digital zoom. All of this is best-effort — if the device
 * ignores regions or the interop call fails, scanning continues with default 3A.
 *
 * @param builder The [Preview.Builder] whose session capture requests are extended.
 * @param context Used to read the back camera's sensor geometry for the metering region.
 */
@OptIn(ExperimentalCamera2Interop::class)
internal fun applyContinuousCenterFocus(
  builder: Preview.Builder,
  context: Context,
) {
  try {
    val extender = Camera2Interop.Extender(builder)
    extender.setCaptureRequestOption(
      CaptureRequest.CONTROL_AF_MODE,
      CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
    )
    val region = centerMeteringRegion(context)
    if (region != null) {
      val regions = arrayOf(region)
      extender.setCaptureRequestOption(CaptureRequest.CONTROL_AF_REGIONS, regions)
      extender.setCaptureRequestOption(CaptureRequest.CONTROL_AE_REGIONS, regions)
    }
  } catch (e: Exception) {
    // Non-fatal: scanning continues with the camera's default 3A behaviour.
    AppSyncLogger.w(TAG, "Continuous center focus setup failed: ${e.message}")
  }
}

/**
 * Builds a centre metering rectangle (the middle region of the sensor active array) for
 * the back camera, or null if the geometry/camera can't be resolved.
 *
 * The rectangle covers the central [AppSyncConstants.CENTER_REGION_HALF_FRACTION] of the
 * active array on each axis, which is where the targeting overlay frames the scale display.
 */
private fun centerMeteringRegion(context: Context): MeteringRectangle? =
  try {
    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    val backCameraId =
      cameraManager?.cameraIdList?.firstOrNull { id ->
        cameraManager.getCameraCharacteristics(id)
          .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
      }
    val activeArray =
      backCameraId
        ?.let { cameraManager.getCameraCharacteristics(it) }
        ?.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
    if (activeArray == null) {
      null
    } else {
      val (x, y, w, h) =
        centerRegionBounds(
          width = activeArray.width(),
          height = activeArray.height(),
          left = activeArray.left,
          top = activeArray.top,
          halfFraction = AppSyncConstants.CENTER_REGION_HALF_FRACTION,
        )
      MeteringRectangle(x, y, w, h, MeteringRectangle.METERING_WEIGHT_MAX)
    }
  } catch (e: Exception) {
    AppSyncLogger.w(TAG, "Could not resolve center metering region: ${e.message}")
    null
  }

/**
 * Computes the centre metering rectangle bounds within a sensor active array, as
 * `[x, y, width, height]`.
 *
 * Pure integer geometry (no Android types) so it is unit-testable. The rectangle is
 * centred in the active array and spans [halfFraction] of each axis on either side of
 * centre, clamped so it never starts before the array origin.
 */
internal fun centerRegionBounds(
  width: Int,
  height: Int,
  left: Int,
  top: Int,
  halfFraction: Float,
): IntArray {
  val halfWidth = (width * halfFraction).toInt()
  val halfHeight = (height * halfFraction).toInt()
  val centerX = left + width / 2
  val centerY = top + height / 2
  return intArrayOf(
    (centerX - halfWidth).coerceAtLeast(left),
    (centerY - halfHeight).coerceAtLeast(top),
    halfWidth * 2,
    halfHeight * 2,
  )
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
        AppSyncLogger.d(TAG, "Skipping frame: resolution too low (${width}x${height})")
        return
      }

      // Convert YUV_420_888 to grayscale using proper stride handling
      // This works at any resolution/zoom level
      val conversionResult = YUV420888ToGrayscaleConverter.convertToGrayscale(imageProxy)

      if (conversionResult != null) {
        val (grayscaleData, convertedWidth) = conversionResult

        // Validate data before processing
        if (grayscaleData.isEmpty() || convertedWidth <= 0 || height <= 0) {
          AppSyncLogger.w(TAG, "Invalid converted data: size=${grayscaleData.size}, dimensions=${convertedWidth}x$height")
          return
        }

        // Check for low light conditions using the converted luminance data
        val isLowLight = AppSyncLowLightDetector.isLowLight(grayscaleData, convertedWidth, height)
        onLowLightDetected(isLowLight)

        // Rotate the frame so the FS003 pattern is upright before handing it to the
        // native detector. Without this the decoder receives a sideways buffer on
        // tablets / portrait-held phones / OEMs that ignore the landscape lock, and
        // the scan callback never fires. Dimensions swap for 90/270.
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val (rotatedData, rotatedWidth, rotatedHeight) = when (rotationDegrees) {
          90, 270 -> Triple(
            YUV420888ToGrayscaleConverter.applyRotationAndMirror(
              grayscaleData, convertedWidth, height, rotationDegrees, mirror = false,
            ),
            height,
            convertedWidth,
          )
          180 -> Triple(
            YUV420888ToGrayscaleConverter.applyRotationAndMirror(
              grayscaleData, convertedWidth, height, 180, mirror = false,
            ),
            convertedWidth,
            height,
          )
          else -> Triple(grayscaleData, convertedWidth, height)
        }

        // Hand the upright frame to the native FS003 detector and deliver any result.
        runNativeDetector(rotatedData, rotatedWidth, rotatedHeight, rotationDegrees, currentZoom, onScanResult)
      } else {
        AppSyncLogger.w(TAG, "Failed to convert YUV_420_888 to grayscale")
      }
    }
  } catch (e: Exception) {
    // Log any errors that occur during frame processing
    AppSyncLogger.e(TAG, AppSyncStrings.JniFrameProcessingFailed, e)
  } finally {
    // Always close the image proxy to release resources
    imageProxy.close()
  }
}

/**
 * Runs the native FS003 detector on an upright grayscale frame and, when a pattern is
 * found and interpreted, delivers the result via [onScanResult]. Null detections are
 * normal for most frames and are not logged. Exceptions from the native layer are
 * swallowed so a single bad frame can never crash the analyzer.
 */
private fun runNativeDetector(
  data: ByteArray,
  width: Int,
  height: Int,
  rotationDegrees: Int,
  currentZoom: () -> Int,
  onScanResult: (AppSyncResult) -> Unit,
) {
  try {
    val bits = CameraHandlerCallback.nativeDetector(data, width, height)
    if (bits != null && bits.isNotEmpty()) {
      AppSyncLogger.d(
        TAG,
        "Pattern detected: bits=${bits.size}, resolution=${width}x$height, rotation=$rotationDegrees",
      )
      val result = AppSyncFs003Interpreter.interpret(bits, currentZoom())
      if (result != null) {
        AppSyncLogger.i(
          TAG,
          "Scan successful: weight=${result.weight}, fat=${result.fat}, muscle=${result.muscle}",
        )
        onScanResult(result)
      } else {
        AppSyncLogger.w(TAG, "Pattern detected but interpreter returned null")
      }
    }
  } catch (e: Exception) {
    AppSyncLogger.e(TAG, "Error calling native detector: ${e.message}", e)
  }
}
