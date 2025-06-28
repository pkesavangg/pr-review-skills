package com.greatergoods.libs.appsync.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dmdbrands.appsync.CameraHandlerCallback
import com.greatergoods.libs.appsync.R
import com.greatergoods.libs.appsync.config.AppSyncConstants
import com.greatergoods.libs.appsync.model.AppSyncResult
import com.greatergoods.libs.appsync.strings.AppSyncStrings
import com.greatergoods.libs.appsync.utility.AppSyncFs003Interpreter
import com.greatergoods.libs.appsync.utility.AppSyncResultFactory
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * Main Composable for the scan screen. Shows CameraX preview and overlays UI controls.
 * Calls [onResult] when a scan result (including cancel/manual) is available.
 * @param initialZoom The initial zoom level (default: 1)
 * @param showManualEntryButton Whether to show the manual entry button (default: true)
 */
@Composable
fun AppSyncScanScreen(
    initialZoom: Int = AppSyncConstants.DEFAULT_ZOOM,
    showManualEntryButton: Boolean = true,
    onResult: (AppSyncResult) -> Unit,
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { granted -> hasCameraPermission = granted },
        )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // CameraX state
    val cameraControlState = remember { mutableStateOf<CameraControl?>(null) }
    val cameraInfoState = remember { mutableStateOf<CameraInfo?>(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var zoomLevel by remember {
        mutableStateOf(
            initialZoom.toFloat().coerceIn(AppSyncConstants.MIN_ZOOM, AppSyncConstants.MAX_ZOOM),
        )
    }

    // UI state
    var scanResult by remember { mutableStateOf<AppSyncResult?>(null) }
    var resultHandled by remember { mutableStateOf(false) }
    var cameraReady by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var showResultTransition by remember { mutableStateOf(false) }

    // Cleanup camera executor when scan completes or composable is disposed
    DisposableEffect(resultHandled) {
        onDispose {
            if (resultHandled) {
                cameraExecutor.shutdown()
            }
        }
    }

    // Manual/cancel handlers using the factory
    val handleManualEntry = {
        if (!resultHandled) {
            val result = AppSyncResultFactory.createManualEntryResult(zoomLevel.toInt())
            scanResult = result
            resultHandled = true
            showResultTransition = true
            onResult(result)
        }
    }
    val handleCancel = {
        if (!resultHandled) {
            val result = AppSyncResultFactory.createCancelResult(zoomLevel.toInt())
            scanResult = result
            resultHandled = true
            showResultTransition = true
            onResult(result)
        }
    }

    // Update CameraX zoom when zoomLevel changes
    LaunchedEffect(zoomLevel, cameraControlState.value) {
        cameraControlState.value?.setLinearZoom(
            ((zoomLevel - AppSyncConstants.MIN_ZOOM) / (AppSyncConstants.MAX_ZOOM - AppSyncConstants.MIN_ZOOM))
                .coerceIn(0f, 1f),
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            cameraError != null -> {
                // Show error message if camera failed
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = cameraError!!, color = MaterialTheme.colorScheme.error)
                }
            }

            scanResult != null && showResultTransition -> {
                // Fade out transition before finishing
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(AppSyncStrings.ScanComplete, style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }

            hasCameraPermission -> {
                Log.i("CHECK", AppSyncStrings.Initializes1)
                CameraPreview(
                    onCameraReady = { camera, cameraControl, cameraInfo ->
                        cameraControlState.value = cameraControl
                        cameraInfoState.value = cameraInfo
                        cameraReady = true
                    },
                    cameraExecutor = cameraExecutor,
                    onScanResult = { result ->
                        if (!resultHandled) {
                            scanResult = result
                            resultHandled = true
                            showResultTransition = true
                            onResult(result)
                        }
                    },
                    onError = { errorMsg ->
                        cameraError = errorMsg
                    },
                )
                OverlayControls(
                    zoomLevel = zoomLevel.toInt(),
                    onZoomIn = { if (zoomLevel < AppSyncConstants.MAX_ZOOM) zoomLevel += AppSyncConstants.ZOOM_STEP },
                    onZoomOut = { if (zoomLevel > AppSyncConstants.MIN_ZOOM) zoomLevel -= AppSyncConstants.ZOOM_STEP },
                    onManualEntry = if (showManualEntryButton) handleManualEntry else null,
                    onClose = handleCancel,
                )
                if (!cameraReady) {
                    // Show loading indicator while camera is initializing
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column {
                            CircularProgressIndicator(
                                modifier = Modifier.semantics { contentDescription = AppSyncStrings.LoadingCamera },
                            )
                            Text("cameraReady $cameraReady")
                            Text("hasCameraPermission $hasCameraPermission")
                        }
                    }
                }
            }

            else -> {
                Text(AppSyncStrings.CameraPermissionRequired)
            }
        }
    }
}

/**
 * CameraX PreviewView inside Compose using AndroidView.
 * Notifies when camera is ready via onCameraReady callback.
 * Sets up ImageAnalysis for frame processing and calls onScanResult when a valid result is found.
 * Calls onError if camera initialization fails.
 */
@Composable
fun CameraPreview(
    onCameraReady: (Camera, CameraControl, CameraInfo) -> Unit,
    cameraExecutor: ExecutorService,
    onScanResult: (AppSyncResult) -> Unit,
    onError: (String) -> Unit = {},
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
                                processFrameWithJNI(imageProxy, onScanResult)
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
 */
private fun processFrameWithJNI(
    imageProxy: ImageProxy,
    onScanResult: (AppSyncResult) -> Unit,
) {
    try {
        if (imageProxy.format == ImageFormat.YUV_420_888) {
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

/**
 * Placeholder result screen for displaying scan results.
 */
@Composable
fun ResultScreen(result: AppSyncResult) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(AppSyncStrings.ScanCompletePlaceholder)
    }
}

/**
 * Overlay UI controls for scan screen: zoom, manual entry, close.
 * @param onManualEntry If null, manual entry button is hidden.
 */
@Composable
fun OverlayControls(
    zoomLevel: Int,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onManualEntry: (() -> Unit)? = null,
    onClose: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Close button (top right)
        IconButton(
            onClick = onClose,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .semantics { contentDescription = AppSyncStrings.CloseScan },
        ) {
            Icon(painter = painterResource(R.drawable.close), contentDescription = null, tint = Color.White)
        }
        // Zoom controls (bottom right)
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            horizontalAlignment = Alignment.End,
        ) {
            FloatingActionButton(
                onClick = onZoomIn,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.size(40.dp).semantics { contentDescription = AppSyncStrings.ZoomIn },
            ) {
                Icon(painter = painterResource(R.drawable.zoomincrease), contentDescription = null, tint = Color.White)
            }
            Spacer(modifier = Modifier.height(8.dp))
            FloatingActionButton(
                onClick = onZoomOut,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.size(40.dp).semantics { contentDescription = AppSyncStrings.ZoomOut },
            ) {
                Icon(painter = painterResource(R.drawable.zoomdecrease), contentDescription = null, tint = Color.White)
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (onManualEntry != null) {
                FloatingActionButton(
                    onClick = onManualEntry,
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = Color.White,
                    modifier = Modifier.size(40.dp).semantics { contentDescription = AppSyncStrings.ManualEntry },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.zoomdecrease),
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun AppSyncScanScreenPreviews() {
    AppSyncScanScreen { _ -> }
}
