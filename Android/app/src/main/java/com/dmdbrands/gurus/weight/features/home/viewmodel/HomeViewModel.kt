package com.dmdbrands.gurus.weight.features.home.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.service.AccountFlagService
import com.dmdbrands.gurus.weight.core.service.WeightOnlyModeEventService
import com.dmdbrands.gurus.weight.core.service.WeightOnlyModeEventType
import com.dmdbrands.gurus.weight.core.shared.utilities.AppReviewManager
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.model.storage.toGGBTDevice
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IAppSyncService
import com.dmdbrands.gurus.weight.domain.services.IFeedService
import com.dmdbrands.gurus.weight.features.appPermissions.helper.AppPermissionsHelper
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
import com.dmdbrands.gurus.weight.features.common.model.SCALES
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.home.reducer.HomeIntent
import com.dmdbrands.gurus.weight.features.home.reducer.HomeReducer
import com.dmdbrands.gurus.weight.features.home.reducer.HomeState
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.toScaleApiEntry
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.toScaleEntry
import com.dmdbrands.library.ggbluetooth.enums.GGBTSettingType
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionType
import com.dmdbrands.library.ggbluetooth.model.GGBTSetting
import com.dmdbrands.library.ggbluetooth.model.GGBTSettingValue
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.greatergoods.blewrapper.GGDeviceService
import com.greatergoods.blewrapper.GGPermissionService
import com.greatergoods.libs.appsync.model.AppSyncResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.app.Activity
import android.util.Log

