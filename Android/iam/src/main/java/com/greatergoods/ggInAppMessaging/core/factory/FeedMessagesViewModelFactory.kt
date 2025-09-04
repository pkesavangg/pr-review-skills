package com.greatergoods.ggInAppMessaging.core.factory

import android.content.Context
import com.greatergoods.ggInAppMessaging.core.service.FeedStorageService
import com.greatergoods.ggInAppMessaging.core.service.GGInAppMessagingService
import com.greatergoods.ggInAppMessaging.core.storage.FeedSettingsDataStore
import com.greatergoods.ggInAppMessaging.ui.viewmodel.FeedMessagesViewModel

/**
 * Factory for creating FeedMessagesViewModel instances
 * Creates all necessary services and ViewModel in one place
 */
class FeedMessagesViewModelFactory(private val context: Context) {

    /**
     * Creates a new FeedMessagesViewModel instance with all dependencies
     */
    fun create(): FeedMessagesViewModel {
        val feedSettingsDataStore = FeedSettingsDataStore(context)
        val feedStorageService = FeedStorageService(feedSettingsDataStore)
        val ggInAppMessagingService = GGInAppMessagingService(feedStorageService)
        return FeedMessagesViewModel(ggInAppMessagingService, context)
    }
}
