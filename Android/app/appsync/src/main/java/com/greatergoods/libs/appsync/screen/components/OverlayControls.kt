package com.greatergoods.libs.appsync.screen.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.greatergoods.libs.appsync.R
import com.greatergoods.libs.appsync.config.AppSyncConstants
import com.greatergoods.libs.appsync.strings.AppSyncStrings

/**
 * Overlay UI controls for scan screen: zoom, manual entry, close.
 * @param onManualEntry If null, manual entry button is hidden.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OverlayControls(
    zoomLevel: Float,
    showLowLightWarning: Boolean = false,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onManualEntry: (() -> Unit)? = null,
    onClose: () -> Unit,
) {
    // Calculate enabled states for zoom buttons
    val canZoomIn = zoomLevel < AppSyncConstants.MAX_ZOOM
    val canZoomOut = zoomLevel > AppSyncConstants.MIN_ZOOM

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                // .windowInsetsPadding(WindowInsets.statusBars)
                .padding(
                    top = 24.dp,
                    start = 54.dp,
                    end = 24.dp,
                ),
    ) {
        Row(
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Close button (top right)
            Image(
                painterResource(R.drawable.logo),
                contentDescription = "Appsync Logo",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.width(100.dp),
            )
            AppsyncButton(
                onClose,
                src = R.drawable.ic_close,
                contentDescription = AppSyncStrings.CloseScan,
            )
        }

        Column(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppsyncButton(
                onClick = onZoomIn,
                src = R.drawable.ic_plus,
                contentDescription = AppSyncStrings.ZoomIn,
                enabled = canZoomIn,
            )
            Image(
                painterResource(R.drawable.zoom),
                contentDescription = "Zoom",
                modifier = Modifier.width(40.dp),
                contentScale = ContentScale.FillWidth,
            )
            AppsyncButton(
                onClick = onZoomOut,
                src = R.drawable.ic_minus,
                contentDescription = AppSyncStrings.ZoomOut,
                enabled = canZoomOut,
            )
        }

        // Low light warning (center vertical)
        if (showLowLightWarning) {
            Column(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.warning),
                    contentDescription = "Low light warning",
                    modifier = Modifier.size(50.dp),
                )
            }
        }

        // Zoom controls (bottom right)
        Row(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            if (onManualEntry != null) {
                Button(
                    onClick = onManualEntry,
                    modifier = Modifier,
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                ) {
                    Text(AppSyncStrings.ManualEntry.uppercase())
                }
            }
        }
    }
}

@Preview(showSystemUi = true, showBackground = true)
@Composable
fun OverlayControlsPreview() {
    OverlayControls(1.0f, true, {}, {}, {}) {}
}

@Preview(showSystemUi = true, showBackground = true)
@Composable
fun OverlayControlsMaxZoomPreview() {
    OverlayControls(5.0f, true, {}, {}, {}) {}
}

@Preview(showSystemUi = true, showBackground = true)
@Composable
fun OverlayControlsMinZoomPreview() {
    OverlayControls(1.0f, true, {}, {}, {}) {}
}
