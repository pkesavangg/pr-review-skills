package com.greatergoods.meapp.utils

import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import android.app.Activity
import android.content.Context

interface AppReviewManager {
    suspend fun shouldPromptForReview(): Boolean
    suspend fun launchReviewFlow(activity: Activity): Boolean
}

class AppReviewManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AppReviewManager {

    private val manager: ReviewManager =
      ReviewManagerFactory.create(context)

    private var reviewInfo: com.google.android.play.core.review.ReviewInfo? = null

    override suspend fun shouldPromptForReview(): Boolean {
        return try {
            val result = manager.requestReviewFlow().await()
            reviewInfo = result
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun launchReviewFlow(activity: Activity): Boolean {
        return try {
            reviewInfo?.let {
                manager.launchReviewFlow(activity, it).await()
                true
            } ?: false
        } catch (e: Exception) {
            false
        }
    }
}
