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
import androidx.compose.runtime.Stable
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
    val coroutineScope = rememberCoroutineScope()
    val state = rememberAppSyncScanState(initialZoom = initialZoom, onResult = onResult)

    // Request camera permission if not already granted
    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { granted -> state.hasCameraPermission = granted },
        )
    LaunchedEffect(Unit) {
        if (!state.hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Cleanup resources when scan completes or composable is disposed
    DisposableEffect(state.resultHandled) {
        onDispose {
            if (state.resultHandled) {
                state.cameraExecutor.shutdown()
                state.zoomManager?.stopAnimation()
            }
        }
    }

    // Handle back button press
    BackHandler {
        state.handleCancel()
    }

    // Wire up zoom manager creation and zoom level synchronization effects
    ZoomManagerEffects(
        cameraControl = state.cameraControl,
        cameraInfo = state.cameraInfo,
        coroutineScope = coroutineScope,
        zoomLevel = state.zoomLevel,
        zoomManager = state.zoomManager,
        onZoomManagerCreated = { state.zoomManager = it },
    )

    // Main UI layout
    ScanScreenContent(
        cameraError = state.cameraError,
        scanResult = state.scanResult,
        showResultTransition = state.showResultTransition,
        hasCameraPermission = state.hasCameraPermission,
        zoomLevel = state.zoomLevel,
        cameraReady = state.cameraReady,
        showLowLightWarning = state.showLowLightWarning,
        cameraExecutor = state.cameraExecutor,
        showManualEntryButton = showManualEntryButton,
        handleManualEntry = state::handleManualEntry,
        handleCancel = state::handleCancel,
        onCameraReady = { cameraControl, cameraInfo ->
            state.cameraControl = cameraControl
            state.cameraInfo = cameraInfo
            state.cameraReady = true
        },
        onScanResult = state::handleScanResult,
        onCameraError = { errorMsg -> state.cameraError = errorMsg },
        onLowLightDetected = { isLowLight -> state.showLowLightWarning = isLowLight },
        onZoomIn = state::zoomIn,
        onZoomOut = state::zoomOut,
    )
}

/**
 * Holder for the mutable UI state of [AppSyncScanScreen].
 *
 * Extracted from the screen composable so the parent stays small. All fields are
 * Compose snapshot state, so reads inside composition are observed exactly as the
 * original inline `var ... by remember { mutableStateOf(...) }` declarations were;
 * the result/zoom handlers reproduce the original logic verbatim.
 */
@Stable
private class AppSyncScanState(
    initialZoom: Int,
    val cameraExecutor: java.util.concurrent.ExecutorService,
    hasCameraPermission: Boolean,
    private val onResult: (AppSyncResult) -> Unit,
) {
    var hasCameraPermission by mutableStateOf(hasCameraPermission)
    var cameraControl by mutableStateOf<CameraControl?>(null)
    var cameraInfo by mutableStateOf<CameraInfo?>(null)
    var zoomManager by mutableStateOf<AppSyncZoomManager?>(null)
    var zoomLevel by mutableStateOf(
        initialZoom.toFloat().coerceIn(AppSyncConstants.MIN_ZOOM, AppSyncConstants.MAX_ZOOM),
    )
    var scanResult by mutableStateOf<AppSyncResult?>(null)
    var resultHandled by mutableStateOf(false)
    var cameraReady by mutableStateOf(false)
    var cameraError by mutableStateOf<String?>(null)
    var showResultTransition by mutableStateOf(false)
    var showLowLightWarning by mutableStateOf(false)

    private fun deliver(result: AppSyncResult) {
        if (!resultHandled) {
            scanResult = result
            resultHandled = true
            showResultTransition = true
            onResult(result)
        }
    }

    fun handleManualEntry() = deliver(AppSyncResultFactory.createManualEntryResult(zoomLevel.toInt()))

    fun handleCancel() = deliver(AppSyncResultFactory.createCancelResult(zoomLevel.toInt()))

    fun handleScanResult(result: AppSyncResult) = deliver(result)

    fun zoomIn() {
        if (zoomLevel < AppSyncConstants.MAX_ZOOM) {
            zoomLevel += AppSyncConstants.ZOOM_STEP
        }
    }

    fun zoomOut() {
        if (zoomLevel > AppSyncConstants.MIN_ZOOM) {
            zoomLevel -= AppSyncConstants.ZOOM_STEP
        }
    }
}

/**
 * Creates and remembers the [AppSyncScanState] for [AppSyncScanScreen].
 */
@Composable
private fun rememberAppSyncScanState(
    initialZoom: Int,
    onResult: (AppSyncResult) -> Unit,
): AppSyncScanState {
    val context = LocalContext.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    return remember {
        AppSyncScanState(
            initialZoom = initialZoom,
            cameraExecutor = cameraExecutor,
            hasCameraPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED,
            onResult = onResult,
        )
    }
}

/**
 * Top-level UI dispatcher for [AppSyncScanScreen]. Selects between the error,
 * completion, scanning, and permission-required states. Extracted to keep the
 * parent composable concise; rendered output and branching are unchanged.
 */
@Composable
private fun ScanScreenContent(
    cameraError: String?,
    scanResult: AppSyncResult?,
    showResultTransition: Boolean,
    hasCameraPermission: Boolean,
    zoomLevel: Float,
    cameraReady: Boolean,
    showLowLightWarning: Boolean,
    cameraExecutor: java.util.concurrent.ExecutorService,
    showManualEntryButton: Boolean,
    handleManualEntry: () -> Unit,
    handleCancel: () -> Unit,
    onCameraReady: (CameraControl?, CameraInfo?) -> Unit,
    onScanResult: (AppSyncResult) -> Unit,
    onCameraError: (String) -> Unit,
    onLowLightDetected: (Boolean) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        when {
            // Show error message if camera initialization failed
            cameraError != null -> {
                CameraErrorContent(error = cameraError)
            }

            // Show completion transition when scan finishes
            scanResult != null && showResultTransition -> {
                ScanCompleteContent()
            }

            // Main scanning interface when camera permission is granted
            hasCameraPermission -> {
                CameraScanContent(
                    zoomLevel = zoomLevel,
                    cameraReady = cameraReady,
                    hasCameraPermission = hasCameraPermission,
                    showLowLightWarning = showLowLightWarning,
                    cameraExecutor = cameraExecutor,
                    showManualEntryButton = showManualEntryButton,
                    handleManualEntry = handleManualEntry,
                    handleCancel = handleCancel,
                    onCameraReady = onCameraReady,
                    onScanResult = onScanResult,
                    onCameraError = onCameraError,
                    onLowLightDetected = onLowLightDetected,
                    onZoomIn = onZoomIn,
                    onZoomOut = onZoomOut,
                )
            }

            // Show permission request message when camera permission is not granted
            else -> {
                Text(AppSyncStrings.CameraPermissionRequired)
            }
        }
    }
}

