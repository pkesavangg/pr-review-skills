package com.greatergoods.meapp.utils

import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import android.app.Activity
import android.content.Context
import com.google.android.play.core.review.ReviewInfo
import javax.inject.Singleton

/**
 * Interface defining the contract for app review functionality.
 * This interface abstracts the implementation details of the Google Play Store review system.
 */
interface IAppReviewManager {
    /**
     * Checks if the app is eligible for review and prepares the review flow.
     * @return true if the app is eligible for review, false otherwise
     */
    suspend fun shouldPromptForReview(): Boolean

    /**
     * Launches the review flow for the given activity.
     * @param activity The activity to launch the review flow from
     * @return true if the review flow was launched successfully, false otherwise
     */
    suspend fun launchReviewFlow(activity: Activity?): Boolean
}

/**
 * Implementation of the app review manager using Google Play Core Library.
 * This class is responsible for managing the app review process and interacting
 * with the Google Play Store review API.
 *
 * @property context The application context
 */
@Singleton
class AppReviewManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : IAppReviewManager {

    // ReviewManager instance for handling review flow
    private val manager: ReviewManager = ReviewManagerFactory.create(context)

    // Cached ReviewInfo instance to avoid multiple API calls
    private var reviewInfo: ReviewInfo? = null

    /**
     * Checks if the app is eligible for review by requesting the review flow.
     * If successful, caches the ReviewInfo for later use.
     *
     * @return true if the app is eligible for review, false if there was an error
     */
    override suspend fun shouldPromptForReview(): Boolean {
        return try {
            val result = manager.requestReviewFlow().await()
            reviewInfo = result
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Launches the review flow for the given activity using the cached ReviewInfo.
     * If no ReviewInfo is available or the activity is null, returns false.
     *
     * @param activity The activity to launch the review flow from
     * @return true if the review flow was launched successfully, false otherwise
     */
    override suspend fun launchReviewFlow(activity: Activity?): Boolean {
        return try {
            reviewInfo?.let {
                activity?.let { p0 -> manager.launchReviewFlow(p0, it) }?.await()
                true
            } ?: false
        } catch (e: Exception) {
            false
        }
    }
}
