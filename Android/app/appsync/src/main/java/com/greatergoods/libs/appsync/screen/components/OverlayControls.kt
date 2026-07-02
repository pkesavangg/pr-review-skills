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
 * Overlay UI controls for the AppSync scan screen.
 *
 * This composable provides the complete set of user interface controls that appear
 * as overlays on top of the camera preview. The controls are positioned strategically
 * to provide easy access while not obstructing the scanning area. The layout includes:
 *
 * - **Top bar**: AppSync logo on the left and close button on the right
 * - **Right side**: Zoom controls (zoom in/out buttons with zoom indicator)
 * - **Left side**: Low light warning indicator (when conditions are poor)
 * - **Bottom**: Manual entry button (optional, can be hidden)
 *
 * The controls automatically adjust their enabled states based on the current zoom
 * level and provide visual feedback for various conditions like low light.
 *
 * @param zoomLevel Current zoom level of the camera (1.0 to 5.0)
 * @param showLowLightWarning Whether to display the low light warning indicator
 * @param onZoomIn Callback invoked when the zoom in button is pressed
 * @param onZoomOut Callback invoked when the zoom out button is pressed
 * @param onManualEntry Optional callback for manual entry. If null, the manual entry
 *                      button is hidden
 * @param onClose Callback invoked when the close button is pressed
 */
@Composable
fun OverlayControls(
  zoomLevel: Float,
  showLowLightWarning: Boolean = false,
  onZoomIn: () -> Unit,
  onZoomOut: () -> Unit,
  onManualEntry: (() -> Unit)? = null,
  onClose: () -> Unit,
) {
  // Calculate enabled states for zoom buttons based on current zoom level
  val canZoomIn = zoomLevel < AppSyncConstants.MAX_ZOOM
  val canZoomOut = zoomLevel > AppSyncConstants.MIN_ZOOM

  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .padding(
          top = 24.dp,
          start = 54.dp,
          end = 24.dp,
        ),
  ) {
    // Top bar with logo and close button
    OverlayTopBar(
      onClose = onClose,
      modifier = Modifier.align(Alignment.TopStart),
    )

    // Right side zoom controls
    OverlayZoomControls(
      canZoomIn = canZoomIn,
      canZoomOut = canZoomOut,
      onZoomIn = onZoomIn,
      onZoomOut = onZoomOut,
      modifier = Modifier.align(Alignment.CenterEnd),
    )

    // Low light warning indicator (left side)
    if (showLowLightWarning) {
      OverlayLowLightWarning(
        modifier = Modifier.align(Alignment.CenterStart),
      )
    }

    // Bottom manual entry button (optional)
    OverlayManualEntry(
      onManualEntry = onManualEntry,
      modifier = Modifier.align(Alignment.BottomEnd),
    )
  }
}

/**
 * Top bar of [OverlayControls]: the AppSync logo on the left and the close button
 * on the right. Extracted mechanically; rendered output is unchanged.
 */
@Composable
private fun OverlayTopBar(
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // AppSync logo on the left
    Image(
      painterResource(R.drawable.logo),
      contentDescription = "Appsync Logo",
      contentScale = ContentScale.FillWidth,
      modifier = Modifier.width(100.dp),
    )

    // Close button on the right
    AppsyncButton(
      onClick = onClose,
      src = R.drawable.ic_close,
      contentDescription = AppSyncStrings.CloseScan,
    )
  }
}

/**
 * Right-side zoom controls of [OverlayControls]: zoom-in, zoom indicator, zoom-out.
 * Extracted mechanically; rendered output is unchanged.
 */