/**
 * Error state shown when camera initialization fails.
 * Extracted from [AppSyncScanScreen]; rendered output is unchanged.
 */
@Composable
private fun CameraErrorContent(error: String?) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = error ?: "", color = MaterialTheme.colorScheme.error)
    }
}

/**
 * Completion transition shown when the scan finishes.
 * Extracted from [AppSyncScanScreen]; rendered output is unchanged.
 */
@Composable
private fun ScanCompleteContent() {
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

/**
 * Creates the [AppSyncZoomManager] once the camera is ready and keeps the camera
 * zoom synchronized with [zoomLevel]. Extracted from [AppSyncScanScreen]; the
 * effect keys and behavior are unchanged.
 */
@Composable
private fun ZoomManagerEffects(
    cameraControl: CameraControl?,
    cameraInfo: CameraInfo?,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    zoomLevel: Float,
    zoomManager: AppSyncZoomManager?,
    onZoomManagerCreated: (AppSyncZoomManager) -> Unit,
) {
    // Initialize zoom manager when camera is ready
    LaunchedEffect(cameraControl, cameraInfo, coroutineScope) {
        if (cameraControl != null && cameraInfo != null) {
            val manager =
                AppSyncZoomManager(
                    cameraControl = cameraControl,
                    cameraInfo = cameraInfo,
                    coroutineScope = coroutineScope,
                )
            onZoomManagerCreated(manager)
            // Set initial zoom without animation
            manager.setZoom(zoomLevel, animate = false)
        }
    }

    // Update camera zoom when zoom level changes
    LaunchedEffect(zoomLevel) {
        zoomManager?.setZoom(zoomLevel, animate = true)
    }
}

/**
 * Camera scanning interface shown when camera permission has been granted.
 *
 * Renders the camera preview, overlay controls, and the loading indicator while
 * the camera initializes. Extracted from [AppSyncScanScreen] to keep the parent
 * composable concise; behavior and rendered UI are unchanged.
 */
@Composable
private fun CameraScanContent(
    zoomLevel: Float,
    cameraReady: Boolean,
    hasCameraPermission: Boolean,
    showLowLightWarning: Boolean,
    cameraExecutor: java.util.concurrent.ExecutorService,
    showManualEntryButton: Boolean,
    handleManualEntry: () -> Unit,
    handleCancel: () -> Unit,
    onCameraReady: (CameraControl?, CameraInfo?) -> Unit,
    onScanResult: (AppSyncResult) -> Unit,
    onCameraError: (String) -> Unit,
    onLowLightDetected: (Boolean) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
) {
    // Camera preview component with callbacks
    CameraPreview(
        onCameraReady = { _, cameraControl, cameraInfo ->
            onCameraReady(cameraControl, cameraInfo)
        },
        cameraExecutor = cameraExecutor,
        onScanResult = onScanResult,
        currentZoom = { zoomLevel.toInt() },
        onError = onCameraError,
        onLowLightDetected = onLowLightDetected,
    )

    // Overlay controls for user interaction
    OverlayControls(
        zoomLevel = zoomLevel,
        showLowLightWarning = showLowLightWarning,
        onZoomIn = onZoomIn,
        onZoomOut = onZoomOut,
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

/**
 * Placeholder result screen for displaying scan results.
 *
 * This composable is a simple placeholder that can be used to display
 * scan results in a separate screen. Currently, shows a basic completion message.
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
