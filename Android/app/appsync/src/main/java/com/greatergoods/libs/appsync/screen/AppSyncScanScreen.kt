package com.greatergoods.libs.appsync.screen

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import com.greatergoods.libs.appsync.config.AppSyncConstants
import com.greatergoods.libs.appsync.model.AppSyncResult
import com.greatergoods.libs.appsync.screen.components.CameraPreview
import com.greatergoods.libs.appsync.screen.components.OverlayControls
import com.greatergoods.libs.appsync.strings.AppSyncStrings
import com.greatergoods.libs.appsync.utility.AppSyncResultFactory
import com.greatergoods.libs.appsync.utility.AppSyncZoomManager
import java.util.concurrent.Executors
import android.Manifest
import android.content.pm.PackageManager
import android.util.Log

/**
 * Main Composable for the AppSync scan screen.
 *
 * This composable provides the complete camera-based scanning interface, including:
 * - Camera preview with real-time image analysis
 * - Permission handling for camera access
 * - Zoom controls with smooth animations
 * - Overlay controls for user interaction
 * - Error handling and status display
 * - Result delivery to the parent component
 *
 * The composable manages its own state for camera permissions, zoom levels,
 * scan results, and UI transitions. It automatically requests camera permissions
 * if not already granted and provides appropriate feedback to the user.
 *
 * @param initialZoom The initial zoom level for the camera. Must be between
 *                    [AppSyncConstants.MIN_ZOOM] and [AppSyncConstants.MAX_ZOOM].
 *                    Defaults to [AppSyncConstants.DEFAULT_ZOOM].
 * @param showManualEntryButton Whether to show the manual entry button in the
 *                              overlay controls. When true, users can choose to
 *                              manually enter data instead of scanning. Defaults to true.
 * @param onResult Callback function that receives the scan result. This is called
 *                 when the scan completes (successfully or with errors), when the
 *                 user cancels, or when manual entry is selected.
 */
@Composable
fun AppSyncScanScreen(
    initialZoom: Int = AppSyncConstants.DEFAULT_ZOOM,
    showManualEntryButton: Boolean = true,
    onResult: (AppSyncResult) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Camera permission state and launcher
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

    // Request camera permission if not already granted
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // CameraX state management
    val cameraControlState = remember { mutableStateOf<CameraControl?>(null) }
    val cameraInfoState = remember { mutableStateOf<CameraInfo?>(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // Zoom state with animation support
    var zoomLevel by remember {
        mutableStateOf(
            initialZoom.toFloat().coerceIn(AppSyncConstants.MIN_ZOOM, AppSyncConstants.MAX_ZOOM),
        )
    }
    var zoomManager by remember { mutableStateOf<AppSyncZoomManager?>(null) }

    // UI state management
    var scanResult by remember { mutableStateOf<AppSyncResult?>(null) }
    var resultHandled by remember { mutableStateOf(false) }
    var cameraReady by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var showResultTransition by remember { mutableStateOf(false) }
    var showLowLightWarning by remember { mutableStateOf(false) }

    // Cleanup resources when scan completes or composable is disposed
    DisposableEffect(resultHandled) {
        onDispose {
            if (resultHandled) {
                cameraExecutor.shutdown()
                zoomManager?.stopAnimation()
            }
        }
    }

    // Manual entry handler - creates a manual entry result and delivers it
    val handleManualEntry = {
        if (!resultHandled) {
            val result = AppSyncResultFactory.createManualEntryResult(zoomLevel.toInt())
            scanResult = result
            resultHandled = true
            showResultTransition = true
            onResult(result)
        }
    }

    // Cancel handler - creates a cancel result and delivers it
    val handleCancel = {
        if (!resultHandled) {
            val result = AppSyncResultFactory.createCancelResult(zoomLevel.toInt())
            scanResult = result
            resultHandled = true
            showResultTransition = true
            onResult(result)
        }
    }

    // Handle back button press
    BackHandler {
        handleCancel()
    }

    // Initialize zoom manager when camera is ready
    LaunchedEffect(cameraControlState.value, cameraInfoState.value, coroutineScope) {
        if (cameraControlState.value != null && cameraInfoState.value != null) {
            zoomManager =
                AppSyncZoomManager(
                    cameraControl = cameraControlState.value,
                    cameraInfo = cameraInfoState.value,
                    coroutineScope = coroutineScope,
                )
            // Set initial zoom without animation
            zoomManager?.setZoom(zoomLevel, animate = false)
        }
    }

    // Update camera zoom when zoom level changes
    LaunchedEffect(zoomLevel) {
        zoomManager?.setZoom(zoomLevel, animate = true)
    }

    // Main UI layout
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        when {
            // Show error message if camera initialization failed
            cameraError != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = cameraError!!, color = MaterialTheme.colorScheme.error)
                }
            }

            // Show completion transition when scan finishes
            scanResult != null && showResultTransition -> {
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

            // Main scanning interface when camera permission is granted
            hasCameraPermission -> {
                // Camera preview component with callbacks
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
                    currentZoom = { zoomLevel.toInt() },
                    onError = { errorMsg ->
                        cameraError = errorMsg
                    },
                    onLowLightDetected = { isLowLight ->
                        showLowLightWarning = isLowLight
                    },
                )

                // Overlay controls for user interaction
                OverlayControls(
                    zoomLevel = zoomLevel,
                    showLowLightWarning = showLowLightWarning,
                    onZoomIn = {
                        if (zoomLevel < AppSyncConstants.MAX_ZOOM) {
                            zoomLevel += AppSyncConstants.ZOOM_STEP
                        }
                    },
                    onZoomOut = {
                        if (zoomLevel > AppSyncConstants.MIN_ZOOM) {
                            zoomLevel -= AppSyncConstants.ZOOM_STEP
                        }
                    },
                    onManualEntry = if (showManualEntryButton) handleManualEntry else null,
                    onClose = handleCancel,
                )

                // Loading indicator while camera initializes
                if (!cameraReady) {
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

            // Show permission request message when camera permission is not granted
            else -> {
                Text(AppSyncStrings.CameraPermissionRequired)
            }
        }
    }
}

/**
 * Placeholder result screen for displaying scan results.
 *
 * This composable is a simple placeholder that can be used to display
 * scan results in a separate screen. Currently shows a basic completion message.
 *
 * @param result The scan result to display
 */
@Composable
fun ResultScreen(result: AppSyncResult) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(AppSyncStrings.ScanCompletePlaceholder)
    }
}

/**
 * Preview composable for the AppSyncScanScreen.
 *
 * This preview is used for development and testing purposes to visualize
 * the scan screen in Android Studio's preview pane.
 */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun AppSyncScanScreenPreviews() {
    AppSyncScanScreen { _ -> }
}
