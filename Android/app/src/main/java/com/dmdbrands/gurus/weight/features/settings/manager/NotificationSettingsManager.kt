package com.dmdbrands.gurus.weight.features.settings.manager

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.api.notification.NotificationSettingsRequest
import com.dmdbrands.gurus.weight.domain.services.IFeedService
import com.dmdbrands.gurus.weight.domain.services.INotificationService
import com.dmdbrands.gurus.weight.features.common.components.RadioButtonOption
import com.dmdbrands.gurus.weight.features.common.components.showRadioGroupModal
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.settings.strings.RadioGroupModalStrings
import com.dmdbrands.gurus.weight.features.settings.viewmodel.SettingsIntent
import com.dmdbrands.gurus.weight.features.settings.viewmodel.SettingsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

interface INotificationSettingsManager {
  fun initFeedNotificationListener(
    scope: CoroutineScope,
    dispatch: (SettingsIntent) -> Unit,
  )

  fun onNotificationsClick(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
  )
}

class NotificationSettingsManager
@Inject
constructor(
  private val feedService: IFeedService,
  private val notificationService: INotificationService,
  private val dialogQueueService: IDialogQueueService,
) : INotificationSettingsManager {
  companion object {
    private const val TAG = "NotificationSettingsManager"
  }

  override fun initFeedNotificationListener(
    scope: CoroutineScope,
    dispatch: (SettingsIntent) -> Unit,
  ) {
    scope.launch {
      try {
        feedService.feedsChanged.collect {
          AppLog.d(TAG, "Feed items changed, updating unread count")
          updateUnreadFeedCount(dispatch)
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error observing feedsChanged flow", e.toString())
      }
    }

    scope.launch {
      try {
        feedService.notificationBadgeUpdated.collect { shouldShow ->
          AppLog.d(TAG, "Notification badge updated: $shouldShow")
          val count = feedService.getUnreadFeedCount()
          dispatch(SettingsIntent.SetUnreadFeedCount(count))
          dispatch(SettingsIntent.SetShowUnreadFeedIndication(shouldShow))
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error observing notificationBadgeUpdated flow", e.toString())
      }
    }

    scope.launch {
      try {
        updateUnreadFeedCount(dispatch)
      } catch (e: Exception) {
        AppLog.e(TAG, "Error in initial feed notification update", e.toString())
      }
    }
  }

  override fun onNotificationsClick(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
  ) {
    AppLog.d(TAG, "Notifications clicked")
    showNotificationsModal(scope, stateProvider)
  }

  private suspend fun updateUnreadFeedCount(dispatch: (SettingsIntent) -> Unit) {
    try {
      val count = feedService.getUnreadFeedCount()
      val feedSettings = feedService.getFeedSettings()
      val shouldShow = count > 0 && (feedSettings?.showNotificationBadge ?: true)
      AppLog.d(TAG, "Updating unread feed count: $count, show indicator: $shouldShow")
      dispatch(SettingsIntent.SetUnreadFeedCount(count))
      dispatch(SettingsIntent.SetShowUnreadFeedIndication(shouldShow))
      AppLog.d(TAG, "Updated unread feed count: $count, show indicator: $shouldShow")
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to update unread feed count", e.toString())
    }
  }

  private fun showNotificationsModal(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
  ) {
    val state = stateProvider()
    val currentNotificationStatus = state.currentNotificationStatus
    AppLog.d(TAG, "Showing notifications modal with reactive status: $currentNotificationStatus")

    showRadioGroupModal(
      dialogService = dialogQueueService,
      title = RadioGroupModalStrings.Titles.Notifications,
      options =
        listOf(
          RadioButtonOption("On", RadioGroupModalStrings.Notifications.On),
          RadioButtonOption("w/ weight", RadioGroupModalStrings.Notifications.WithWeight),
          RadioButtonOption("Off", RadioGroupModalStrings.Notifications.Off),
        ),
      selectedItem =
        when {
          state.account?.shouldSendEntryNotifications == true &&
            state.account.shouldSendWeightInEntryNotifications == true -> "w/ weight"

          state.account?.shouldSendEntryNotifications == true -> "On"
          else -> "Off"
        },
      confirmText = RadioGroupModalStrings.Button.Save,
      onConfirm = { selectedNotification ->
        selectedNotification?.let { notificationOption ->
          onNotificationUpdate(scope, notificationOption.toString())
        }
      },
      onCancel = {
        AppLog.d(TAG, "Notification selection cancelled")
      },
    )
  }

  private fun getNotificationSettingsFromOption(option: String): NotificationSettingsRequest =
    when (option) {
      "On" ->
        NotificationSettingsRequest(
          shouldSendEntryNotifications = true,
          shouldSendWeightInEntryNotifications = false,
        )

      "w/ weight" ->
        NotificationSettingsRequest(
          shouldSendEntryNotifications = true,
          shouldSendWeightInEntryNotifications = true,
        )

      else ->
        NotificationSettingsRequest(
          shouldSendEntryNotifications = false,
          shouldSendWeightInEntryNotifications = false,
        )
    }

  private fun onNotificationUpdate(
    scope: CoroutineScope,
    notificationOption: String,
  ) {
    dialogQueueService.showLoader("Loading...")
    scope.launch {
      try {
        val notificationSettings = getNotificationSettingsFromOption(notificationOption)
        val updatedAccount = notificationService.updateNotificationSettings(notificationSettings)
        if (updatedAccount != null) {
          dialogQueueService.dismissLoader()
          dialogQueueService.showToast(Toast.Simple("Notification settings updated", "Success!"))
          AppLog.i(TAG, "Successfully updated notification settings - flow will update UI")
        } else {
          AppLog.e(TAG, "Notification settings update returned null account")
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error updating notification settings", e)
      } finally {
        dialogQueueService.dismissLoader()
      }
    }
  }
}
