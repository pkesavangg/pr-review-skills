package com.greatergoods.libs.appsync.screen.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.greatergoods.libs.appsync.R
import com.greatergoods.libs.appsync.strings.AppSyncStrings

/**
 * Overlay UI controls for scan screen: zoom, manual entry, close.
 * @param onManualEntry If null, manual entry button is hidden.
 */
@Composable
fun OverlayControls(
    zoomLevel: Int,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onManualEntry: (() -> Unit)? = null,
    onClose: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Close button (top right)
        IconButton(
            onClick = onClose,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .semantics { contentDescription = AppSyncStrings.CloseScan },
        ) {
            Icon(painter = painterResource(R.drawable.close), contentDescription = null, tint = Color.White)
        }
        // Zoom controls (bottom right)
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            horizontalAlignment = Alignment.End,
        ) {
            FloatingActionButton(
                onClick = onZoomIn,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier =
                    Modifier
                        .size(40.dp)
                        .semantics { contentDescription = AppSyncStrings.ZoomIn },
            ) {
                Icon(painter = painterResource(R.drawable.zoomincrease), contentDescription = null, tint = Color.White)
            }
            Spacer(modifier = Modifier.height(8.dp))
            FloatingActionButton(
                onClick = onZoomOut,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier =
                    Modifier
                        .size(40.dp)
                        .semantics { contentDescription = AppSyncStrings.ZoomOut },
            ) {
                Icon(painter = painterResource(R.drawable.zoomdecrease), contentDescription = null, tint = Color.White)
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (onManualEntry != null) {
                FloatingActionButton(
                    onClick = onManualEntry,
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = Color.White,
                    modifier =
                        Modifier
                            .size(40.dp)
                            .semantics { contentDescription = AppSyncStrings.ManualEntry },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.zoomdecrease),
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
            }
        }
    }
}
