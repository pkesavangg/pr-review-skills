package com.greatergoods.ggInAppMessaging.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.greatergoods.ggInAppMessaging.core.utilities.IAMLogger
import com.greatergoods.ggInAppMessaging.domain.models.FeedSetting
import com.greatergoods.ggInAppMessaging.proto.FeedSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import android.content.Context

/**
 * Extension property to provide FeedSettings DataStore instance from Context.
 */
val Context.feedSettingsDataStore: DataStore<FeedSettings> by dataStore(
    fileName = "feed_settings.pb",
    serializer = FeedSettingsSerializer,
)

/**
 * DataStore for managing feed settings using Proto DataStore.
 * Provides type-safe storage for pop-up messages and notification badges settings.
 */
class FeedSettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : BaseProtoDataStore<FeedSettings>(
    dataStore = context.feedSettingsDataStore,
) {
    private val tag = "FeedSettingsDataStore"

    /**
     * Emits a Flow of feed settings for all accounts.
     */
    val feedSettingsFlow: Flow<Map<String, FeedSetting>> = dataFlow.map { proto ->
        proto.accountSettingsMap.mapValues { (_, accountSettings) ->
            FeedSetting(
                showPopupMessage = accountSettings.showPopupMessage,
                showNotificationBadge = accountSettings.showNotificationBadge
            )
        }
    }

    /**
     * Gets the feed settings for a specific account.
     * Returns default values (true) if settings haven't been initialized yet.
     * @param accountId The account ID to get settings for.
     */
    suspend fun getFeedSettings(accountId: String): FeedSetting {
        val data = getData()
        val accountSettings = data.accountSettingsMap[accountId]

        return if (accountSettings == null) {
            FeedSetting(
                showPopupMessage = true, // Default to true for first-time users
                showNotificationBadge = true // Default to true for first-time users
            )
        } else {
            FeedSetting(
                showPopupMessage = accountSettings.showPopupMessage,
                showNotificationBadge = accountSettings.showNotificationBadge
            )
        }
    }

    /**
     * Updates the feed settings for a specific account.
     * @param feedSetting The new feed settings to store.
     * @param accountId The account ID for which these settings apply.
     */
    suspend fun updateFeedSettings(feedSetting: FeedSetting, accountId: String) {
        try {
            IAMLogger.d(tag, "Updating feed settings for account: $accountId")
            val current = getData()
            val accountSettings = com.greatergoods.ggInAppMessaging.proto.AccountFeedSettings.newBuilder()
                .setShowPopupMessage(feedSetting.showPopupMessage)
                .setShowNotificationBadge(feedSetting.showNotificationBadge)
                .setAccountId(accountId)
                .setLastUpdated(System.currentTimeMillis().toString())
                .build()

            val updated = current.toBuilder()
                .putAccountSettings(accountId, accountSettings)
                .setLastUpdated(System.currentTimeMillis().toString())
                .build()

            updateData { updated }
            IAMLogger.d(tag, "Successfully updated feed settings for account: $accountId")
        } catch (e: Exception) {
            IAMLogger.e(tag, "Failed to update feed settings for account: $accountId", e.toString())
            throw e
        }
    }

    /**
     * Updates only the pop-up message setting for a specific account.
     * @param showPopupMessage Whether to show pop-up messages.
     * @param accountId The account ID for which this setting applies.
     */
    suspend fun updatePopupMessageSetting(showPopupMessage: Boolean, accountId: String) {
        try {
            IAMLogger.d(
                tag,
                "Updating popup message setting: $showPopupMessage for account: $accountId"
            )
            val current = getData()
            val existingSettings = current.accountSettingsMap[accountId]

            val accountSettings = if (existingSettings != null) {
                existingSettings.toBuilder()
                    .setShowPopupMessage(showPopupMessage)
                    .setLastUpdated(System.currentTimeMillis().toString())
                    .build()
            } else {
                com.greatergoods.ggInAppMessaging.proto.AccountFeedSettings.newBuilder()
                    .setShowPopupMessage(showPopupMessage)
                    .setShowNotificationBadge(true) // Default value
                    .setAccountId(accountId)
                    .setLastUpdated(System.currentTimeMillis().toString())
                    .build()
            }

            val updated = current.toBuilder()
                .putAccountSettings(accountId, accountSettings)
                .setLastUpdated(System.currentTimeMillis().toString())
                .build()

            updateData { updated }
            IAMLogger.d(tag, "Successfully updated popup message setting for account: $accountId")
        } catch (e: Exception) {
            IAMLogger.e(tag, "Failed to update popup message setting for account: $accountId", e.toString())
            throw e
        }
    }

    /**
     * Updates only the notification badge setting for a specific account.
     * @param showNotificationBadge Whether to show notification badges.
     * @param accountId The account ID for which this setting applies.
     */
    suspend fun updateNotificationBadgeSetting(
        showNotificationBadge: Boolean,
        accountId: String
    ) {
        try {
            IAMLogger.d(
                tag,
                "Updating notification badge setting: $showNotificationBadge for account: $accountId"
            )
            val current = getData()
            val existingSettings = current.accountSettingsMap[accountId]

            val accountSettings = if (existingSettings != null) {
                existingSettings.toBuilder()
                    .setShowNotificationBadge(showNotificationBadge)
                    .setLastUpdated(System.currentTimeMillis().toString())
                    .build()
            } else {
                com.greatergoods.ggInAppMessaging.proto.AccountFeedSettings.newBuilder()
                    .setShowPopupMessage(true) // Default value
                    .setShowNotificationBadge(showNotificationBadge)
                    .setAccountId(accountId)
                    .setLastUpdated(System.currentTimeMillis().toString())
                    .build()
            }

            val updated = current.toBuilder()
                .putAccountSettings(accountId, accountSettings)
                .setLastUpdated(System.currentTimeMillis().toString())
                .build()

            updateData { updated }
            IAMLogger.d(tag, "Successfully updated notification badge setting for account: $accountId")
        } catch (e: Exception) {
            IAMLogger.e(tag, "Failed to update notification badge setting for account: $accountId", e.toString())
            throw e
        }
    }

    /**
     * Gets the pop-up message setting for a specific account.
     * Returns true as default if settings haven't been initialized yet.
     * @param accountId The account ID to get the setting for.
     */
    suspend fun getPopupMessageSetting(accountId: String): Boolean {
        val data = getData()
        val accountSettings = data.accountSettingsMap[accountId]

        return if (accountSettings == null) {
            true // Default to true for first-time users
        } else {
            accountSettings.showPopupMessage
        }
    }

    /**
     * Gets the notification badge setting for a specific account.
     * Returns true as default if settings haven't been initialized yet.
     * @param accountId The account ID to get the setting for.
     */
    suspend fun getNotificationBadgeSetting(accountId: String): Boolean {
        val data = getData()
        val accountSettings = data.accountSettingsMap[accountId]

        return if (accountSettings == null) {
            true // Default to true for first-time users
        } else {
            accountSettings.showNotificationBadge
        }
    }

    /**
     * Gets the last time a feed modal was triggered for a specific account.
     * Returns null if no feed modal has been triggered yet.
     * @param accountId The account ID to get the timestamp for.
     */
    suspend fun getFeedLastTriggeredAt(accountId: String): Long? {
        val data = getData()
        val accountSettings = data.accountSettingsMap[accountId]

        return if (accountSettings == null || accountSettings.feedLastTriggeredAt == 0L) {
            null
        } else {
            accountSettings.feedLastTriggeredAt
        }
    }

    /**
     * Stores the last time a feed modal was triggered for a specific account.
     * @param timestamp The timestamp when the feed modal was last triggered.
     * @param accountId The account ID for which this applies.
     */
    suspend fun storeFeedLastTriggeredAt(timestamp: Long, accountId: String) {
        try {
            IAMLogger.d(tag, "Storing feed last triggered at: $timestamp for account: $accountId")
            val current = getData()
            val existingSettings = current.accountSettingsMap[accountId]

            val accountSettings = if (existingSettings != null) {
                existingSettings.toBuilder()
                    .setFeedLastTriggeredAt(timestamp)
                    .setLastUpdated(System.currentTimeMillis().toString())
                    .build()
            } else {
                com.greatergoods.ggInAppMessaging.proto.AccountFeedSettings.newBuilder()
                    .setShowPopupMessage(true) // Default value
                    .setShowNotificationBadge(true) // Default value
                    .setAccountId(accountId)
                    .setFeedLastTriggeredAt(timestamp)
                    .setLastUpdated(System.currentTimeMillis().toString())
                    .build()
            }

            val updated = current.toBuilder()
                .putAccountSettings(accountId, accountSettings)
                .setLastUpdated(System.currentTimeMillis().toString())
                .build()

            updateData { updated }
            IAMLogger.d(tag, "Successfully stored feed last triggered at for account: $accountId")
        } catch (e: Exception) {
            IAMLogger.e(tag, "Failed to store feed last triggered at for account: $accountId", e.toString())
            throw e
        }
    }

    override fun getDefaultInstance(): FeedSettings = FeedSettings.getDefaultInstance()

    /**
     * Clears feed settings for a specific account.
     * @param accountId The account ID to clear settings for.
     */
    suspend fun clearAccountSettings(accountId: String) {
        try {
            IAMLogger.i(tag, "Clearing feed settings for account: $accountId")
            val current = getData()
            val updated = current.toBuilder()
                .removeAccountSettings(accountId)
                .setLastUpdated(System.currentTimeMillis().toString())
                .build()

            updateData { updated }
            IAMLogger.i(tag, "Successfully cleared feed settings for account: $accountId")
        } catch (e: Exception) {
            IAMLogger.e(tag, "Failed to clear feed settings for account: $accountId", e.toString())
            throw e
        }
    }

    /**
     * Gets all account IDs that have feed settings.
     * @return List of account IDs with settings.
     */
    suspend fun getAccountIds(): List<String> {
        val data = getData()
        return data.accountSettingsMap.keys.toList()
    }

    /**
     * Checks if settings exist for a specific account.
     * @param accountId The account ID to check.
     * @return True if settings exist for the account.
     */
    suspend fun hasAccountSettings(accountId: String): Boolean {
        val data = getData()
        return data.accountSettingsMap.containsKey(accountId)
    }

    /**
     * Clears all feed settings data.
     */
    override suspend fun clearData() {
        try {
            IAMLogger.i(tag, "Clearing all feed settings data")
            super.clearData()
            IAMLogger.i(tag, "Successfully cleared all feed settings data")
        } catch (e: Exception) {
            IAMLogger.e(tag, "Failed to clear all feed settings data", e.toString())
            throw e
        }
    }
}

