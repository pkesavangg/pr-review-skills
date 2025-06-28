package com.greatergoods.libs.appsync

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
    initialZoom: Int = 1,
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
    var zoomLevel by remember { mutableStateOf(initialZoom.toFloat().coerceIn(1.0f, 5.0f)) }
    val minZoom = 1.0f
    val maxZoom = 5.0f
    val zoomStep = 0.1f

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

    // Manual/cancel handlers
    val handleManualEntry = {
        if (!resultHandled) {
            val result =
                AppSyncResult(
                    weight = null,
                    fat = null,
                    muscle = null,
                    water = null,
                    mode = null,
                    canceled = false,
                    manual = true,
                )
            scanResult = result
            resultHandled = true
            showResultTransition = true
            onResult(result)
        }
    }
    val handleCancel = {
        if (!resultHandled) {
            val result =
                AppSyncResult(
                    weight = null,
                    fat = null,
                    muscle = null,
                    water = null,
                    mode = null,
                    canceled = true,
                    manual = false,
                )
            scanResult = result
            resultHandled = true
            showResultTransition = true
            onResult(result)
        }
    }

    // Update CameraX zoom when zoomLevel changes
    LaunchedEffect(zoomLevel, cameraControlState.value) {
        cameraControlState.value?.setLinearZoom(((zoomLevel - minZoom) / (maxZoom - minZoom)).coerceIn(0f, 1f))
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
                        Text("Scan complete!", style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }

            hasCameraPermission -> {
                Log.i("CHECK", "Initializes 1")
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
                    onZoomIn = { if (zoomLevel < maxZoom) zoomLevel += zoomStep },
                    onZoomOut = { if (zoomLevel > minZoom) zoomLevel -= zoomStep },
                    onManualEntry = if (showManualEntryButton) handleManualEntry else null,
                    onClose = handleCancel,
                )
                if (!cameraReady) {
                    // Show loading indicator while camera is initializing
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column {
                            CircularProgressIndicator(
                                modifier = Modifier.semantics { contentDescription = "Loading camera" },
                            )
                            Text("cameraReady $cameraReady")
                            Text("hasCameraPermission $hasCameraPermission")
                        }
                    }
                }
            }

            else -> {
                Text("Camera permission required")
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
    Log.i("CHECK", "Initializes")
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
                        Log.e("AppSyncScan", "Camera binding failed", exc)
                        onError("Camera initialization failed. Please try again.")
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
 * Calls JniBridge.nativeDetector and handles the result.
 * Decodes the result using fs003Interpreter logic (ported from Java).
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
            // Call native detector
            val bits = com.dmdbrands.appsync.CameraHandlerCallback.nativeDetector(yBytes, width, height)
            if (bits != null && bits.isNotEmpty()) {
                val result = interpretFs003(bits)
                if (result != null) {
                    onScanResult(result)
                } else {
                    Log.w("AppSyncScan", "Interpreter returned null (invalid scan)")
                }
            } else {
                // Native detector failed or returned no result; handle gracefully (e.g., log, skip, or retry)
                Log.w("AppSyncScan", "Native detector returned null or empty result")
            }
        }
    } catch (e: Exception) {
        Log.e("AppSyncScan", "JNI frame processing failed", e)
    } finally {
        imageProxy.close()
    }
}

/**
 * Ported fs003Interpreter logic from Java to Kotlin.
 * Decodes the bit array from the native detector into AppSyncResult fields.
 */
