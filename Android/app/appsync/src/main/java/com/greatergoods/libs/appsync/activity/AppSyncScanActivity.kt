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
 * Activity that hosts the AppSync scan flow UI.
 *
 * This activity serves as the container for the camera-based scanning interface. It:
 * - Hosts the Compose UI for camera preview and overlay controls
 * - Manages the scan result delivery to [AppSyncResultHolder]
 * - Handles activity lifecycle and result propagation
 * - Provides edge-to-edge display for immersive scanning experience
 *
 * The activity receives configuration parameters via intent extras and passes them
 * to the [AppSyncScanScreen] composable. When a scan result is available, it stores
 * the result in [AppSyncResultHolder] and finishes itself to return control to
 * the calling activity.
 *
 * @see AppSyncScanScreen
 * @see AppSyncResultHolder
 */
class AppSyncScanActivity : ComponentActivity() {
    /**
     * Initializes the activity and sets up the Compose UI.
     *
     * This method:
     * 1. Extracts configuration parameters from the launching intent
     * 2. Sets up edge-to-edge display for immersive experience
     * 3. Creates the [AppSyncScanScreen] composable with proper configuration
     * 4. Handles result delivery to [AppSyncResultHolder]
     *
     * The activity uses a local state variable to prevent multiple result deliveries
     * in case of rapid state changes or recompositions.
     *
     * @param savedInstanceState The saved instance state bundle, or null if no saved state
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Extract configuration parameters from intent extras
        val initialZoom = intent.getIntExtra("zoom", 1)
        val showManualEntryButton = intent.getBooleanExtra("showManualEntryButton", true)

        setContent {
            // Local state to prevent multiple result deliveries
            var finished by remember { mutableStateOf(false) }

            // Enable edge-to-edge display for immersive scanning experience
            enableEdgeToEdge()

            // Create the main scan screen composable
            AppSyncScanScreen(
                initialZoom = initialZoom,
                showManualEntryButton = showManualEntryButton,
                onResult = { result ->
                    // Ensure we only deliver the result once
                    if (!finished) {
                        // Store the result for the calling function to retrieve
                        AppSyncResultHolder.result = result
                        finished = true
                        // Finish the activity to return control to the caller
                        finish()
                    }
                },
            )
        }
    }
}