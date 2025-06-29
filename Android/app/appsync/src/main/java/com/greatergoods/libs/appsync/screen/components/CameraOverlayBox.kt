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
 * Overlay composable for camera preview with a transparent square and rounded corners.
 */
@Composable
fun CameraOverlayBox(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val overlayRect = size
            val boxSize = size.minDimension * 0.6f
            // val left = (size.width - boxSize) / 2
            // val top = (size.height - boxSize) / 2
            val targetBoxWidth = 250.dp.toPx()
            val targetBoxHeight = 161.dp.toPx()
            val left = (size.width - targetBoxWidth) / 2
            val top = (size.height - targetBoxHeight) / 2
            val boxRect = Rect(left, top, left + targetBoxWidth, top + targetBoxHeight)

            // val boxRect = Rect(left, top, left + boxSize, top + boxSize)

            // Use theme colors
            val overlayColor = Color(0x802C2827).copy(alpha = 0.5f)
            val cornerColor = Color.White
            val cornerRadius = 16.dp.toPx()
            val cornerStroke = 6.dp.toPx()
            val cornerLength = boxSize * 0.18f

            // Draw the semi-transparent overlay
            drawRoundRect(
                color = overlayColor,
                size = overlayRect,
                cornerRadius = CornerRadius(0f, 0f),
            )
            // Clear the center square (simulate transparency)
            drawRoundRect(
                color = Color.Transparent,
                topLeft = boxRect.topLeft,
                size = boxRect.size,
                cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                blendMode = androidx.compose.ui.graphics.BlendMode.Clear,
            )
            // Draw the white corners (4 corners, each with two lines)
            // Top-left
            drawRoundRect(
                color = cornerColor,
                topLeft = boxRect.topLeft,
                size =
                    androidx.compose.ui.geometry
                        .Size(cornerLength, cornerStroke),
                cornerRadius = CornerRadius(cornerStroke, cornerStroke),
            )
            drawRoundRect(
                color = cornerColor,
                topLeft = boxRect.topLeft,
                size =
                    androidx.compose.ui.geometry
                        .Size(cornerStroke, cornerLength),
                cornerRadius = CornerRadius(cornerStroke, cornerStroke),
            )
            // Top-right
            drawRoundRect(
                color = cornerColor,
                topLeft = boxRect.topRight - Offset(cornerLength, 0f),
                size =
                    androidx.compose.ui.geometry
                        .Size(cornerLength, cornerStroke),
                cornerRadius = CornerRadius(cornerStroke, cornerStroke),
            )
            drawRoundRect(
                color = cornerColor,
                topLeft = boxRect.topRight - Offset(cornerStroke, 0f),
                size =
                    androidx.compose.ui.geometry
                        .Size(cornerStroke, cornerLength),
                cornerRadius = CornerRadius(cornerStroke, cornerStroke),
            )
            // Bottom-left
            drawRoundRect(
                color = cornerColor,
                topLeft = boxRect.bottomLeft - Offset(0f, cornerLength),
                size =
                    androidx.compose.ui.geometry
                        .Size(cornerStroke, cornerLength),
                cornerRadius = CornerRadius(cornerStroke, cornerStroke),
            )
            drawRoundRect(
                color = cornerColor,
                topLeft = boxRect.bottomLeft - Offset(0f, cornerStroke),
                size =
                    androidx.compose.ui.geometry
                        .Size(cornerLength, cornerStroke),
                cornerRadius = CornerRadius(cornerStroke, cornerStroke),
            )
            // Bottom-right
            drawRoundRect(
                color = cornerColor,
                topLeft = boxRect.bottomRight - Offset(cornerLength, cornerStroke),
                size =
                    androidx.compose.ui.geometry
                        .Size(cornerLength, cornerStroke),
                cornerRadius = CornerRadius(cornerStroke, cornerStroke),
            )
            drawRoundRect(
                color = cornerColor,
                topLeft = boxRect.bottomRight - Offset(cornerStroke, cornerLength),
                size =
                    androidx.compose.ui.geometry
                        .Size(cornerStroke, cornerLength),
                cornerRadius = CornerRadius(cornerStroke, cornerStroke),
            )
        }
        // Image(
        //     painterResource(R.drawable.display_overlay),
        //     "Target Overlay",
        //     modifier = Modifier.align(Alignment.Center),
        // )
    }
}

/**
 * Previews for CameraOverlayBox in light and dark mode, phone and tablet.
 */
@Preview(showSystemUi = true)
@Composable
fun CameraOverlayBoxPreview() {
    CameraOverlayBox()
}
