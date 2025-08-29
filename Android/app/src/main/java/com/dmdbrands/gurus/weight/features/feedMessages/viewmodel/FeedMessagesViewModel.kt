package com.dmdbrands.gurus.weight.features.feedMessages.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * ViewModel for FeedMessagesScreen
 * Currently handles basic state, can be extended for future features
 */
@HiltViewModel
class FeedMessagesViewModel @Inject constructor() : ViewModel() {
  // Future: Add state management for messages, loading states, etc.
}
