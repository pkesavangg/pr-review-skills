package com.dmdbrands.gurus.weight.features.ScaleSetup.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.config.AppConfig
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.domain.services.IAppSyncService
import com.dmdbrands.gurus.weight.features.ScaleSetup.ScaleSetupConstants
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.AppsyncScaleSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.AppsyncScaleSetupIntent
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.AppsyncScaleSetupReducer
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.AppsyncScaleSetupState
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.ScaleSetupStrings
import com.dmdbrands.gurus.weight.features.appPermissions.helper.AppPermissionsHelper
import com.dmdbrands.gurus.weight.features.common.components.DialogType
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.SCALES
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionState
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionType
import com.dmdbrands.library.ggbluetooth.model.GGDeviceDetail
import com.greatergoods.blewrapper.GGPermissionService
import com.greatergoods.libs.appsync.model.AppSyncResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * ViewModel for the AppsyncScaleSetupScreen. Handles scale setup flow state and navigation.
 * @property sku The SKU/model number of the scale being set up.
 */
@HiltViewModel(
  assistedFactory = AppsyncScaleSetupViewModel.Factory::class,
)
class AppsyncScaleSetupViewModel
@AssistedInject
constructor(
  private val permissionService: GGPermissionService,
  private val dialogUtility: IDialogUtility,
  private val deviceService: IDeviceService,
  private val appSyncService: IAppSyncService,
  @Assisted private val sku: String,
) : BaseIntentViewModel<AppsyncScaleSetupState, AppsyncScaleSetupIntent>(
  reducer = AppsyncScaleSetupReducer(),
) {
  @AssistedFactory
  interface Factory {
    fun create(sku: String): AppsyncScaleSetupViewModel
  }

  override fun provideInitialState(): AppsyncScaleSetupState = AppsyncScaleSetupState()

  override fun handleIntent(intent: AppsyncScaleSetupIntent) {
    AppLog.d(TAG, "Handling appsync scale setup intent: ${intent::class.simpleName}")
    super.handleIntent(intent)
    when (intent) {
      is AppsyncScaleSetupIntent.Next -> {
        AppLog.d(TAG, "Next intent received")
        onNext()
      }
      is AppsyncScaleSetupIntent.ExitSetup -> {
        AppLog.d(TAG, "Exit setup intent received, isSetupFinished: ${intent.isSetupFinished}")
        onExitSetup(intent.isSetupFinished)
      }

      AppsyncScaleSetupIntent.OpenHelp -> {
        AppLog.d(TAG, "Open help intent received")
        openHelpModal()
      }
      is AppsyncScaleSetupIntent.RequestPermission -> {
        AppLog.d(TAG, "Request permission intent received: ${intent.permissionType}")
        requestPermission(intent.permissionType)
      }

      is AppsyncScaleSetupIntent.HandleAppSyncResult -> {
        AppLog.d(TAG, "Handle AppSync result intent received")
        handleAppSyncResult(intent.result)
      }

      else -> {}
    }
  }

  init {
    AppLog.d(TAG, "AppsyncScaleSetupViewModel initialized for SKU: $sku")
    // Set setup in progress when initialization starts
    deviceService.setSetupInProgress(true)
    loadScaleInfo()
    observePermissions()
    observeSetup()
    observeAppSyncZoomLevel()
  }

  /**
   * Loads scale information based on the provided SKU.
   */
  private fun loadScaleInfo() {
    AppLog.d(TAG, "Loading scale info for SKU: $sku")
    val scaleInfo = SCALES.find { it.sku == sku }
    if (scaleInfo != null) {
      AppLog.d(TAG, "Found scale info: ${scaleInfo.productName}, bodyComp: ${scaleInfo.bodyComp}")
      handleIntent(AppsyncScaleSetupIntent.SetScaleSku(sku))
      handleIntent(AppsyncScaleSetupIntent.SetBodyComp(scaleInfo.bodyComp))
    } else {
      AppLog.w(TAG, "Scale info not found for SKU: $sku, using default bodyComp: true")
      handleIntent(AppsyncScaleSetupIntent.SetScaleSku(sku))
      handleIntent(AppsyncScaleSetupIntent.SetBodyComp(true))
    }
  }

  private fun observePermissions() {
    AppLog.d(TAG, "Starting permission observation")
    viewModelScope.launch {
      try {
        permissionService.permissionCallBackFlow.collect { permissions ->
          val areRequiredPermissionsEnabled =
            AppPermissionsHelper.areRequiredPermissionsEnabled(permissions, setupType = ScaleSetupType.AppSync)
          AppLog.d(TAG, "Permission status updated: ${permissions.size} permissions")
          handleIntent(AppsyncScaleSetupIntent.SetPermissions(permissions))
          handleIntent(AppsyncScaleSetupIntent.SetNextButtonState(areRequiredPermissionsEnabled))
          if (!areRequiredPermissionsEnabled) {
            if (state.value.currentStep != AppsyncScaleSetupStep.PERMISSIONS && state.value.currentStep != AppsyncScaleSetupStep.SCALE_INFO) {
              AppLog.d(TAG, "Permissions not granted, moving to permissions step")
              handleIntent(AppsyncScaleSetupIntent.SetCurrentStep(AppsyncScaleSetupStep.PERMISSIONS))
            }
          }
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error observing permissions", e)
      }
    }
  }

  /**
   * Handles moving to the next step in the setup process.
   */
  private fun onNext() {
    val currentState = state.value
    AppLog.d(TAG, "Moving to next step from: ${currentState.currentStep}")

    when (currentState.currentStep) {

      AppsyncScaleSetupStep.PERMISSIONS -> {
        val areRequiredPermissionsEnabled = AppPermissionsHelper
          .areRequiredPermissionsEnabled(state.value.permissions, setupType = ScaleSetupType.AppSync)
        val currentState = state.value
        AppLog.d(TAG, "Required permissions enabled: $areRequiredPermissionsEnabled")
        // If permissions are enabled and we're on PERMISSIONS step, auto-advance
        if (areRequiredPermissionsEnabled && currentState.currentStep == AppsyncScaleSetupStep.PERMISSIONS) {
          AppLog.d(TAG, "Permissions granted, auto-advancing to next step")
          handleIntent(AppsyncScaleSetupIntent.Next)
        } else {
          // Check and request permissions sequentially
          permissionAccess()
        }
      }

      else -> {
        AppLog.d(TAG, "Proceeding to next step for: ${currentState.currentStep}")
        // For other steps, just move to next
      }
    }
  }

  private fun handleAppSyncResult(result: AppSyncResult) {
    AppLog.d(TAG, "Handling AppSync result - canceled: ${result.canceled}, manual: ${result.manual}")
    viewModelScope.launch {
      appSyncService.saveLastZoomLevel(result.zoom)
    }
    if (result.canceled || !result.manual) {
      AppLog.d(TAG, "AppSync result indicates proceeding to next step")
      handleIntent(AppsyncScaleSetupIntent.Next)
    } else {
      AppLog.d(TAG, "AppSync result indicates manual mode, not proceeding")
    }
  }

  private fun observeAppSyncZoomLevel() {
    viewModelScope.launch {
      appSyncService.lastZoomLevel.collect { zoom ->
        handleIntent(AppsyncScaleSetupIntent.SetAppSyncZoomLevel(zoom))
      }
    }
  }

  private fun onExitSetup(
    isSetupFinished: Boolean,
  ) {
    AppLog.d(TAG, "Exit setup requested - isSetupFinished: $isSetupFinished, isLastStep: ${state.value.isLastStep}")
    // Clear setup in progress state when exiting
    deviceService.setSetupInProgress(false)
    if (isSetupFinished && state.value.isLastStep) {
      AppLog.d(TAG, "Setup is finished, checking and saving scale")
      checkAndSaveScale()
    } else {
      AppLog.d(TAG, "Setup not finished, showing exit confirmation dialog")
      dialogQueueService.enqueue(
        DialogModel.Confirm(
          title = ScaleSetupStrings.ExitSetupAlert.Title,
          message = ScaleSetupStrings.ExitSetupAlert.Message(false),
          confirmText = ScaleSetupStrings.ExitSetupAlert.Exit,
          cancelText = ScaleSetupStrings.ExitSetupAlert.GoBack,
          onConfirm = {
            AppLog.d(TAG, "User confirmed exit setup")
            navigateBack()
          },
        ),
      )
    }
  }

  private fun checkAndSaveScale() {
    AppLog.d(TAG, "Checking and saving scale for SKU: $sku")

    // Fail fast: Validate SKU before launching coroutine
    val currentSku = state.value.sku
    if (currentSku.isBlank()) {
      AppLog.e(TAG, "SKU is null or blank, cannot save scale")
      return
    }

    dialogQueueService.showLoader(ScaleSetupStrings.SaveScaleLoader)
    viewModelScope.launch {
      try {
        val alreadyPairedScale = deviceService.pairedScales.first().find { it.sku == currentSku }
        if (alreadyPairedScale != null) {
          AppLog.d(TAG, "Found already paired scale, deleting: ${alreadyPairedScale.id}")
          deviceService.deleteScale(alreadyPairedScale.id)
        }

        val scaleInfo = SCALES.find { it.sku == currentSku }
        val productName = scaleInfo?.productName ?: ScaleSetupStrings.UnknownScale

        AppLog.d(TAG, "Scale info found: $productName, bodyComp: ${state.value.bodyComp}, SKU: $currentSku")

        val appSyncDevice = Device(
          device = GGDeviceDetail(
            deviceName = productName,
            macAddress = "",
            identifier = "",
          ),
          sku = currentSku,
          deviceType = ScaleSetupType.AppSync.value,
          nickname = productName,
        )

        AppLog.d(TAG, "Saving AppSync device: ${appSyncDevice.id}")
        val savedDevice = deviceService.saveScale(appSyncDevice)
        runAfterSaveComplete(savedDevice, currentSku)
      } catch (e: Exception) {
        AppLog.e(TAG, "Error checking and saving scale", e)
        dialogQueueService.dismissLoader()
        navigateBack()
      }
    }
  }

  /**
   * Runs after saveScale returns: waits for the scale to appear in pairedScales (so DB/flow
   * updates are complete), then dismisses loader and navigates back. Uses a timeout so we
   * don't block forever if the list doesn't update.
   */
  private suspend fun runAfterSaveComplete(savedDevice: Device?, currentSku: String) {
    waitForScaleInPairedList(savedDevice, currentSku)
    AppLog.i(TAG, "Successfully saved AppSync scale")
    dialogQueueService.dismissLoader()
    doNavigateBack()
  }

  /**
   * Waits for the saved scale to appear in [deviceService.pairedScales] (with timeout).
   * Ensures all DB/flow updates triggered by saveScale are visible before we navigate.
   */
  private suspend fun waitForScaleInPairedList(savedDevice: Device?, currentSku: String) {
    val listWithScale = withTimeoutOrNull(ScaleSetupConstants.WAIT_FOR_SCALE_IN_LIST_MS) {
      deviceService.pairedScales.first { list ->
        list.any { device ->
          if (savedDevice != null) device.id == savedDevice.id
          else device.sku == currentSku && device.deviceType == ScaleSetupType.AppSync.value
        }
      }
    }
    if (listWithScale == null) {
      AppLog.w(TAG, "Timeout waiting for scale in paired list; navigating anyway")
    }
  }

  /**
   * Opens the Help modal.
   */
  private fun openHelpModal() {
    AppLog.d(TAG, "Opening help modal")
    dialogQueueService.enqueue(
      DialogModel.Custom(
        contentKey = DialogType.HelpPopup,
        params =
          mapOf(
            "showGuide" to true,
            "onGuideClick" to {
              AppLog.d(TAG, "User clicked on guide in help modal")
              openProductGuide()
            },
          ),
      ),
    )
  }

  /**
   * Navigates back from the setup screen. Use from non-coroutine call sites (e.g. dialog callbacks).
   */
  private fun navigateBack() {
    viewModelScope.launch { doNavigateBack() }
  }

  /**
   * Performs navigation back. Suspend so it can be awaited in a coroutine (e.g. after save).
   */
  private suspend fun doNavigateBack() {
    AppLog.d(TAG, "Navigating back from AppSync scale setup")
    try {
      navigationService.navigateBack()
      AppLog.d(TAG, "Successfully navigated back from AppSync scale setup")
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to navigate back from AppSync scale setup", e)
    }
  }

  private fun openProductGuide() {
    val sku = state.value.sku
    val url = "${AppConfig.PRODUCT_URL}/$sku"
    AppLog.d(TAG, "Opening product guide URL: $url")
    openInAppBrowser(url)
  }

  /**
   * Handles permission access similar to Angular's permissionAccess() method.
   * For AppSync setup, only camera permission is required.
   */
  private fun permissionAccess() {
    val currentPermissions = state.value.permissions

    // Check Camera permission for AppSync setup
    if (currentPermissions[GGPermissionType.CAMERA] != GGPermissionState.ENABLED) {
      AppLog.d(TAG, "Requesting Camera permission")
      handleIntent(AppsyncScaleSetupIntent.RequestPermission(GGPermissionType.CAMERA))
      return
    }

    AppLog.d(TAG, "All required permissions are enabled")
  }

  /**
   * Requests a specific permission with rationale alert using the permission service.
   */
  private fun requestPermission(permissionType: String) {
    AppLog.d(TAG, "Requesting permission: $permissionType")
    viewModelScope.launch {
      try {
        dialogUtility.permissionAlert(
          permissionType = permissionType,
          onRequest = {
            AppLog.d(TAG, "User confirmed permission request for: $permissionType")
            permissionService.requestPermission(permissionType)
          },
        )
      } catch (e: Exception) {
        AppLog.e(TAG, "Error requesting permission $permissionType", e)
      }
    }
  }

  private fun observeSetup() {
    AppLog.d(TAG, "Starting setup observation")
    viewModelScope.launch {
      try {
        state.collect { currentState ->
          when (currentState.currentStep) {
            AppsyncScaleSetupStep.PERMISSIONS -> {
              val areRequiredPermissionsEnabled = AppPermissionsHelper
                .areRequiredPermissionsEnabled(state.value.permissions, setupType = ScaleSetupType.AppSync)
              AppLog.d(TAG, "Permissions step - required permissions enabled: $areRequiredPermissionsEnabled")
              handleIntent(AppsyncScaleSetupIntent.SetNextButtonState(areRequiredPermissionsEnabled))
            }

            else -> {
              AppLog.d(TAG, "Non-permissions step, enabling next button")
              handleIntent(AppsyncScaleSetupIntent.SetNextButtonState(true))
            }
          }
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error observing setup", e)
      }
    }
  }

  override fun onCleared() {
    super.onCleared()
    deviceService.setSetupInProgress(false)
  }

  companion object {
    private const val TAG = "AppsyncScaleSetupViewModel"
  }
}
