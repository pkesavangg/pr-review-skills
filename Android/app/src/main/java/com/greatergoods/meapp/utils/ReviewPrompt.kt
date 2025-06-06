package com.greatergoods.meapp.utils

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import android.app.Activity

/**
 * A composable that displays a review prompt button when the app is eligible for review.
 * This component handles the UI and interaction logic for requesting app reviews.
 *
 * @param viewModel The ViewModel that manages the review state and business logic.
 *                  If not provided, it will be automatically injected using Hilt.
 */
@Composable
fun ReviewPrompt(viewModel: ReviewViewModel = hiltViewModel()) {
    // Get the current context and coroutine scope
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Observe the review eligibility state
    val showReview by viewModel.shouldShowReview.collectAsState()

    // Check review eligibility when the composable is first launched
    LaunchedEffect(Unit) {
        viewModel.checkReviewEligibility()
    }

    // Show the review button only if the app is eligible for review
    if (showReview) {
        Button(onClick = {
            val activity = context as? Activity
            if (activity != null) {
                scope.launch {
                    viewModel.launchReview(activity) { success ->
                        // Optional: show message or snackbar
                    }
                }
            }
        }) {
            Text("Leave a Review")
        }
    }
}


