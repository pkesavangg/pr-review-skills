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
import com.dmdbrands.gurus.weight.domain.services.IHealthConnectService
import com.dmdbrands.gurus.weight.features.appPermissions.helper.AppPermissionsHelper
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
import com.dmdbrands.gurus.weight.features.common.helper.ScaleDataHelper
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.home.reducer.HomeIntent
import com.dmdbrands.gurus.weight.features.home.reducer.HomeReducer
import com.dmdbrands.gurus.weight.features.home.reducer.HomeState
import com.dmdbrands.gurus.weight.features.home.strings.HomeStrings
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.toScaleApiEntry
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.toScaleEntry
import com.dmdbrands.library.ggbluetooth.enums.GGBTSettingType
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionType
import com.dmdbrands.library.ggbluetooth.model.GGBTSetting
import com.dmdbrands.library.ggbluetooth.model.GGBTSettingValue
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.greatergoods.blewrapper.GGDeviceService
import com.greatergoods.blewrapper.GGPermissionService
import com.greatergoods.ggInAppMessaging.domain.services.IInAppMessagingService
import com.greatergoods.libs.appsync.AppSyncResultHolder
import com.greatergoods.libs.appsync.model.AppSyncResult
import com.greatergoods.libs.appsync.startAppSyncScan
import com.greatergoods.libs.appsync.utility.AppSyncResultFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import android.app.Activity

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
  private val ggInAppMessagingService: IInAppMessagingService,
  private val healthConnectService: IHealthConnectService,
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
    observeAppSyncZoomLevel()
    checkHealthConnectPermission()
    viewModelScope.launch {
      ggInAppMessagingService.setAccountId(accountService.activeAccount.first()?.id ?: "")
      val isModalTriggered = feedService.checkAndTriggerFeedModal()
      if (!isModalTriggered) {
        checkAccountFlags("login")
      }
    }
    viewModelScope.launch {
      delay(IAM_FEED_MODAL_RETRY_DELAY_MS)
      feedService.checkAndTriggerFeedModal()
    }
  }

  /**
   * Checks Health Connect permission with a short delay so the home screen is visible first.
   * Shows the Health Connect popup from here when permission is disabled.
   */
  private fun checkHealthConnectPermission() {
    viewModelScope.launch {
      try {
        delay(HEALTH_CONNECT_CHECK_DELAY_MS)
        healthConnectService.checkHealthConnectPermissionDisabled()
        AppLog.d(TAG, "Health Connect permission check completed")
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to check Health Connect permission", e)
      }
    }
  }

  companion object {
    private const val HEALTH_CONNECT_CHECK_DELAY_MS = 1000L
    private const val IAM_FEED_MODAL_RETRY_DELAY_MS = 1500L

    // Max time to wait for the BLE permission flow to load before deciding on the camera prompt.
    private const val PERMISSION_LOAD_TIMEOUT_MS = 4000L
    private val APPSYNC_SKUS = setOf(
      "0340", "0341", "0342", "0343", "0345", "0346", "0347",
      "0358", "0359", "0364", "0369", "0370", "0371",
    )
  }

  override fun handleIntent(intent: HomeIntent) {
    super.handleIntent(intent)
    when (intent) {
      is HomeIntent.CheckAndRequestPermission -> {
        checkAndRequestCameraPermission(intent.onResult)
      }

      is HomeIntent.StartAppSyncScan -> startAppSyncScanFlow(intent.activity)

      is HomeIntent.HandleAppSyncResult -> handleAppSyncResult(intent.result)

      is HomeIntent.OnWeightOnlyModeEnable -> onWeightOnlyModeEnable()

      is HomeIntent.OnWeightOnlyModeAlertDismiss -> onWeightOnlyModeAlertDismiss()

      is HomeIntent.LaunchAppReview -> launchAppReview(intent.activity)

      else -> {}
    }
  }

  fun enableSessionImpedence(device: Device) {
    viewModelScope.launch(Dispatchers.IO) {
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
          val sku = savedScale.getSKU()
          savedScale.deviceType.equals(ScaleSetupType.AppSync.value, ignoreCase = true) ||
            ScaleDataHelper.findScaleInfoBySku(sku)?.setupType == ScaleSetupType.AppSync ||
            sku in APPSYNC_SKUS
        }
        handleIntent(HomeIntent.SetShowAppsync(hasAppSyncScales))
      }
    }
  }

  private fun observePermissions() {
    viewModelScope.launch {
      ggPermissionService.permissionCallBackFlow.collect { data ->
        // Safely handle the flow data - it can be String or GGPermissionStatusMap
        when (data) {
          is GGPermissionStatusMap -> {
            val isAppSyncPermissionsEnabled =
              AppPermissionsHelper.areRequiredPermissionsEnabled(
                data, setupType = ScaleSetupType.AppSync,
              )
            handleIntent(HomeIntent.isAppSyncPermissionsEnabled(isAppSyncPermissionsEnabled))
          }
          else -> {
            // Ignore non-Map types in the observer
            AppLog.d(TAG, "Permission flow emitted non-Map type: ${data?.javaClass?.simpleName}")
          }
        }
      }
    }
  }

  private fun checkAndRequestCameraPermission(onResult: (Boolean) -> Unit) {
    if (state.value.isAppSyncPermissionsEnabled) {
      onResult(true)
      return
    }

    viewModelScope.launch {
      try {
        // The BLE permission flow holds an empty map until its first poll emits after a scan
        // starts (~1.2s+ after cold start). If the status hasn't loaded yet, show a loader and
        // wait for the real status before deciding, so we don't prompt off a stale empty map
        // (MOB-710). Timeout-guarded so a slow/absent scan can't hang the tap.
        if (ggPermissionService.permissionCallBackFlow.value.isEmpty()) {
          dialogQueueService.showLoader(HomeStrings.CheckingCameraPermission)
          // try/finally so the loader is always dismissed — including if this coroutine is
          // cancelled mid-await (otherwise the loader could be left up). (PR #2093 review)
          val loaded = try {
            withTimeoutOrNull(PERMISSION_LOAD_TIMEOUT_MS) {
              ggPermissionService.permissionCallBackFlow.first { it.isNotEmpty() }
            }
          } finally {
            dialogQueueService.dismissLoader()
          }
          if (loaded != null &&
            AppPermissionsHelper.areRequiredPermissionsEnabled(loaded, setupType = ScaleSetupType.AppSync)
          ) {
            onResult(true)
            return@launch
          }
        }

        var permissionResultReceived = false

        // Launch a job to wait for the NEXT permission update from flow
        val permissionJob = launch {
          // Skip the current value and wait for the next update after permission request
          ggPermissionService.permissionCallBackFlow
            .drop(1) // Skip the current value
            .first() // Wait for the next emission
            .let { data ->
              // Safely handle the flow data - it can be String or GGPermissionStatusMap
              when (data) {
                is GGPermissionStatusMap -> {
                  val isEnabled = AppPermissionsHelper.areRequiredPermissionsEnabled(
                    data, setupType = ScaleSetupType.AppSync,
                  )

                  if (!permissionResultReceived) {
                    permissionResultReceived = true
                    onResult(isEnabled)
                  }
                }
                is String -> {
                  // Handle error message or denial
                  AppLog.w(TAG, "Permission flow returned string: $data")
                  if (!permissionResultReceived) {
                    permissionResultReceived = true
                    onResult(false)
                  }
                }
                else -> {
                  AppLog.w(TAG, "Unexpected permission flow type: ${data?.javaClass?.simpleName}")
                  if (!permissionResultReceived) {
                    permissionResultReceived = true
                    onResult(false)
                  }
                }
              }
            }
        }

        dialogUtility.permissionAlert(
          GGPermissionType.CAMERA,
          onRequest = {
            try {
              // Call requestPermission without callback, just like AppPermissionsViewModel
              // The library handles navigation to settings internally
              ggPermissionService.requestPermission(GGPermissionType.CAMERA)
              // The result will come through permissionCallBackFlow
            } catch (e: Exception) {
              AppLog.e(TAG, "Error requesting camera permission", e.toString())
              permissionJob.cancel()
              if (!permissionResultReceived) {
                permissionResultReceived = true
                onResult(false)
              }
            }
          },
          onDismiss = {
            permissionJob.cancel()
            if (!permissionResultReceived) {
              permissionResultReceived = true
              onResult(false)
            }
          },
        )
      } catch (e: Exception) {
        AppLog.e(TAG, "Error in checkAndRequestCameraPermission", e.toString())
        onResult(false)
      }
    }
  }

  /**
   * Launches the AppSync camera scan and handles its result.
   *
   * Runs on [viewModelScope] rather than the home screen's rememberCoroutineScope. Previously the
   * scan was launched from a scope tied to the bottom-nav composition; after the device had been
   * idle for a while the composition could be disposed, cancelling that scope and failing the scan
   * with "rememberCoroutineScope left the composition" — and leaving the local isScanning guard
   * stuck true so the AppSync icon stopped responding to taps while other tabs kept working
   * (MOB-710). Hoisting isScanning into [HomeState] and launching on the ViewModel scope keeps the
   * scan alive across recomposition and guarantees the guard is reset in the finally block.
   */
  private fun startAppSyncScanFlow(activity: Activity) {
    if (state.value.isScanning) {
      AppLog.d(TAG, "AppSync scan already in progress — ignoring tap")
      return
    }
    handleIntent(HomeIntent.SetScanning(true))
    viewModelScope.launch {
      try {
        val result = startAppSyncScan(
          context = activity,
          zoom = state.value.appSyncZoomLevel,
          showManualEntryButton = true,
          onBack = {
            // Create cancelled result and call intent handler immediately
            val cancelResult = AppSyncResultFactory.createCancelResult(state.value.appSyncZoomLevel)
            AppSyncResultHolder.result = cancelResult
            handleIntent(HomeIntent.HandleAppSyncResult(cancelResult))
          },
        )
        AppLog.w(
          TAG,
          "Scale display detected results on home flow " +
            "(device=${android.os.Build.MODEL}, result=$result errors=${result.errors})",
        )
        handleIntent(HomeIntent.HandleAppSyncResult(result))
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        AppLog.e(TAG, "AppSync scan failed on home flow: ${e.message}", e)
      } finally {
        handleIntent(HomeIntent.SetScanning(false))
      }
    }
  }

  private fun handleAppSyncResult(result: AppSyncResult) {
    if (result.canceled) {
      return
    }
    viewModelScope.launch {
      appSyncService.saveLastZoomLevel(result.zoom)
    }
    if (result.weight != null) {
      handleNewEntry(result)
    } else if (result.manual) {
      navigateToManualEntry()
    }
  }

  private fun observeAppSyncZoomLevel() {
    viewModelScope.launch {
      appSyncService.lastZoomLevel.collect { zoom ->
        handleIntent(HomeIntent.SetAppSyncZoomLevel(zoom))
      }
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
          Toast.Simple(message = "Failed to process AppSync data: ${e.message}"),
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
            Toast.Simple(message = "Body metrics enabled successfully!"),
          )
        }

        // Dismiss the bottom sheet
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to enable weight-only mode", e)
        dialogQueueService.showToast(
          Toast.Simple(message = "Failed to update scale settings"),
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
