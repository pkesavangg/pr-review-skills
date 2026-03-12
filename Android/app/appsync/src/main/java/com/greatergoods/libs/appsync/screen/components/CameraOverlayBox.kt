package com.greatergoods.libs.appsync.screen.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.greatergoods.libs.appsync.R

/**
 * Overlay composable for camera preview that provides visual targeting for scan operations.
 *
 * This composable creates a visual overlay that helps users position their device
 * correctly when scanning smart scale displays. It consists of:
 * - A semi-transparent overlay covering the entire screen
 * - A transparent rectangular target area in the center
 * - White corner indicators at each corner of the target area
 * - A display overlay image positioned within the scanning rectangle
 *
 * The target area is sized to match typical smart scale display dimensions
 * (250dp x 161dp) and is positioned in the center of the screen. The corner
 * indicators provide clear visual guidance for proper alignment, while the
 * display overlay image provides additional visual context for scanning.
 *
 * The overlay uses a dark semi-transparent background to reduce visual clutter
 * while maintaining visibility of the camera feed in the target area. The white
 * corner indicators provide high contrast for easy visibility in various lighting
 * conditions. The display overlay image is centered within the scanning rectangle
 * to provide visual guidance for optimal scanning positioning.
 *
 * @param modifier Optional modifier to apply to the overlay container
 */
@Composable
fun CameraOverlayBox(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Calculate overlay dimensions
            val overlayRect = size

            // Define target box dimensions (matching typical scale display size)
            val targetBoxWidth = 250.dp.toPx()
            val targetBoxHeight = 161.dp.toPx()

            // Center the target box on screen
            val left = (size.width - targetBoxWidth) / 2
            val top = (size.height - targetBoxHeight) / 2
            val boxRect = Rect(left, top, left + targetBoxWidth, top + targetBoxHeight)

            // Define visual styling constants
            val overlayColor = Color(0x802C2827).copy(alpha = 0.5f) // Dark semi-transparent overlay

            // Draw the semi-transparent overlay covering the entire screen
            drawRoundRect(
                color = overlayColor,
                size = overlayRect,
                cornerRadius = CornerRadius(0f, 0f),
            )

            // Clear the center target area to create transparency
            drawRoundRect(
                color = Color.Transparent,
                topLeft = boxRect.topLeft,
                size = boxRect.size,
                blendMode = androidx.compose.ui.graphics.BlendMode.Clear,
            )

        }

      // Display overlay image within the scanning rectangle
      Image(
        painter = painterResource(id = R.drawable.display_overlay),
        contentDescription = "Display Overlay",
        modifier = Modifier
          .size(150.dp, 100.dp)
          .align(Alignment.Center)
          .alpha(0.4f),
      )
    }
}

/**
 * Preview composable for the CameraOverlayBox.
 *
 * This preview is used for development and testing purposes to visualize
 * the overlay in Android Studio's preview pane. It shows how the overlay
 * appears with the target area and corner indicators.
 */
@Preview(showSystemUi = true)
@Composable
fun CameraOverlayBoxPreview() {
    CameraOverlayBox()
}
