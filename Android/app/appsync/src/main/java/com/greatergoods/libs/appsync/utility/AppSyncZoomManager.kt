package com.greatergoods.libs.appsync.utility

import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import com.greatergoods.libs.appsync.config.AppSyncConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Manages smooth camera zoom animations.
 * Provides animated zoom transitions instead of instant zoom changes.
 */
class AppSyncZoomManager(
    private val cameraControl: CameraControl?,
    private val cameraInfo: CameraInfo?,
    private val coroutineScope: CoroutineScope
) {
    private var currentZoom = AppSyncConstants.MIN_ZOOM
    private var targetZoom = AppSyncConstants.MIN_ZOOM
    private var isAnimating = false
    private var isInitialized = false

    /**
     * Gets the current zoom level.
     */
    fun getCurrentZoom(): Float = currentZoom

    /**
     * Sets the target zoom level and animates to it smoothly.
     *
     * @param targetZoom The target zoom level
     * @param animate Whether to animate the zoom change (default: true)
     */
    fun setZoom(targetZoom: Float, animate: Boolean = true) {
        val clampedTarget = targetZoom.coerceIn(AppSyncConstants.MIN_ZOOM, AppSyncConstants.MAX_ZOOM)

        // Debug logging
        android.util.Log.d("AppSyncZoom", "setZoom called: targetZoom=$targetZoom, clampedTarget=$clampedTarget, animate=$animate, isInitialized=$isInitialized")

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
     * Animates zoom to the target level smoothly.
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

            android.util.Log.d("AppSyncZoom", "Animation params: startZoom=$startZoom, zoomDifference=$zoomDifference, zoomStep=$zoomStep")

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
     * Applies the current zoom level to the camera.
     * Maps our zoom levels (1.0-5.0) to camera zoom ratios (0.0-1.0).
     */
    private fun applyZoomToCamera() {
        cameraControl?.let { control ->
            // Map our zoom range (1.0-5.0) to camera zoom ratio (0.0-1.0)
            // When currentZoom is MIN_ZOOM (1.0), zoomRatio should be 0.0
            // When currentZoom is MAX_ZOOM (5.0), zoomRatio should be 1.0
            val zoomRatio = (currentZoom - AppSyncConstants.MIN_ZOOM) /
                           (AppSyncConstants.MAX_ZOOM - AppSyncConstants.MIN_ZOOM)
            val clampedRatio = zoomRatio.coerceIn(0f, 1f)

            // Debug logging
            android.util.Log.d("AppSyncZoom", "Setting zoom: currentZoom=$currentZoom, zoomRatio=$clampedRatio")

            control.setLinearZoom(clampedRatio)
        }
    }

    /**
     * Checks if zoom animation is currently in progress.
     */
    fun isAnimating(): Boolean = isAnimating

    /**
     * Stops any ongoing zoom animation.
     */
    fun stopAnimation() {
        isAnimating = false
    }
}
