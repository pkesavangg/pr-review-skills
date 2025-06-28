package com.greatergoods.libs.appsync

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import android.app.Activity
import android.content.Context
import android.content.Intent

/**
 * Holds the scan result for delivery between the scan activity and the suspend function.
 */
internal object AppSyncResultHolder {
    @Volatile
    var result: AppSyncResult? = null
}

/**
 * Starts the AppSync scan flow.
 *
 * Launches the scan UI and returns the result as an [AppSyncResult].
 *
 * @param context The context to use for launching the scan (should be an Activity context).
 * @param zoom Initial zoom level (default: 1).
 * @param showManualEntryButton Whether to show the manual entry button (default: true).
 * @return The result of the scan.
 */
suspend fun startAppSyncScan(
    context: Context,
    zoom: Int = 1,
    showManualEntryButton: Boolean = true,
): AppSyncResult =
    suspendCancellableCoroutine { cont ->
        // Clear any previous result
        AppSyncResultHolder.result = null
        // Launch the scan activity
        val activity = context as? Activity ?: error("Context must be an Activity")
        val intent = Intent(context, AppSyncScanActivity::class.java)
        // Optionally pass zoom/showManualEntryButton via intent extras if needed
        activity.startActivity(intent)
        // Poll for result (simple approach; can be improved with a callback or LiveData/Flow)
        activity.window.decorView.postDelayed(
            object : Runnable {
                override fun run() {
                    val result = AppSyncResultHolder.result
                    if (result != null) {
                        cont.resume(result)
                    } else {
                        activity.window.decorView.postDelayed(this, 100)
                    }
                }
            },
            100,
        )
    }
