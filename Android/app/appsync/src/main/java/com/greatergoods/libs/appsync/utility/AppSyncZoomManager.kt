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

        if (abs(this.targetZoom - clampedTarget) < AppSyncConstants.ZOOM_CHANGE_THRESHOLD) {
            return // No change needed
        }

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
        if (isAnimating) return

        isAnimating = true
        coroutineScope.launch {
            val startZoom = currentZoom
            val zoomDifference = targetZoom - startZoom
            val stepDuration = AppSyncConstants.ZOOM_ANIMATION_DURATION / AppSyncConstants.ZOOM_ANIMATION_STEPS
            val zoomStep = zoomDifference / AppSyncConstants.ZOOM_ANIMATION_STEPS

            for (i in 1..AppSyncConstants.ZOOM_ANIMATION_STEPS) {
                currentZoom = startZoom + (zoomStep * i)
                applyZoomToCamera()
                delay(stepDuration)
            }

            // Ensure we end up exactly at the target
            currentZoom = targetZoom
            applyZoomToCamera()
            isAnimating = false
        }
    }

    /**
     * Applies the current zoom level to the camera.
     */
    private fun applyZoomToCamera() {
        cameraControl?.let { control ->
            val zoomRatio = (currentZoom - AppSyncConstants.MIN_ZOOM) /
                           (AppSyncConstants.MAX_ZOOM - AppSyncConstants.MIN_ZOOM)
            control.setLinearZoom(zoomRatio.coerceIn(0f, 1f))
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
