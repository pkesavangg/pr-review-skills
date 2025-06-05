package com.greatergoods.meapp.utils

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel class that manages the app review state and business logic.
 * This class coordinates between the UI layer and the app review manager.
 *
 * @property reviewManager The app review manager instance injected by Hilt
 */
@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val reviewManager: IAppReviewManager
) : ViewModel() {

    // State flow to track whether the review prompt should be shown
    private val _shouldShowReview = MutableStateFlow(false)
    val shouldShowReview: StateFlow<Boolean> = _shouldShowReview

    // Flag to track if review info is available from the review manager
    private var reviewInfoAvailable = false

    /**
     * Checks if the app is eligible for review by querying the review manager.
     * Updates the shouldShowReview state based on the result.
     */
    fun checkReviewEligibility() {
        viewModelScope.launch {
            reviewInfoAvailable = reviewManager.shouldPromptForReview()
            _shouldShowReview.value = reviewInfoAvailable
        }
    }

    /**
     * Launches the review flow for the given activity.
     *
     * @param activity The activity to launch the review flow from
     * @param onResult Callback function that receives the result of the review flow launch
     */
    fun launchReview(activity: Activity?, onResult: (Boolean) -> Unit) {
        if (activity == null || !reviewInfoAvailable) {
            onResult(false)
            return
        }

        viewModelScope.launch {
            val success = reviewManager.launchReviewFlow(activity)
            onResult(success)
        }
    }
}
