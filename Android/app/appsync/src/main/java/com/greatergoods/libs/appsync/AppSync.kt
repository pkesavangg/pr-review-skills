package com.greatergoods.libs.appsync

import com.greatergoods.libs.appsync.activity.AppSyncScanActivity
import com.greatergoods.libs.appsync.model.AppSyncResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import android.app.Activity
import android.content.Context
import android.content.Intent

/**
 * Internal object that holds the scan result for delivery between the scan activity and the suspend function.
 *
 * This object acts as a bridge between the [AppSyncScanActivity] and the [startAppSyncScan] function.
 * The result is set by the activity when the scan completes and retrieved by the polling mechanism
 * in the suspend function.
 *
 * @property result The scan result, null until the scan completes
 */
internal object AppSyncResultHolder {
    /**
     * The scan result that will be delivered to the calling function.
     * Volatile to ensure thread safety when accessed from different threads.
     */
    @Volatile
    var result: AppSyncResult? = null
}

/**
 * Starts the AppSync scan flow.
 *
 * This function launches the camera-based scanning UI and waits for the user to complete
 * the scan operation. The function returns when the user either:
 * - Successfully scans a scale display
 * - Cancels the operation
 * - Chooses manual entry
 *
 * The function uses a polling mechanism to wait for the result from the scan activity.
 * This approach ensures compatibility with the suspend function pattern while maintaining
 * the existing activity-based UI flow.
 *
 * @param context The context to use for launching the scan. Must be an Activity context
 *                to properly launch the scan activity.
 * @param zoom Initial zoom level for the camera. Valid range is 1-5, with 1 being no zoom
 *             and 5 being maximum zoom. Default is 1.
 * @param showManualEntryButton Whether to show the manual entry button in the scan UI.
 *                              When true, users can choose to manually enter data instead
 *                              of scanning. Default is true.
 * @return An [AppSyncResult] containing the scan results, including measurements like
 *         weight, body fat, muscle, and water percentages, along with error information
 *         and operation status.
 * @throws IllegalArgumentException if the context is not an Activity
 * @throws SecurityException if camera permissions are not granted
 */
suspend fun startAppSyncScan(
    context: Context,
    zoom: Int = 1,
    showManualEntryButton: Boolean = true,
): AppSyncResult =
    suspendCancellableCoroutine { cont ->
        // Clear any previous result to ensure we get a fresh result
        AppSyncResultHolder.result = null

        // Launch the scan activity
        val activity = context as? Activity ?: error("Context must be an Activity")
        val intent = Intent(context, AppSyncScanActivity::class.java)

        // TODO: Pass zoom and showManualEntryButton via intent extras for better parameter handling
        // intent.putExtra("zoom", zoom)
        // intent.putExtra("showManualEntryButton", showManualEntryButton)

        activity.startActivity(intent)

        // Poll for result using a simple polling mechanism
        // This approach can be improved with a callback or LiveData/Flow for better performance
        activity.window.decorView.postDelayed(
            object : Runnable {
                override fun run() {
                    val result = AppSyncResultHolder.result
                    if (result != null) {
                        // Result is available, resume the coroutine with the result
                        cont.resume(result)
                    } else {
                        // Result not yet available, continue polling
                        activity.window.decorView.postDelayed(this, 100)
                    }
                }
            },
            100, // Poll every 100ms
        )
    }