private fun interpretFs003(bits: IntArray): AppSyncResult? {
    // Hamming decode: bits[] -> data[] (4/7 the length)
    val data = IntArray(10)
    val errorsFound = IntArray(10)
    var j = 0
    var i = 0
    while (j < 10 && i + 7 <= bits.size) {
        var ham = 0
        val start = i
        for (k in 0 until 7) {
            ham = ham or (((if (i < bits.size && bits[i] == 1) 1 else 0) shl k))
            i++
        }
        data[j] = Hammer.extractData(ham)
        errorsFound[j] = if (Hammer.errorFoundInLastCorrection) 1 else 0
        j++
    }
    // Extract values as in Java
    // Weight
    var weight = data[2]
    weight = weight or (data[1] shl 4)
    weight = weight or (data[0] shl 8)
    weight = weight ushr 1
    val weightValue = weight * 1f / 10f
    val weightErrors = errorsFound[1] + errorsFound[2] + errorsFound[0]
    // Fat
    var fat = data[5]
    fat = fat or (data[4] shl 4)
    fat = fat or (data[3] shl 8)
    fat = fat or (data[2] shl 12)
    fat = fat ushr 3
    fat = fat and 0x3ff
    var fatValue = fat * 1f / 10f
    if (fat == 0x3ff || fat == 0) fatValue = -1f
    val fatErrors = errorsFound[2] + errorsFound[3] + errorsFound[4] + errorsFound[5]
    // Muscle
    var muscle = data[7]
    muscle = muscle or (data[6] shl 4)
    muscle = muscle or (data[5] shl 8)
    muscle = muscle ushr 2
    muscle = muscle and 0x1ff
    var muscleValue = 14.9f + muscle * 1f / 10f
    if (muscle == 0x1ff || muscle == 0) muscleValue = -1f
    val muscleErrors = errorsFound[7] + errorsFound[6] + errorsFound[5]
    // Water
    var water = data[9] shr 1
    water = water or (data[8] shl 1)
    water = water or (data[7] shl 5)
    water = water and 0x7f
    var waterValue = 18f + water.toFloat() / 2f
    if (water == 0x7f || water == 0) waterValue = -1f
    val waterErrors = errorsFound[9] + errorsFound[8] + errorsFound[7]
    // Mode
    val unit = data[9] and 0x1
    val modes = arrayOf("lb", "kg")
    val modeStr = modes.getOrNull(unit)
    val modeErrors = errorsFound[9]
    // Total errors
    var totalErrors = errorsFound.sum()
    if (weightValue == 0.0f && fatValue == 0.0f && muscleValue == 14.9f) {
        totalErrors = 77
    }
    // If all values are invalid, treat as no result
    if (weightValue == 0.0f && fatValue == -1f && muscleValue == -1f && waterValue == -1f) {
        return null
    }
    return AppSyncResult(
        weight = if (weightValue > 0) weightValue else null,
        fat = if (fatValue > 0) fatValue else null,
        muscle = if (muscleValue > 0) muscleValue else null,
        water = if (waterValue > 0) waterValue else null,
        mode = modeStr,
        weightErrors = weightErrors,
        fatErrors = fatErrors,
        muscleErrors = muscleErrors,
        waterErrors = waterErrors,
        modeErrors = modeErrors,
        errors = totalErrors,
        zoom = 1,
        canceled = false,
        manual = false,
    )
}

private object Hammer {
    var errorFoundInLastCorrection: Boolean = false
    private val H = intArrayOf(0x55, 0x66, 0x78)
    private val extractionMatrix = intArrayOf(0x04, 0x10, 0x20, 0x40)
    fun extractData(input: Int): Int {
        val corrected = correctErrors(input)
        return multiply(extractionMatrix, corrected)
    }
    private fun correctErrors(input: Int): Int {
        var inputVar = input
        val check = multiply(H, inputVar)
        if (check != 0) inputVar = inputVar xor (1 shl (check - 1))
        errorFoundInLastCorrection = (check != 0)
        return inputVar
    }
    private fun multiply(matrix: IntArray, vector: Int): Int {
        var ret = 0
        for (i in matrix.indices) {
            if (parity(vector and matrix[i])) ret = ret or (1 shl i)
        }
        return ret
    }
    private fun parity(n: Int): Boolean {
        var nVar = n
        var parity = false
        while (nVar != 0) {
            parity = !parity
            nVar = nVar and (nVar - 1)
        }
        return parity
    }
}

private fun Int.toFloatOrNull(): Float? =
    try {
        this.toFloat()
    } catch (_: Exception) {
        null
    }

/**
 * Placeholder result screen for displaying scan results.
 */
@Composable
fun ResultScreen(result: AppSyncResult) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Scan complete! (Result placeholder)")
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
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).semantics { contentDescription = "Close scan" },
        ) {
            // Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
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
                modifier = Modifier.size(40.dp).semantics { contentDescription = "Zoom in" },
            ) {
                // Icon(Icons.Default.Add, contentDescription = null)
                Icon(painter = painterResource(R.drawable.zoomincrease), contentDescription = null, tint = Color.White)
            }
            Spacer(modifier = Modifier.height(8.dp))
            FloatingActionButton(
                onClick = onZoomOut,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.size(40.dp).semantics { contentDescription = "Zoom out" },
            ) {
                // Icon(Icons.Default.Edit, contentDescription = null)
                Icon(painter = painterResource(R.drawable.zoomdecrease), contentDescription = null, tint = Color.White)
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (onManualEntry != null) {
                FloatingActionButton(
                    onClick = onManualEntry,
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = Color.White,
                    modifier = Modifier.size(40.dp).semantics { contentDescription = "Manual entry" },
                ) {
                    // Icon(Icons.Default.Edit, contentDescription = null)
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