@HiltViewModel
class HomeViewModel
@Inject
constructor(
  private val deviceService: IDeviceService,
  private val ggDeviceService: GGDeviceService,
  private val ggPermissionService: GGPermissionService,
  private val dialogUtility: IDialogUtility,
  private val appSyncService: IAppSyncService,
  private val accountService: IAccountService,
  private val feedService: IFeedService,
  private val accountFlagService: AccountFlagService,
  private val appReviewManager: AppReviewManager,
) : BaseIntentViewModel<HomeState, HomeIntent>(HomeReducer()) {
  private val TAG = "HomeViewModel"
  override fun provideInitialState(): HomeState = HomeState()

  init {
    provideInitialState()
    observeAppSyncStatus()
    observePermissions()
    subscribeToWeightOnlyModeEvents()
    subscribeToWeightOnlyModeAlertDismissed()
    observeFeedIndicator()
    viewModelScope.launch {
      val isModalTriggered = feedService.checkAndTriggerFeedModal()
      if(!isModalTriggered){
      checkAccountFlags("login")
      }
    }

  }

  override fun handleIntent(intent: HomeIntent) {
    super.handleIntent(intent)
    when (intent) {
      is HomeIntent.CheckAndRequestPermission -> {
        checkAndRequestCameraPermission(intent.onResult)
      }

      is HomeIntent.HandleAppSyncResult -> handleAppSyncResult(intent.result)

      is HomeIntent.OnWeightOnlyModeEnable -> onWeightOnlyModeEnable()

      is HomeIntent.OnWeightOnlyModeAlertDismiss -> onWeightOnlyModeAlertDismiss()

      is HomeIntent.LaunchAppReview -> launchAppReview(intent.activity)

      else -> {}
    }
  }

  fun enableSessionImpedence(device: Device) {
    CoroutineScope(Dispatchers.IO).launch {
      ggDeviceService.updateSettings(
        device.toGGBTDevice(),
        GGBTSetting(
          key = GGBTSettingType.SESSION_IMPEDANCE,
          value = GGBTSettingValue.Boolean(true),
        ),
      )

    }
  }

  private fun observeAppSyncStatus() {
    viewModelScope.launch {
      deviceService.pairedScales.collect { savedScales ->
        val hasAppSyncScales = savedScales.any { savedScale ->
          val scaleInfo = SCALES.find { it.sku == savedScale.sku }
          scaleInfo?.setupType == ScaleSetupType.AppSync
        } && savedScales.isNotEmpty()

        handleIntent(HomeIntent.SetShowAppsync(hasAppSyncScales))

      }
    }
  }

  private fun observePermissions() {
    viewModelScope.launch {
      ggPermissionService.permissionCallBackFlow.collect {
        val isAppSyncPermissionsEnabled =
          AppPermissionsHelper.areRequiredPermissionsEnabled(
            it, setupType = ScaleSetupType.AppSync,
          )
        handleIntent(HomeIntent.isAppSyncPermissionsEnabled(isAppSyncPermissionsEnabled))
      }
    }
  }

  private fun checkAndRequestCameraPermission(onResult: (Boolean) -> Unit) {
    if (state.value.isAppSyncPermissionsEnabled) {
      onResult(true)
      return
    }
    viewModelScope.launch {

      dialogUtility.permissionAlert(
        GGPermissionType.CAMERA,
        onRequest = {
          viewModelScope.launch {
            ggPermissionService.requestPermission(GGPermissionType.CAMERA) {
              val permissionStatus = it as GGPermissionStatusMap
              val isEnabled = AppPermissionsHelper.areRequiredPermissionsEnabled(
                permissionStatus, setupType = ScaleSetupType.AppSync,
              )
              onResult(isEnabled)
            }
          }
        },
        onDismiss = { onResult(false) },
      )
    }
  }

  private fun handleAppSyncResult(result: AppSyncResult) {
    if (result.weight != null && !result.canceled) {
      handleNewEntry(result)
    } else if (result.manual) {
      navigateToManualEntry()
    } else {
    }
  }

  private fun handleNewEntry(result: AppSyncResult) {
    viewModelScope.launch {
      try {
        val currentAccount = accountService.activeAccountFlow.first()
        val accountId = currentAccount?.id ?: return@launch
        val storedEntry = result.toScaleApiEntry(accountId)
        // Create ScaleEntry directly from AppSyncResult with calculated BMI
        val scaleEntry =
          result.toScaleEntry(accountId, currentAccount.weightUnit.value.toString(), currentAccount.height)
        Log.i("CHECKING01", "$scaleEntry")
        Log.i("CHECKING02", "${scaleEntry.toScaleApiEntry()}")
        appSyncService.setAppSyncData(storedEntry)
        dialogUtility.showEntrySyncPopup(
          entry = scaleEntry,
          apiEntry = scaleEntry.toScaleApiEntry(),
          onEdit = {
            viewModelScope.launch {
              appSyncService.setAppSyncDataForEditing(scaleEntry)
              appSyncService.handleEditAppSyncData(scaleEntry)
            }
          },
          onSave = {
            viewModelScope.launch {
              val saveEntry =
                result.toScaleEntry(accountId, currentAccount.weightUnit.value.toString(), currentAccount.height, true)
              appSyncService.handleSaveAppSyncData(saveEntry)
            }
          },
        )
        AppLog.i(TAG, "Appsync new entry: ", result.toString())
      } catch (e: Exception) {
        AppLog.e(TAG, "Error handling new entry: ${e.message}", e)
        dialogQueueService.showToast(
          Toast(message = "Failed to process AppSync data: ${e.message}"),
        )
      }
    }
  }

  private fun navigateToManualEntry() {
    viewModelScope.launch {
      navigationService.navigateTo(AppRoute.Main.Entry, AppRoute.Home)
    }
  }

  /**
   * Subscribes to weight-only mode events for showing/hiding bottom sheet.
   */
  private fun subscribeToWeightOnlyModeEvents() {
    viewModelScope.launch {
      WeightOnlyModeEventService.events.collect { event ->
        when (event) {
          WeightOnlyModeEventType.SHOW_ALERT -> {
            handleIntent(HomeIntent.SetShowWeightOnlyModeBottomSheet(true))
            AppLog.d(TAG, "Weight-only mode bottom sheet should be shown")
          }

          WeightOnlyModeEventType.HIDE_ALERT -> {
            handleIntent(HomeIntent.SetShowWeightOnlyModeBottomSheet(false))
            AppLog.d(TAG, "Weight-only mode bottom sheet should be hidden")
          }

          else -> {
            // Handle other events if needed
          }
        }
      }
    }
  }

  /**
   * Subscribe to DeviceService's isWeightOnlyModeAlertShown flow to update HomeState.
   */
  private fun subscribeToWeightOnlyModeAlertDismissed() {
    viewModelScope.launch {
      deviceService.isWeightOnlyModeAlertShown.collect { isAlertShown ->
        handleIntent(HomeIntent.SetWeightOnlyModeDismissed(isAlertShown))
        AppLog.d(TAG, "Weight-only mode alert dismissed state updated: $isAlertShown")
      }
    }
  }

  /**
   * Handles enabling weight-only mode for connected scales.
   * Delegates to AppViewModel for the actual implementation.
   */
  private fun onWeightOnlyModeEnable() {
    viewModelScope.launch {
      try {
        AppLog.d(TAG, "Enabling weight-only mode via AppViewModel")
        val pairedScales = deviceService.pairedScales.first()
        val scalesToUpdate = pairedScales.filter { device ->
          device.connectionStatus == BLEStatus.CONNECTED &&
            device.isWeighOnlyModeEnabledByOthers
        }

        if (scalesToUpdate.isNotEmpty()) {
          // Show loading toast
          dialogQueueService.showLoader(
            "Updating Mode...",
          )

          for (scale in scalesToUpdate) {
            // Update scale settings to enable body metrics
            try {
              enableSessionImpedence(scale)
              dialogQueueService.dismissLoader()
              handleIntent(HomeIntent.OpenWeightOnlyModePopup(false))
              AppLog.d(TAG, "Updated settings for scale: ${scale.device?.deviceName}")
            } catch (e: Exception) {
              AppLog.e(TAG, "Failed to update scale settings", e)
            }
          }
          // Show success toast
          dialogQueueService.showToast(
            Toast(message = "Body metrics enabled successfully!"),
          )
        }

        // Dismiss the bottom sheet
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to enable weight-only mode", e)
        dialogQueueService.showToast(
          Toast(message = "Failed to update scale settings"),
        )
      }
      finally {
          dialogQueueService.dismissLoader()
        dialogQueueService.dismissCurrent()
      }
    }
  }

  fun handleWeightOnlyDismiss() {
    handleIntent(HomeIntent.OpenWeightOnlyModePopup(false))
    handleIntent(HomeIntent.SetShowWeightOnlyModeBottomSheet(false))
  }

  /**
   * Handles dismissing the weight-only mode bottom sheet.
   */
  private fun onWeightOnlyModeAlertDismiss() {
    deviceService.weightOnlyModeDismissAlert { handleWeightOnlyDismiss() }
    AppLog.d(TAG, "Weight-only mode bottom sheet dismissed")
  }

  /**
   * Launches the app review flow.
   */
  private fun launchAppReview(activity: Activity) {
    viewModelScope.launch {
      try {
        AppLog.i(TAG, "Launching app review flow from HomeScreen")
        appReviewManager.launchInAppReview(activity)
        AppLog.i(TAG, "App review flow launched successfully")
        // Reset the flag after launching
        handleIntent(HomeIntent.SetShouldAskForReview(false))
      } catch (e: Exception) {
        AppLog.e(TAG, "Error launching app review flow", e)
      }
    }
  }

  /**
   * Observes feed changes and updates the unread feed indicator.
   */
  private fun observeFeedIndicator() {
    viewModelScope.launch {
      try {
        // Initial update
        updateUnreadFeedIndicator()

        // Observe feed changes
        feedService.feedsChanged.collect {
          updateUnreadFeedIndicator()
        }
      } catch (e: Exception) {
        AppLog.e("HomeViewModel", "Failed to observe feed indicator", e.toString())
      }
    }
  }

  /**
   * Updates the unread feed count and indicator visibility.
   */
  private suspend fun updateUnreadFeedIndicator() {
    try {
      val count = feedService.getUnreadFeedCount()
      val feedSettings = feedService.getFeedSettings()
      val shouldShow = count > 0 && (feedSettings?.showNotificationBadge ?: true)
      handleIntent(HomeIntent.SetShowUnreadFeedIndicator(shouldShow))
      AppLog.d("HomeViewModel", "Updated unread feed count: $count, show indicator: $shouldShow")
    } catch (e: Exception) {
      AppLog.e("HomeViewModel", "Failed to update unread feed indicator", e.toString())
    }
  }

  // * Checks for account flags and triggers appropriate actions.
  // * @param trigger The trigger type (e.g., "login", "entry")
  // */
  private fun checkAccountFlags(trigger: String) {
    viewModelScope.launch {
      try {
        // First get the account flag
        val accountFlag = accountFlagService.getAccountFlag()
        if (accountFlag != null) {
          AppLog.d(TAG, "Found account flag: ${accountFlag.type} for trigger: $trigger")
          // Check if the flag should be triggered
          val flagTriggered = accountFlagService.checkAccountFlag(trigger)
          if (flagTriggered) {
            AppLog.d(TAG, "Account flag triggered for: $trigger")
            // Set the review flag to true when account flag is triggered
            handleIntent(HomeIntent.SetShouldAskForReview(true))
          }
        } else {
          AppLog.d(TAG, "No account flags found for trigger: $trigger")
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to check account flags for trigger: $trigger", e.toString())
      }
    }
  }
}
