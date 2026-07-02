package com.dmdbrands.gurus.weight.features.DeviceSetup.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.enums.BabySex
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile as DomainBabyProfile
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.services.IBabyProfileService
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.BabyScaleSetupStep
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.DeviceSetupStep
import com.dmdbrands.gurus.weight.features.DeviceSetup.modal.BabyProfile as SetupBabyProfile
import com.dmdbrands.gurus.weight.features.DeviceSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.DeviceSetup.modal.SetupInitData
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.BabyScaleSetupReducer
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.BabyScaleSetupState
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.DeviceSetupIntent
import com.dmdbrands.gurus.weight.features.DeviceSetup.strings.BabyScaleSetupStrings
import com.dmdbrands.gurus.weight.features.appPermissions.helper.AppPermissionsHelper
import com.dmdbrands.gurus.weight.features.common.enums.DeviceSetupType
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.greatergoods.ggbluetoothsdk.external.enums.GGDeviceProtocolType
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant

@HiltViewModel(
  assistedFactory = BabyScaleBLESetupViewModel.Factory::class,
)
class BabyScaleBLESetupViewModel
@AssistedInject
constructor(
  @Assisted val setupInit: SetupInitData<BabyScaleSetupStep>,
  dependencies: BLESetupDependencies,
  private val babyProfileService: IBabyProfileService,
  private val accountRepository: IAccountRepository,
) : BLESetupViewmodel<BabyScaleSetupStep, BabyScaleSetupState>(
  GGDeviceProtocolType.GG_DEVICE_PROTOCOL_A6.value,
  setupInit,
  reducer = BabyScaleSetupReducer(),
  dependencies,
) {
  @AssistedFactory
  interface Factory {
    fun create(
      @Assisted setupInit: SetupInitData<BabyScaleSetupStep>,
    ): BabyScaleBLESetupViewModel
  }

  private val sku = setupInit.sku

  // FINISH on the baby list uploads the added profiles; SKIP finishes without any upload, so
  // skipping never creates a baby on the server even if one was added then backed out of. (MOB-596)
  private var shouldUploadBabyProfiles = true

  override fun provideInitialState(): BabyScaleSetupState {
    return BabyScaleSetupState()
  }

  // Pairing the scale always adds "baby" to productTypes so "My Kids" enables, even when the
  // baby-profile step is skipped. (MOB-686)
  override fun productToRegisterAfterSetup(): ProductType = ProductType.BABY

  // The most recently added baby in this session, captured on persist. Drives the post-setup
  // product switch so the dashboard opens on the newly-added baby. Null when the baby-profile
  // step was skipped (no baby created), which leaves the active product unchanged.
  private var lastPersistedBaby: DomainBabyProfile? = null

  // Switch the dashboard to the just-added baby after setup (MOB-422 auto-switch): selects that
  // baby's product view and turns snapshot mode off. Null (skip flow) leaves the product unchanged.
  override fun productSelectionAfterSetup(): ProductSelection? =
    lastPersistedBaby?.let { ProductSelection.Baby(it) }

  init {
    lazyInit()
  }

  private suspend fun saveScale(): Boolean {
    return try {
      val scale = discoveredScale
      if (scale != null) {
        val nickname = state.value.nickname
        val currentTime = Instant.now().toString()
        val updatedScale = scale.copy(
          nickname = nickname,
          deviceType = DeviceSetupType.BabyScale.value,
          sku = sku,
          createdAt = currentTime,
          device = scale.device?.copy(
            deviceName = scale.device.deviceName.ifEmpty { nickname },
          ),
        )
        discoveredScale = updatedScale
        deviceService.saveScale(updatedScale)
        AppLog.d(TAG, "Baby Scale saved: nickname=$nickname, sku=$sku")
        true
      } else {
        AppLog.w(TAG, "No discovered Baby Scale to save")
        false
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      AppLog.e(TAG, "Error saving Baby Scale", e)
      false
    }
  }

  override fun onNext() {
    val currentState = state.value

    if (currentState.isLastStep) {
      this.handleIntent(DeviceSetupIntent.ExitSetup(true))
    } else if (currentSetupState.step == BabyScaleSetupStep.SCALE_INFO) {
      if (isPermissionGranted) {
        handleIntent(DeviceSetupIntent.SetNewStep(BabyScaleSetupStep.WAKEUP))
      } else {
        handleIntent(DeviceSetupIntent.SetNewStep(BabyScaleSetupStep.PERMISSIONS))
        permissionAccess()
      }
    } else {
      currentState.nextStep?.let { handleIntent(DeviceSetupIntent.SetNewStep(it)) }
    }
  }

  override suspend fun onSetupFinished() {
    AppLog.d(TAG, "Setup finished — saving scale with final nickname: ${state.value.nickname}")
    // Show a loader while the scale (and any baby profiles) persist so the screen isn't
    // interactive before the flow navigates back.
    dialogQueueService.showLoader(BabyScaleSetupStrings.savingLoader)
    try {
      val saved = saveScale()
      if (!saved) {
        AppLog.w(TAG, "Scale save failed during setup finish")
      }
      // After the scale is saved, upload the baby profiles added on BABY_LIST to the server
      // (POST /v3/baby/, which also auto-adds "baby" to productTypes). Skipped when the user
      // chose to skip the baby-profile step — see [showSkipBabyProfileDialog]. (MOB-596)
      if (shouldUploadBabyProfiles) {
        persistBabyProfiles()
      }
    } finally {
      dialogQueueService.dismissLoader()
    }
  }

  /**
   * Uploads the baby profiles collected on the BABY_LIST step to the server. Best-effort per baby
   * (mirrors signup's persistBabies): the server assigns its own id on each POST, so a blanket
   * retry would create duplicates; a single failing baby is logged and skipped. createBaby also
   * auto-adds the "baby" product server-side. (MOB-596)
   */
  private suspend fun persistBabyProfiles() {
    val profiles = state.value.babyProfiles
    if (profiles.isEmpty()) {
      AppLog.d(TAG, "No baby profiles to persist")
      return
    }
    val accountId = accountRepository.getActiveAccount().first()?.id
    if (accountId == null) {
      AppLog.w(TAG, "No active account — skipping baby profile upload")
      return
    }
    var savedCount = 0
    var lastSaved: DomainBabyProfile? = null
    profiles.forEach { profile ->
      try {
        // save() returns the persisted profile with the server-assigned id.
        val saved = babyProfileService.save(profile.toDomain(accountId))
        lastSaved = saved
        savedCount++
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to persist baby profile during baby-scale setup: ${profile.id}", e)
      }
    }
    AppLog.d(TAG, "Persisted $savedCount/${profiles.size} baby profiles to server")

    // The last baby added in this setup session becomes the active baby (the one the dashboard
    // shows) and the active product view — the latter is applied post-setup via
    // [productSelectionAfterSetup]. Refresh available products so the new baby surfaces without an
    // app restart (productTypes may already include "baby", in which case the reactive account
    // observer won't re-emit on its own).
    lastSaved?.let { saved ->
      lastPersistedBaby = saved
      accountRepository.setActiveBabyId(accountId, saved.id)
      productSelectionManager.loadAvailableProducts(accountId)
    }
  }

  /**
   * Maps the in-memory setup [SetupBabyProfile] to the domain [DomainBabyProfile] for upload.
   * The setup form has no unit toggle and its inputs are metric (cm / kg) per the design, so birth
   * length/weight convert cm→mm and kg→decigrams. An unset or "Other" sex resolves to "private"
   * via [BabySex.fromValue]. (MOB-596)
   */
  private fun SetupBabyProfile.toDomain(accountId: String): DomainBabyProfile =
    DomainBabyProfile(
      id = id,
      accountId = accountId,
      name = name,
      birthdate = birthday,
      sex = BabySex.fromValue(biologicalSex?.lowercase()).value,
      birthWeightDecigrams = birthWeight?.toDoubleOrNull()?.let(ConversionTools::convertKgToDecigrams),
      birthLengthMillimeters = birthLength?.toDoubleOrNull()?.let(ConversionTools::convertCmToMm),
    )

  override fun onBack() {
    val currentState = state.value
    val currentStep = currentState.step

    if (currentState.isFirstStep) {
      navigateTo(AppRoute.AccountSettings.MyDevices)
      return
    }

    // Skip WAKEUP when going back from SCALE_NAME — go to PERMISSIONS
    if (currentStep == BabyScaleSetupStep.SCALE_NAME) {
      handleIntent(DeviceSetupIntent.SetNewStep(BabyScaleSetupStep.PERMISSIONS))
      return
    }

    val previousStep = currentState.previousStep
    if (previousStep != null) {
      handleIntent(DeviceSetupIntent.SetNewStep(previousStep))
    } else {
      navigateTo(AppRoute.AccountSettings.MyDevices)
    }
  }

  override fun onSkip() {
    // On the baby-profile form, skipping confirms via the "Skip Baby Profile?" dialog and
    // finishes setup (MOB-440). Other steps fall through to the normal next-step advance.
    when (state.value.step) {
      BabyScaleSetupStep.BABY_PROFILE_FORM -> showSkipBabyProfileDialog()
      else -> onNext()
    }
  }

  private fun showSkipBabyProfileDialog() {
    dialogQueueService.enqueue(
      DialogModel.Confirm(
        title = BabyScaleSetupStrings.SkipDialog.Title,
        message = BabyScaleSetupStrings.SkipDialog.Message,
        confirmText = BabyScaleSetupStrings.SkipDialog.FinishSetup,
        cancelText = BabyScaleSetupStrings.SkipDialog.Cancel,
        onConfirm = {
          // Skipping means no baby profile is created — finish without uploading. (MOB-596)
          shouldUploadBabyProfiles = false
          handleIntent(DeviceSetupIntent.ExitSetup(true))
        },
        onCancel = {},
      ),
    )
  }

  override fun onTryAgain() {
    when (state.value.step) {
      BabyScaleSetupStep.WAKEUP -> wakeUpScale()
      else -> {}
    }
  }

  override fun onStepChange(step: DeviceSetupStep) {
    AppLog.d(TAG, "Step: $step")
    viewModelScope.launch {
      when (step) {
        BabyScaleSetupStep.WAKEUP -> wakeUpScale()
        else -> AppLog.d(TAG, "No specific action for step: $step")
      }
    }
  }

  private fun wakeUpScale() {
    handleIntent(DeviceSetupIntent.AlterConnectionState(ConnectionState.Loading))
    clearBluetoothTimeout()
    stopObservingDevices()

    bluetoothTimeoutJob = viewModelScope.launch {
      delay(bluetoothTimeout)
      if (discoveredScale == null) {
        handleIntent(DeviceSetupIntent.AlterConnectionState(ConnectionState.Failed.Error))
      }
    }

    try {
      AppLog.d(TAG, "BLE scan started for baby scale (sku=$sku)")
      ggDeviceService.scanForPairing()
      startObservingDevices { data ->
        viewModelScope.launch {
          try {
            AppLog.d(TAG, "Baby scale device found: ${data.deviceName}")
            // Block re-pairing a scale that's already paired to this account (Figma 33013-205573):
            // show the "Scale Already Connected" alert and exit instead of adding a duplicate.
            if (deviceService.scaleExistsByMac(data.macAddress)) {
              AppLog.w(TAG, "Baby scale already paired for this account: ${data.macAddress}")
              clearBluetoothTimeout()
              stopObservingDevices()
              dialogQueueService.showDialog(
                DialogModel.Alert(
                  title = BabyScaleSetupStrings.AlreadyPaired.Title,
                  message = BabyScaleSetupStrings.AlreadyPaired.Message,
                  dismissText = BabyScaleSetupStrings.AlreadyPaired.Exit,
                  onDismiss = {
                    handleIntent(DeviceSetupIntent.ExitSetup(true))
                    dialogQueueService.dismissCurrent()
                  },
                ),
              )
              return@launch
            }
            discoveredScale = Device(
              device = data,
              deviceType = DeviceSetupType.BabyScale.value,
              sku = sku,
            )
            clearBluetoothTimeout()
            handleIntent(DeviceSetupIntent.AlterConnectionState(ConnectionState.Success))
            delay(2000)
            onNext()
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            AppLog.e(TAG, "Error processing discovered device", e)
            clearBluetoothTimeout()
            handleIntent(DeviceSetupIntent.AlterConnectionState(ConnectionState.Failed.Error))
          }
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      AppLog.e(TAG, "Error during BLE scan", e)
      clearBluetoothTimeout()
      handleIntent(DeviceSetupIntent.AlterConnectionState(ConnectionState.Failed.Error))
    }
  }

  override fun observePermissions() {
    viewModelScope.launch {
      try {
        subscribePermissions().collect { newPermissions: GGPermissionStatusMap ->
          val areRequiredPermissionsEnabled =
            AppPermissionsHelper.areRequiredPermissionsEnabled(newPermissions, setupType = DeviceSetupType.BabyScale)
          handleIntent(DeviceSetupIntent.SetPermissions(newPermissions))
          if (isPermissionGranted != areRequiredPermissionsEnabled) {
            isPermissionGranted = areRequiredPermissionsEnabled
          }
          if (!areRequiredPermissionsEnabled) {
            if (currentSetupState.step != BabyScaleSetupStep.PERMISSIONS && currentSetupState.step != BabyScaleSetupStep.SCALE_INFO) {
              handleIntent(DeviceSetupIntent.SetNewStep(BabyScaleSetupStep.PERMISSIONS))
            }
          }
          handleIntent(DeviceSetupIntent.NextEnabled(areRequiredPermissionsEnabled))
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        AppLog.e(TAG, "Error observing permissions", e)
      }
    }
  }

  companion object {
    private const val TAG = "BabyScaleBLESetupVM"
  }
}
