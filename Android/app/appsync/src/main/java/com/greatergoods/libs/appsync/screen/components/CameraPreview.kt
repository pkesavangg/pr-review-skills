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
 * CameraX PreviewView inside Compose using AndroidView.
 * Notifies when camera is ready via onCameraReady callback.
 * Sets up ImageAnalysis for frame processing and calls onScanResult when a valid result is found.
 * Calls onError if camera initialization fails.
 * Detects low light conditions and notifies via onLowLightDetected callback.
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
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview =
                            Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
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
}

/**
 * Processes a camera frame using JNI/native detector.
 * Only processes YUV_420_888 format, converts to grayscale ByteArray.
 * Calls AppSyncJniBridge.nativeDetector and handles the result.
 * Decodes the result using AppSyncFs003Interpreter.
 * Calls onScanResult with a valid AppSyncResult.
 * Detects low light conditions and calls onLowLightDetected.
 */
private fun processFrameWithJNI(
    imageProxy: ImageProxy,
    onScanResult: (AppSyncResult) -> Unit,
    onLowLightDetected: (Boolean) -> Unit,
) {
    try {
        if (imageProxy.format == ImageFormat.YUV_420_888) {
            // Check for low light conditions
            val isLowLight = AppSyncLowLightDetector.isLowLight(imageProxy)
            onLowLightDetected(isLowLight)

            val yBuffer = imageProxy.planes[0].buffer
            val width = imageProxy.width
            val height = imageProxy.height
            val ySize = yBuffer.remaining()
            val yBytes = ByteArray(ySize)
            yBuffer.get(yBytes)

            // Call native detector using the unified bridge
            val bits = CameraHandlerCallback.nativeDetector(yBytes, width, height)
            if (bits != null && bits.isNotEmpty()) {
                val result = AppSyncFs003Interpreter.interpret(bits)
                if (result != null) {
                    onScanResult(result)
                } else {
                    Log.w("AppSyncScan", AppSyncStrings.InterpreterReturnedNull)
                }
            } else {
                // Native detector failed or returned no result; handle gracefully
                Log.w("AppSyncScan", AppSyncStrings.NativeDetectorReturnedNull)
            }
        }
    } catch (e: Exception) {
        Log.e("AppSyncScan", AppSyncStrings.JniFrameProcessingFailed, e)
    } finally {
        imageProxy.close()
    }
}
