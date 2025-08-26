package com.greatergoods.ggInAppMessaging.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greatergoods.ggInAppMessaging.core.GGInAppMessagingService
import com.greatergoods.ggInAppMessaging.domain.models.FeedSetting
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for FeedSettingsView
 * Android equivalent of iOS FeedSettingsViewModel
 */
@HiltViewModel
class FeedSettingsViewModel @Inject constructor(
    private val ggIAMService: GGInAppMessagingService
) : ViewModel() {
    
    private val tag = "FeedSettingsViewModel"
    
    private val _feedSetting = MutableStateFlow<FeedSetting?>(null)
    val feedSetting: StateFlow<FeedSetting?> = _feedSetting.asStateFlow()
    
    init {
        loadFeedSettings()
    }
    
    private fun loadFeedSettings() {
        val settings = ggIAMService.getStoredFeedNotificationSetting()
        _feedSetting.value = settings ?: FeedSetting(
            showPopupMessage = true,
            showNotificationBadge = true
        )
    }
    
    /**
     * Updates the popup message setting
     */
    fun updatePopupMessageSetting(enabled: Boolean) {
        val currentSetting = _feedSetting.value ?: return
        val updatedSetting = currentSetting.copy(showPopupMessage = enabled)
        updateSetting(updatedSetting)
    }
    
    /**
     * Updates the notification badge setting
     */
    fun updateNotificationBadgeSetting(enabled: Boolean) {
        val currentSetting = _feedSetting.value ?: return
        val updatedSetting = currentSetting.copy(showNotificationBadge = enabled)
        updateSetting(updatedSetting)
    }
    
    private fun updateSetting(setting: FeedSetting) {
        viewModelScope.launch {
            try {
                ggIAMService.storeFeedNotificationSetting(setting)
                _feedSetting.value = setting
                Timber.d("[$tag] Updated feed setting: $setting")
            } catch (e: Exception) {
                Timber.e(e, "[$tag] Failed to update feed setting")
            }
        }
    }
}