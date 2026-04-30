package com.greatergoods.libs.appsync.utility

import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import com.greatergoods.libs.appsync.config.AppSyncConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Manages smooth camera zoom animations for the AppSync scanning interface.
 *
 * This class provides animated zoom transitions instead of instant zoom changes,
 * creating a more polished user experience. It handles the mapping between
 * the app's zoom levels (1.0-5.0) and the camera's zoom ratios (0.0-1.0).
 *
 * The zoom manager includes features such as:
 * - Smooth animation between zoom levels
 * - Threshold-based change detection to avoid unnecessary updates
 * - Proper initialization handling
 * - Animation state management
 * - Coroutine-based animation execution
 *
 * The manager uses the CameraX [CameraControl] to apply zoom changes and
 * provides a clean interface for the UI components to control zoom behavior.
 *
 * @param cameraControl CameraX control object for applying zoom changes
 * @param cameraInfo CameraX info object for camera capabilities
 * @param coroutineScope Coroutine scope for managing zoom animations
 */
class AppSyncZoomManager(
    private val cameraControl: CameraControl?,
    private val cameraInfo: CameraInfo?,
    private val coroutineScope: CoroutineScope,
) {
    private var currentZoom = AppSyncConstants.MIN_ZOOM
    private var targetZoom = AppSyncConstants.MIN_ZOOM
    private var isAnimating = false
    private var isInitialized = false

    /**
     * Gets the current zoom level.
     *
     * @return Current zoom level (1.0-5.0)
     */
    fun getCurrentZoom(): Float = currentZoom

    /**
     * Sets the target zoom level and optionally animates to it smoothly.
     *
     * This method handles zoom level changes with the following logic:
     * - Clamps the target zoom to valid range (1.0-5.0)
     * - On first call, applies zoom immediately without animation
     * - Checks if the change is significant enough to warrant an update
     * - Either animates smoothly or applies instantly based on the animate parameter
     *
     * The method includes threshold-based change detection to avoid unnecessary
     * camera updates for very small zoom changes, which improves performance.
     *
     * @param targetZoom The desired zoom level (1.0-5.0)
     * @param animate Whether to animate the zoom change (true for smooth transition, false for instant)
     */
    fun setZoom(
        targetZoom: Float,
        animate: Boolean = true,
    ) {
        val clampedTarget = targetZoom.coerceIn(AppSyncConstants.MIN_ZOOM, AppSyncConstants.MAX_ZOOM)

        // Debug logging
        android.util.Log.d(
            "AppSyncZoom",
            "setZoom called: targetZoom=$targetZoom, clampedTarget=$clampedTarget, animate=$animate, isInitialized=$isInitialized",
        )

        // For the first call, always apply the zoom without checking threshold
        if (!isInitialized) {
            this.targetZoom = clampedTarget
            this.currentZoom = clampedTarget
            android.util.Log.d("AppSyncZoom", "First call - setting initial zoom: $clampedTarget")
            applyZoomToCamera()
            isInitialized = true
            return
        }

        // Check if the change is significant enough to warrant an update
        if (abs(this.targetZoom - clampedTarget) < AppSyncConstants.ZOOM_CHANGE_THRESHOLD) {
            android.util.Log.d("AppSyncZoom", "Change too small, ignoring")
            return // No change needed
        }

        android.util.Log.d("AppSyncZoom", "Updating zoom from ${this.targetZoom} to $clampedTarget")
        this.targetZoom = clampedTarget

        if (animate) {
            animateZoom()
        } else {
            currentZoom = clampedTarget
            applyZoomToCamera()
        }
    }

    /**
     * Animates zoom to the target level smoothly using coroutines.
     *
     * This method creates a smooth animation from the current zoom level
     * to the target zoom level. The animation is divided into multiple steps
     * with small delays between each step to create a fluid transition.
     *
     * The animation uses the configured duration and step count from
     * [AppSyncConstants] to ensure consistent behavior across the app.
     *
     * If an animation is already in progress, this method will skip
     * the new animation request to avoid conflicts.
     */
    private fun animateZoom() {
        if (isAnimating) {
            android.util.Log.d("AppSyncZoom", "Animation already in progress, skipping")
            return
        }

        android.util.Log.d("AppSyncZoom", "Starting animation from $currentZoom to $targetZoom")
        isAnimating = true
        coroutineScope.launch {
            val startZoom = currentZoom
            val zoomDifference = targetZoom - startZoom
            val stepDuration = AppSyncConstants.ZOOM_ANIMATION_DURATION / AppSyncConstants.ZOOM_ANIMATION_STEPS
            val zoomStep = zoomDifference / AppSyncConstants.ZOOM_ANIMATION_STEPS

            android.util.Log.d(
                "AppSyncZoom",
                "Animation params: startZoom=$startZoom, zoomDifference=$zoomDifference, zoomStep=$zoomStep",
            )

            // Animate through each step
            for (i in 1..AppSyncConstants.ZOOM_ANIMATION_STEPS) {
                currentZoom = startZoom + (zoomStep * i)
                applyZoomToCamera()
                delay(stepDuration)
            }

            // Ensure we end up exactly at the target
            currentZoom = targetZoom
            applyZoomToCamera()
            android.util.Log.d("AppSyncZoom", "Animation completed, final zoom: $currentZoom")
            isAnimating = false
        }
    }

    /**
     * Applies the current zoom level to the camera using CameraX.
     *
     * This method maps the app's zoom levels (1.0-5.0) to the camera's
     * zoom ratios (0.0-1.0) and applies the zoom using CameraX's
     * [CameraControl.setLinearZoom] method.
     *
     * The mapping ensures that:
     * - MIN_ZOOM (1.0) maps to camera zoom ratio 0.0 (no zoom)
     * - MAX_ZOOM (5.0) maps to camera zoom ratio 1.0 (maximum zoom)
     * - Intermediate values are linearly interpolated
     *
     * The method includes safety checks to ensure the zoom ratio is
     * clamped to valid range (0.0-1.0).
     */
    private fun applyZoomToCamera() {
        cameraControl?.let { control ->
            // Map our zoom range (1.0-5.0) to camera zoom ratio (0.0-1.0)
            // When currentZoom is MIN_ZOOM (1.0), zoomRatio should be 0.0
            // When currentZoom is MAX_ZOOM (5.0), zoomRatio should be 1.0
            val zoomRatio =
                (currentZoom - AppSyncConstants.MIN_ZOOM) /
                    (AppSyncConstants.MAX_ZOOM - AppSyncConstants.MIN_ZOOM)
            val clampedRatio = zoomRatio.coerceIn(0f, 1f)

            // Debug logging
            android.util.Log.d("AppSyncZoom", "Setting zoom: currentZoom=$currentZoom, zoomRatio=$clampedRatio")

            control.setLinearZoom(clampedRatio)
        }
    }

    /**
     * Checks if a zoom animation is currently in progress.
     *
     * @return true if an animation is running, false otherwise
     */
    fun isAnimating(): Boolean = isAnimating

    /**
     * Stops any ongoing zoom animation.
     *
     * This method immediately stops any zoom animation that is currently
     * in progress. The camera will remain at its current zoom level
     * when the animation is stopped.
     */
    fun stopAnimation() {
        isAnimating = false
    }
}