@Composable
private fun OverlayZoomControls(
  canZoomIn: Boolean,
  canZoomOut: Boolean,
  onZoomIn: () -> Unit,
  onZoomOut: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.padding(end = 32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // Zoom in button
    AppsyncButton(
      onClick = onZoomIn,
      src = R.drawable.ic_plus,
      contentDescription = AppSyncStrings.ZoomIn,
      enabled = canZoomIn,
    )

    // Zoom indicator image
    Image(
      painterResource(R.drawable.zoom),
      contentDescription = "Zoom",
      modifier = Modifier.width(40.dp),
      contentScale = ContentScale.FillWidth,
    )

    // Zoom out button
    AppsyncButton(
      onClick = onZoomOut,
      src = R.drawable.ic_minus,
      contentDescription = AppSyncStrings.ZoomOut,
      enabled = canZoomOut,
    )
  }
}

/**
 * Left-side low light warning indicator of [OverlayControls].
 * Extracted mechanically; rendered output is unchanged.
 */
@Composable
private fun OverlayLowLightWarning(
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.padding(start = 40.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    // Warning icon
    Image(
      painter = painterResource(R.drawable.warning),
      contentDescription = AppSyncStrings.LowLightWarning,
      modifier = Modifier.size(50.dp),
    )
  }
}

/**
 * Bottom manual entry button of [OverlayControls]. Renders nothing when
 * [onManualEntry] is null. Extracted mechanically; rendered output is unchanged.
 */
@Composable
private fun OverlayManualEntry(
  onManualEntry: (() -> Unit)?,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.padding(vertical = 16.dp),
    horizontalArrangement = Arrangement.End,
  ) {
    // Only show manual entry button if callback is provided
    if (onManualEntry != null) {
      Button(
        onClick = onManualEntry,
        modifier = Modifier,
        shape = RoundedCornerShape(4.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color.Gray,
          ),
      ) {
        Text(AppSyncStrings.ManualEntry.uppercase())
      }
    }
  }
}

/**
 * Preview composable for OverlayControls with default zoom level.
 *
 * Shows the controls in their normal state with zoom level 1.0 and low light
 * warning enabled. Used for development and testing purposes.
 */
@Preview(showSystemUi = true, showBackground = true)
@Composable
fun OverlayControlsPreview() {
  OverlayControls(
    zoomLevel = 1.0f,
    showLowLightWarning = true,
    onZoomIn = { /* Preview only */ },
    onZoomOut = { /* Preview only */ },
    onManualEntry = { /* Preview only */ },
    onClose = { /* Preview only */ },
  )
}

/**
 * Preview composable for OverlayControls at maximum zoom level.
 *
 * Shows the controls when zoom is at maximum (5.0), demonstrating how the
 * zoom in button becomes disabled. Used for development and testing purposes.
 */
@Preview(showSystemUi = true, showBackground = true)
@Composable
fun OverlayControlsMaxZoomPreview() {
  OverlayControls(
    zoomLevel = 5.0f,
    showLowLightWarning = true,
    onZoomIn = { /* Preview only */ },
    onZoomOut = { /* Preview only */ },
    onManualEntry = { /* Preview only */ },
    onClose = { /* Preview only */ },
  )
}

/**
 * Preview composable for OverlayControls at minimum zoom level.
 *
 * Shows the controls when zoom is at minimum (1.0), demonstrating how the
 * zoom out button becomes disabled. Used for development and testing purposes.
 */
@Preview(showSystemUi = true, showBackground = true)
@Composable
fun OverlayControlsMinZoomPreview() {
  OverlayControls(
    zoomLevel = 1.0f,
    showLowLightWarning = true,
    onZoomIn = { /* Preview only */ },
    onZoomOut = { /* Preview only */ },
    onManualEntry = { /* Preview only */ },
    onClose = { /* Preview only */ },
  )
}

/**
 * Preview composable for OverlayControls with low light warning.
 *
 * Shows the controls with the low light warning indicator displayed on the
 * left side. Used for development and testing purposes.
 */
@Preview(showSystemUi = true, showBackground = true)
@Composable
fun OverlayControlsLowLightPreview() {
  OverlayControls(
    zoomLevel = 1.0f,
    showLowLightWarning = true,
    onZoomIn = { /* Preview only */ },
    onZoomOut = { /* Preview only */ },
    onManualEntry = { /* Preview only */ },
    onClose = { /* Preview only */ },
  )
}
