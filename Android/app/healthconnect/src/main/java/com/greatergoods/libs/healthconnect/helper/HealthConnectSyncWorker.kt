package com.greatergoods.libs.healthconnect.helper

import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import android.content.Context

/**
 * Worker for performing background Health Connect sync operations.
 * Extend this to implement actual sync logic.
 */
class HealthConnectSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        // TODO: Implement background sync logic
        return Result.success()
    }
}