/**
 * Base class for Proto DataStore operations.
 */
abstract class BaseProtoDataStore<T : com.google.protobuf.MessageLite>(
    protected val dataStore: DataStore<T>
) {
    private val tag = "BaseProtoDataStore"

    /**
     * Returns a Flow of the Proto data.
     */
    val dataFlow: Flow<T> get() = dataStore.data

    /**
     * Returns the current snapshot of the Proto data.
     */
    suspend fun getData(): T = dataStore.data.first()

    /**
     * Updates the Proto data atomically.
     */
    suspend fun updateData(transform: suspend (T) -> T): T {
        return dataStore.updateData(transform)
    }

    /**
     * Gets the default instance of the Proto message.
     */
    protected abstract fun getDefaultInstance(): T

    /**
     * Clears all fields in the Proto message by resetting it to its default instance.
     */
    open suspend fun clearData() {
        try {
            IAMLogger.i(tag, "Clearing DataStore: ${this::class.simpleName}")
            updateData { getDefaultInstance() }
            IAMLogger.i(tag, "Successfully cleared DataStore: ${this::class.simpleName}")
        } catch (e: Exception) {
            IAMLogger.e(tag, "Failed to clear DataStore: ${this::class.simpleName}", e.toString())
            throw e
        }
    }
}

/**
 * Serializer for FeedSettings proto.
 */
object FeedSettingsSerializer : Serializer<FeedSettings> {
    override val defaultValue: FeedSettings = FeedSettings.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): FeedSettings =
        FeedSettings.parseFrom(input)

    override suspend fun writeTo(
        t: FeedSettings,
        output: OutputStream,
    ) = t.writeTo(output)
}
