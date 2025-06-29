package com.greatergoods.libs.appsync.activity

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.greatergoods.libs.appsync.AppSyncResultHolder
import com.greatergoods.libs.appsync.screen.AppSyncScanScreen
import android.os.Bundle

/**
 * Activity for the AppSync scan flow. Hosts the Compose UI for camera and overlay.
 * Delivers the result to AppSyncResultHolder and finishes when done.
 */
class AppSyncScanActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialZoom = intent.getIntExtra("zoom", 1)
        val showManualEntryButton = intent.getBooleanExtra("showManualEntryButton", true)
        setContent {
            var finished by remember { mutableStateOf(false) }
            enableEdgeToEdge()
            AppSyncScanScreen(
                initialZoom = initialZoom,
                showManualEntryButton = showManualEntryButton,
                onResult = { result ->
                    if (!finished) {
                        AppSyncResultHolder.result = result
                        finished = true
                        finish()
                    }
                },
            )
        }
    }
}
