package com.greatergoods.ggInAppMessaging.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.greatergoods.ggInAppMessaging.core.utilities.IAMLogger
import com.greatergoods.ggInAppMessaging.domain.models.FeedSetting
import com.greatergoods.ggInAppMessaging.proto.FeedSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.InputStream
import java.io.OutputStream

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
class FeedSettingsDataStore(
  private val context: Context,
) : BaseProtoDataStore<FeedSettings>(
  dataStore = context.feedSettingsDataStore,
) {
  private val tag = "FeedSettingsDataStore"

  /**
   * Emits a Flow of the current feed settings.
   */
  val feedSettingsFlow: Flow<FeedSetting> = dataFlow.map { proto ->
    FeedSetting(
      showPopupMessage = proto.showPopupMessage,
      showNotificationBadge = proto.showNotificationBadge
    )
  }

  /**
   * Gets the current feed settings.
   */
  suspend fun getFeedSettings(): FeedSetting {
    val proto = getData()
    return FeedSetting(
      showPopupMessage = proto.showPopupMessage,
      showNotificationBadge = proto.showNotificationBadge
    )
  }

  /**
   * Updates the feed settings.
   * @param feedSetting The new feed settings to store.
   * @param accountId The account ID for which these settings apply.
   */
  suspend fun updateFeedSettings(feedSetting: FeedSetting, accountId: String = "") {
    try {
      IAMLogger.d(tag, "Updating feed settings for account: $accountId")
      val updated = getData().toBuilder()
        .setShowPopupMessage(feedSetting.showPopupMessage)
        .setShowNotificationBadge(feedSetting.showNotificationBadge)
        .setAccountId(accountId)
        .setLastUpdated(System.currentTimeMillis().toString())
        .build()

      updateData { updated }
      IAMLogger.d(tag, "Successfully updated feed settings")
    } catch (e: Exception) {
      IAMLogger.e(tag, "Failed to update feed settings", e.toString())
      throw e
    }
  }

  /**
   * Updates only the pop-up message setting.
   * @param showPopupMessage Whether to show pop-up messages.
   * @param accountId The account ID for which this setting applies.
   */
  suspend fun updatePopupMessageSetting(showPopupMessage: Boolean, accountId: String = "") {
    try {
      IAMLogger.d(tag, "Updating popup message setting: $showPopupMessage for account: $accountId")
      val current = getData()
      val updated = current.toBuilder()
        .setShowPopupMessage(showPopupMessage)
        .setAccountId(accountId)
        .setLastUpdated(System.currentTimeMillis().toString())
        .build()

      updateData { updated }
      IAMLogger.d(tag, "Successfully updated popup message setting")
    } catch (e: Exception) {
      IAMLogger.e(tag, "Failed to update popup message setting", e.toString())
      throw e
    }
  }

  /**
   * Updates only the notification badge setting.
   * @param showNotificationBadge Whether to show notification badges.
   * @param accountId The account ID for which this setting applies.
   */
  suspend fun updateNotificationBadgeSetting(showNotificationBadge: Boolean, accountId: String = "") {
    try {
      IAMLogger.d(tag, "Updating notification badge setting: $showNotificationBadge for account: $accountId")
      val current = getData()
      val updated = current.toBuilder()
        .setShowNotificationBadge(showNotificationBadge)
        .setAccountId(accountId)
        .setLastUpdated(System.currentTimeMillis().toString())
        .build()

      updateData { updated }
      IAMLogger.d(tag, "Successfully updated notification badge setting")
    } catch (e: Exception) {
      IAMLogger.e(tag, "Failed to update notification badge setting", e.toString())
      throw e
    }
  }

  /**
   * Gets the pop-up message setting.
   */
  suspend fun getPopupMessageSetting(): Boolean = getData().showPopupMessage

  /**
   * Gets the notification badge setting.
   */
  suspend fun getNotificationBadgeSetting(): Boolean = getData().showNotificationBadge

  /**
   * Gets the account ID for the current settings.
   */
  suspend fun getAccountId(): String = getData().accountId

  /**
   * Gets the last updated timestamp.
   */
  suspend fun getLastUpdated(): String = getData().lastUpdated

  override fun getDefaultInstance(): FeedSettings = FeedSettings.getDefaultInstance()

  /**
   * Clears all feed settings data.
   */
  override suspend fun clearData() {
    try {
      IAMLogger.i(tag, "Clearing feed settings data")
      super.clearData()
      IAMLogger.i(tag, "Successfully cleared feed settings data")
    } catch (e: Exception) {
      IAMLogger.e(tag, "Failed to clear feed settings data", e.toString())
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
