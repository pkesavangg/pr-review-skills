package com.greatergoods.libs.appsync.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import kotlinx.coroutines.CoroutineScope

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
    val coroutineScope = rememberCoroutineScope()

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

    // Zoom state with animation
    var zoomLevel by remember { mutableStateOf(initialZoom.toFloat().coerceIn(AppSyncConstants.MIN_ZOOM, AppSyncConstants.MAX_ZOOM)) }
    var zoomManager by remember { mutableStateOf<AppSyncZoomManager?>(null) }

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
                zoomManager?.stopAnimation()
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

    // Update zoom manager when camera is ready
    LaunchedEffect(cameraControlState.value, cameraInfoState.value, coroutineScope) {
        if (cameraControlState.value != null && cameraInfoState.value != null) {
            zoomManager = AppSyncZoomManager(
                cameraControl = cameraControlState.value,
                cameraInfo = cameraInfoState.value,
                coroutineScope = coroutineScope
            )
            // Set initial zoom
            zoomManager?.setZoom(zoomLevel, animate = false)
        }
    }

    // Update zoom when zoomLevel changes
    LaunchedEffect(zoomLevel) {
        zoomManager?.setZoom(zoomLevel, animate = true)
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
 * Placeholder result screen for displaying scan results.
 */
@Composable
fun ResultScreen(result: AppSyncResult) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(AppSyncStrings.ScanCompletePlaceholder)
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun AppSyncScanScreenPreviews() {
    AppSyncScanScreen { _ -> }
}
