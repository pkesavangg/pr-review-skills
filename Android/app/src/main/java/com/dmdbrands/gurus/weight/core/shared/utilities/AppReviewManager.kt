package com.dmdbrands.gurus.weight.core.shared.utilities

import com.google.android.play.core.review.ReviewManagerFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import android.app.Activity
import android.content.Context
import android.util.Log

/**
 * Interface defining the contract for app review functionality.
 * This interface abstracts the implementation details of the Google Play Store review system.
 */
interface IAppReviewManager {
    suspend fun launchInAppReview(context: Context)
}

/**
 * Implementation of the app review manager using Google Play Core Library.
 * This class is responsible for managing the app review process and interacting
 * with the Google Play Store review API.
 *
 * @property context The application context
 */
@Singleton
class AppReviewManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : IAppReviewManager {
  val TAG = "Appreviewmanager"

        // ReviewManager instance for handling review flow

  override suspend fun launchInAppReview(context: Context) {
    val reviewManager = ReviewManagerFactory.create(context as Activity)
    val requestReviewFlow = reviewManager.requestReviewFlow()
    requestReviewFlow.addOnCompleteListener { request ->
      if (request.isSuccessful) {
        val reviewInfo = request.result
        reviewManager.launchReviewFlow(context, reviewInfo)
      } else {
        Timber.e(request.exception?.localizedMessage)
        Log.d(TAG, "${request.exception?.localizedMessage}")
      }
    }
  } }
