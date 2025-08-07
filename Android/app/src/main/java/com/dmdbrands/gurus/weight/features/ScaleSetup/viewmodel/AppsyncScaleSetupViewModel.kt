package com.dmdbrands.gurus.weight.features.ScaleSetup.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.config.AppConfig
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
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
  @Assisted private val sku: String,
) : BaseIntentViewModel<AppsyncScaleSetupState, AppsyncScaleSetupIntent>(
  reducer = AppsyncScaleSetupReducer(),
) {
  @AssistedFactory
  interface Factory {
    fun create(sku: String): AppsyncScaleSetupViewModel
  }

  private val TAG = "AppsyncScaleSetupViewModel"

  override fun provideInitialState(): AppsyncScaleSetupState = AppsyncScaleSetupState()

  override fun handleIntent(intent: AppsyncScaleSetupIntent) {
    super.handleIntent(intent)
    when (intent) {
      is AppsyncScaleSetupIntent.Next -> onNext()
      is AppsyncScaleSetupIntent.ExitSetup ->
        onExitSetup(intent.isSetupFinished)

      AppsyncScaleSetupIntent.OpenHelp -> openHelpModal()
      is AppsyncScaleSetupIntent.RequestPermission -> requestPermission(
        intent.permissionType,
      )

      is AppsyncScaleSetupIntent.HandleAppSyncResult -> handleAppSyncResult(intent.result)

      else -> {}
    }
  }

  init {
    loadScaleInfo()
    observePermissions()
    observeSetup()
  }

  /**
   * Loads scale information based on the provided SKU.
   */
  private fun loadScaleInfo() {
    handleIntent(AppsyncScaleSetupIntent.SetScaleSku(sku))
    AppLog.d(TAG, "Loading scale info for SKU: $sku")
  }

  private fun observePermissions() {
    viewModelScope.launch {
      permissionService.permissionCallBackFlow.collect { permissions ->
        handleIntent(AppsyncScaleSetupIntent.SetPermissions(permissions))
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
          .areRequiredPermissionsEnabled(state.value.permissions, sku = sku)
        val currentState = state.value
        // If permissions are enabled and we're on PERMISSIONS step, auto-advance
        if (areRequiredPermissionsEnabled && currentState.currentStep == AppsyncScaleSetupStep.PERMISSIONS) {
          AppLog.d(TAG, "Permissions granted, auto-advancing to next step")
          handleIntent(AppsyncScaleSetupIntent.Next)
        } else {
          requestPermission(GGPermissionType.CAMERA)
          AppLog.w(TAG, "Cannot proceed: required permissions not granted")
        }
      }

      else -> {
        // For other steps, just move to next
      }
    }
  }

  private fun handleAppSyncResult(result: AppSyncResult) {
    if (result.canceled || !result.manual) {
      handleIntent(AppsyncScaleSetupIntent.Next)
    }
  }

  private fun onExitSetup(
    isSetupFinished: Boolean,
  ) {
    if (isSetupFinished || state.value.isLastStep) {
      checkAndSaveScale()
    } else {
      dialogQueueService.enqueue(
        DialogModel.Confirm(
          title = ScaleSetupStrings.ExitSetupAlert.Title,
          message = ScaleSetupStrings.ExitSetupAlert.Message(true),
          confirmText = ScaleSetupStrings.ExitSetupAlert.Exit,
          cancelText = ScaleSetupStrings.ExitSetupAlert.Back,
          onConfirm = {
            navigateBack()
          },
        ),
      )
    }
  }

  private fun checkAndSaveScale() {
    dialogQueueService.showLoader(ScaleSetupStrings.SaveScaleLoader)
    viewModelScope.launch {
      val alreadyPairedScale = deviceService.pairedScales.first().find { it.sku == sku }
      if (alreadyPairedScale != null) {
        deviceService.deleteScale(alreadyPairedScale.id)
      }
      val scaleInfo = SCALES.find { it.sku == state.value.sku }
      val appSyncDevice = Device(
        device = GGDeviceDetail(
          deviceName = scaleInfo?.productName ?: "",
          macAddress = "",
          identifier = "",
        ),
        sku = state.value.sku,
        deviceType = ScaleSetupType.AppSync.value,
        nickname = scaleInfo?.productName!!,
      )
      deviceService.saveScale(appSyncDevice)
      dialogQueueService.dismissLoader()
      navigateBack()
    }
  }

  /**
   * Opens the Help modal.
   */
  private fun openHelpModal() {
    dialogQueueService.enqueue(
      DialogModel.Custom(
        contentKey = DialogType.HelpPopup,
        params =
          mapOf(
            "showGuide" to true,
            "onGuideClick" to {
              openProductGuide()
              dialogQueueService.dismissCurrent()
            },
          ),
      ),
    )
  }

  /**
   * Navigates back from the setup screen.
   */
  private fun navigateBack() {
    viewModelScope.launch {
      try {
        navigationService.navigateBack()
        AppLog.d(TAG, "Successfully navigated back from scale setup")
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to navigate back from scale setup", e.toString())
      }
    }
  }

  private fun openProductGuide() {
    val sku = state.value.sku
    val url = "${AppConfig.PRODUCT_URL}/$sku"
    openInAppBrowser(url)
  }

  /**
   * Requests a specific permission with rationale alert using the permission service.
   */
  private fun requestPermission(permissionType: String) {
    viewModelScope.launch {
      try {
        dialogUtility.permissionAlert(
          permissionType = permissionType,
          onRequest = {
            permissionService.requestPermission(permissionType)
          },
        )
      } catch (e: Exception) {
        AppLog.e(TAG, "Error requesting permission ${permissionType}", e.toString())
      }
    }
  }

  private fun observeSetup() {
    viewModelScope.launch {
      state.collect {
        when (it.currentStep) {
          AppsyncScaleSetupStep.PERMISSIONS -> {
            val areRequiredPermissionsEnabled = AppPermissionsHelper
              .areRequiredPermissionsEnabled(state.value.permissions, sku = sku)
            handleIntent(AppsyncScaleSetupIntent.SetNextButtonState(areRequiredPermissionsEnabled))
          }

          else -> handleIntent(AppsyncScaleSetupIntent.SetNextButtonState(true))
        }
      }
    }
  }
}
