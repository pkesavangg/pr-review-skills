package com.greatergoods.libs.appsync.screen.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Overlay composable for camera preview that provides visual targeting for scan operations.
 *
 * This composable creates a visual overlay that helps users position their device
 * correctly when scanning smart scale displays. It consists of:
 * - A semi-transparent overlay covering the entire screen
 * - A transparent rectangular target area in the center
 * - White corner indicators at each corner of the target area
 *
 * The target area is sized to match typical smart scale display dimensions
 * (250dp x 161dp) and is positioned in the center of the screen. The corner
 * indicators provide clear visual guidance for proper alignment.
 *
 * The overlay uses a dark semi-transparent background to reduce visual clutter
 * while maintaining visibility of the camera feed in the target area. The white
 * corner indicators provide high contrast for easy visibility in various lighting
 * conditions.
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
            val cornerColor = Color.White // White corner indicators
            val cornerRadius = 16.dp.toPx() // Rounded corners for target area
            val cornerStroke = 6.dp.toPx() // Thickness of corner indicators
            val cornerLength = size.minDimension * 0.18f // Length of corner indicators

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
                cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                blendMode = androidx.compose.ui.graphics.BlendMode.Clear,
            )

            // Draw corner indicators for each corner of the target area
            // Each corner consists of two perpendicular lines

            // Top-left corner - horizontal line
            drawRoundRect(
                color = cornerColor,
                topLeft = boxRect.topLeft,
                size =
                    androidx.compose.ui.geometry
                        .Size(cornerLength, cornerStroke),
                cornerRadius = CornerRadius(cornerStroke, cornerStroke),
            )
            // Top-left corner - vertical line
            drawRoundRect(
                color = cornerColor,
                topLeft = boxRect.topLeft,
                size =
                    androidx.compose.ui.geometry
                        .Size(cornerStroke, cornerLength),
                cornerRadius = CornerRadius(cornerStroke, cornerStroke),
            )

            // Top-right corner - horizontal line
            drawRoundRect(
                color = cornerColor,
                topLeft = boxRect.topRight - Offset(cornerLength, 0f),
                size =
                    androidx.compose.ui.geometry
                        .Size(cornerLength, cornerStroke),
                cornerRadius = CornerRadius(cornerStroke, cornerStroke),
            )
            // Top-right corner - vertical line
            drawRoundRect(
                color = cornerColor,
                topLeft = boxRect.topRight - Offset(cornerStroke, 0f),
                size =
                    androidx.compose.ui.geometry
                        .Size(cornerStroke, cornerLength),
                cornerRadius = CornerRadius(cornerStroke, cornerStroke),
            )

            // Bottom-left corner - vertical line
            drawRoundRect(
                color = cornerColor,
                topLeft = boxRect.bottomLeft - Offset(0f, cornerLength),
                size =
                    androidx.compose.ui.geometry
                        .Size(cornerStroke, cornerLength),
                cornerRadius = CornerRadius(cornerStroke, cornerStroke),
            )
            // Bottom-left corner - horizontal line
            drawRoundRect(
                color = cornerColor,
                topLeft = boxRect.bottomLeft - Offset(0f, cornerStroke),
                size =
                    androidx.compose.ui.geometry
                        .Size(cornerLength, cornerStroke),
                cornerRadius = CornerRadius(cornerStroke, cornerStroke),
            )

            // Bottom-right corner - horizontal line
            drawRoundRect(
                color = cornerColor,
                topLeft = boxRect.bottomRight - Offset(cornerLength, cornerStroke),
                size =
                    androidx.compose.ui.geometry
                        .Size(cornerLength, cornerStroke),
                cornerRadius = CornerRadius(cornerStroke, cornerStroke),
            )
            // Bottom-right corner - vertical line
            drawRoundRect(
                color = cornerColor,
                topLeft = boxRect.bottomRight - Offset(cornerStroke, cornerLength),
                size =
                    androidx.compose.ui.geometry
                        .Size(cornerStroke, cornerLength),
                cornerRadius = CornerRadius(cornerStroke, cornerStroke),
            )
        }

        // Alternative implementation using image overlay (commented out)
        // This could be used to display a custom overlay image instead of drawing
        // Image(
        //     painterResource(R.drawable.display_overlay),
        //     "Target Overlay",
        //     modifier = Modifier.align(Alignment.Center),
        // )
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
