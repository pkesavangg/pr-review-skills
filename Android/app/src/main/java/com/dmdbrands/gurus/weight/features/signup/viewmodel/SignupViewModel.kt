package com.dmdbrands.gurus.weight.features.signup.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.domain.model.api.auth.SignupRequest
import com.dmdbrands.gurus.weight.domain.model.api.user.ProfileUpdateRequest
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile as DomainBabyProfile
import com.dmdbrands.gurus.weight.domain.model.common.MeasurementUnits
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.repository.IProductSelectionRepository
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IAnalyticsService
import com.dmdbrands.gurus.weight.domain.services.IBabyProfileService
import com.dmdbrands.gurus.weight.domain.services.IGoalService
import com.dmdbrands.gurus.weight.features.signup.model.BabyProfile as SignupBabyProfile
import com.dmdbrands.gurus.weight.features.signup.model.BabyWeightUnit
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.DateTimeValue
import com.dmdbrands.gurus.weight.features.common.components.DialogType
import com.dmdbrands.gurus.weight.features.common.components.BiologicalSexOptions
import com.dmdbrands.gurus.weight.features.common.components.showRadioGroupModal
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.common.strings.AppPopupStrings
import com.dmdbrands.gurus.weight.features.signup.model.SignupData
import com.dmdbrands.gurus.weight.features.signup.model.SignupFormControls
import com.dmdbrands.gurus.weight.features.signup.model.SignupIntent
import com.dmdbrands.gurus.weight.features.signup.model.SignupReducer
import com.dmdbrands.gurus.weight.features.signup.model.SignupState
import com.dmdbrands.gurus.weight.features.signup.model.SignupStep
import com.dmdbrands.gurus.weight.features.signup.strings.BabySignupStrings
import com.dmdbrands.gurus.weight.features.signup.strings.SignupErrorStrings
import com.dmdbrands.gurus.weight.features.signup.strings.SignupStrings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the multi-device signup flow.
 *
 * Flow ownership:
 *  - Common form input (NAME / EMAIL / BIRTHDAY / PICK_DEVICE / device path / PASSWORD)
 *    runs through the reducer's `Next` advancement.
 *  - On the **last data step** before a Ready terminal screen, the reducer
 *    short-circuits and this ViewModel runs the side-effecting work:
 *    `accountService.signup(...)` on the first pass, or device-specific
 *    side effects only on subsequent loop passes. Once that completes
 *    successfully, [SignupIntent.RegisterDevice] is dispatched so the
 *    reducer can record the device and advance to DEVICE_READY /
 *    ALL_DEVICES_READY.
 *  - [SignupIntent.FinishSignup] navigates to the Dashboard via
 *    `AppRoute.Init.Loading`. [SignupIntent.ConnectAnotherDevice] is
 *    handled entirely by the reducer (returns user to PICK_DEVICE).
 */
@HiltViewModel
class SignupViewModel
@Inject
constructor(
  private val accountService: IAccountService,
  private val goalService: IGoalService,
  private val analyticsService: IAnalyticsService,
  private val productSelectionRepository: IProductSelectionRepository,
  private val babyProfileService: IBabyProfileService,
  private val accountRepository: IAccountRepository,
) : BaseIntentViewModel<SignupState, SignupIntent>(
  reducer = SignupReducer(),
) {
  private val TAG = "SignupViewModel"

  override fun provideInitialState(): SignupState =
    SignupState(
      form = FormGroup(SignupFormControls.create()),
    )

  override fun handleIntent(intent: SignupIntent) {
    // Confirmation-gated intents: show an alert and defer the real intent until the user confirms.
    // These return early so the intent never reaches the reducer until confirmed.
    when (intent) {
      is SignupIntent.Skip -> if (maybeConfirmBabySkip()) return
      is SignupIntent.Back -> if (maybeConfirmBabyEditBack()) return
      is SignupIntent.DeleteBaby -> {
        confirmRemoveBaby(intent)
        return
      }
      else -> {}
    }
    when (intent) {
      is SignupIntent.OpenHelpModal -> openHelpModal()
      is SignupIntent.OpenURL -> openInAppBrowser(intent.url)
      is SignupIntent.Next -> onNext()
      is SignupIntent.Skip -> onSkip()
      is SignupIntent.OpenBabySexPicker -> openBabySexPicker()
      is SignupIntent.RetryDevice -> retryDevice()
      is SignupIntent.OnRequestBack -> onRequestBack()
      is SignupIntent.FinishSignup -> finishSignup()
      is SignupIntent.ConnectAnotherDevice -> AppLog.i(
        TAG,
        "Loop iteration — registered=${state.value.registeredDevices.map { it.id }}",
      )
      else -> {}
    }
    super.handleIntent(intent)
  }

  /** Routes an intent straight to the reducer, bypassing the confirmation gates above. */
  private fun dispatchToReducer(intent: SignupIntent) {
    super.handleIntent(intent)
  }

  /**
   * On the Add/Edit Baby form, Skip is confirmed first. Returns true when a dialog was shown
   * (so the caller must NOT forward the intent yet). Editing shows "Skip editing?", adding shows
   * "Skip Baby Profile?".
   */
  private fun maybeConfirmBabySkip(): Boolean {
    val current = state.value
    if (current.currentStep != SignupStep.ADD_BABY) return false
    val editing = current.babyState?.editingBabyId != null
    dialogQueueService.enqueue(
      DialogModel.Confirm(
        title = if (editing) BabySignupStrings.skipEditingTitle else BabySignupStrings.skipBabyTitle,
        message = if (editing) BabySignupStrings.skipEditingMessage else BabySignupStrings.skipBabyMessage,
        confirmText = BabySignupStrings.yesSkip,
        cancelText = BabySignupStrings.goBack,
        onConfirm = {
          // Editing skip is reducer-only; an adding skip also runs the loop product sync.
          if (!editing) onSkip()
          dispatchToReducer(SignupIntent.Skip)
          dialogQueueService.dismissCurrent()
        },
        onCancel = { dialogQueueService.dismissCurrent() },
      ),
    )
    return true
  }

  /** Back while editing a baby discards the edit — confirm with "Skip editing?" first. */
  private fun maybeConfirmBabyEditBack(): Boolean {
    val current = state.value
    if (current.currentStep != SignupStep.ADD_BABY || current.babyState?.editingBabyId == null) {
      return false
    }
    dialogQueueService.enqueue(
      DialogModel.Confirm(
        title = BabySignupStrings.skipEditingTitle,
        message = BabySignupStrings.skipEditingMessage,
        confirmText = BabySignupStrings.yesSkip,
        cancelText = BabySignupStrings.goBack,
        onConfirm = {
          dispatchToReducer(SignupIntent.Back)
          dialogQueueService.dismissCurrent()
        },
        onCancel = { dialogQueueService.dismissCurrent() },
      ),
    )
    return true
  }

  /** Swipe-delete on the baby list is confirmed with the destructive "Remove Baby?" alert. */
  private fun confirmRemoveBaby(intent: SignupIntent.DeleteBaby) {
    dialogQueueService.enqueue(
      DialogModel.Confirm(
        title = BabySignupStrings.removeBabyTitle,
        message = BabySignupStrings.removeBabyMessage,
        primaryActionType = ButtonType.ErrorText,
        confirmText = BabySignupStrings.deleteAction,
        cancelText = BabySignupStrings.cancelAction,
        onConfirm = {
          dispatchToReducer(intent)
          dialogQueueService.dismissCurrent()
        },
        onCancel = { dialogQueueService.dismissCurrent() },
      ),
    )
  }


  /**
   * Triggered on every Next dispatch. When the user is on the final data
   * step before a Ready terminal, hand off to [onSubmit] which runs the
   * signup / side-effect work; otherwise the reducer's Next branch (via
   * super.handleIntent) advances to the next form step.
   */
  fun onNext() {
    if (state.value.isFinalDataStep) {
      AppLog.d(TAG, "Final data step reached — running submit")
      onSubmit()
    } else {
      AppLog.d(TAG, "After Next intent - new currentStep: ${state.value.currentStep}")
    }
  }

  /**
   * Runs the signup work for the current device pass.
   *
   *  - **Account not yet created** (`!accountCreated`): validates all form
   *    fields, creates the account via [IAccountService.signup], then runs
   *    device-specific side effects.
   *  - **Account already created**: skips account creation, reuses the active
   *    session, and runs only device-specific side effects.
   *
   * Error routing (MOB-420):
   *  - **Account-creation failure** → toast on the password step; the user
   *    stays put and can correct/retry. The error screen is never shown.
   *  - **Product/device side-effect failure** → [SignupIntent.ShowDeviceError]
   *    navigates to the terminal ERROR screen.
   *
   * On success, dispatches [SignupIntent.RegisterDevice] so the reducer
   * records the device and advances to the terminal Ready step.
   */
  private fun onSubmit() {
    val stateValue = state.value
    val productType = ProductType.fromId(stateValue.form.controls.device.value) ?: run {
      handleIntent(SignupIntent.ShowDeviceError)
      return
    }
    val needsAccount = !stateValue.accountCreated

    // Inline field errors are surfaced by validate(); no toast/error screen here.
    if (needsAccount && !validateAllFields(stateValue, productType)) return

    // Only the account-creation pass says "Creating your account..."; the
    // loop pass (connect another device) reuses the existing account, so show
    // a generic loader instead.
    dialogQueueService.showLoader(
      message = if (needsAccount) SignupStrings.LoaderMessage else SignupStrings.LoadingMessage,
    )
    AppLog.d(TAG, "Submit pass — device=${productType.id} needsAccount=$needsAccount")

    viewModelScope.launch {
      try {
        val account: Account = if (needsAccount) {
          val result = runCatching { accountService.signup(buildSignupRequest(stateValue, productType)) }
          result.exceptionOrNull()?.let { e ->
            // signup() handles HTTP errors itself (specific toast + null return);
            // only unexpected throws (e.g. max accounts reached) reach here.
            AppLog.e(TAG, "Account creation failed", e)
            dialogQueueService.dismissLoader()
            dialogQueueService.showToast(Toast.Simple(message = SignupErrorStrings.accountFailedToast))
            return@launch
          }
          val created = result.getOrNull()
          if (created == null) {
            // AccountService.signup already surfaced the specific failure toast
            // (e.g. "Email address is already in use"); don't mask it with a generic
            // one here. (MOB-592)
            dialogQueueService.dismissLoader()
            return@launch
          }
          AppLog.i(TAG, "Account created successfully")
          handleIntent(SignupIntent.AccountCreated)
          analyticsService.logEvent(IAnalyticsService.Events.SIGNUP_COMPLETED)
          // Remember the first device the user picked so Entry/History and the
          // initial dashboard land on it after FinishSignup (skipping the snapshot).
          productSelectionRepository.saveSelectedProductType(productType)
          created
        } else {
          // Loop pass: read the just-created account from the DB. The in-memory
          // activeAccount StateFlow can still be null here (its collector hasn't emitted
          // yet right after signup), so getCurrentAccount() is the reliable source.
          accountService.getCurrentAccount() ?: run {
            dialogQueueService.dismissLoader()
            dialogQueueService.showToast(Toast.Simple(message = SignupErrorStrings.accountFailedToast))
            return@launch
          }
        }

        runDeviceProfile(productType, account, stateValue)
      } finally {
        dialogQueueService.dismissLoader()
      }
    }
  }

  /**
   * Retries the failed device's product creation from the error screen.
   * The account already exists, so account creation is skipped (MOB-420).
   */
  private fun retryDevice() {
    val stateValue = state.value
    val productType = ProductType.fromId(stateValue.form.controls.device.value) ?: run {
      handleIntent(SignupIntent.ShowDeviceError)
      return
    }
    // Account already exists on retry — generic loader, not "Creating your account...".
    dialogQueueService.showLoader(message = SignupStrings.LoadingMessage)
    AppLog.d(TAG, "Retrying device profile — device=${productType.id}")
    viewModelScope.launch {
      try {
        // Read from the DB rather than the in-memory activeAccount StateFlow, which may not
        // have emitted yet right after signup (see onSubmit loop pass).
        val account = accountService.getCurrentAccount() ?: run {
          dialogQueueService.dismissLoader()
          dialogQueueService.showToast(Toast.Simple(message = SignupErrorStrings.accountFailedToast))
          return@launch
        }
        runDeviceProfile(productType, account, stateValue)
      } finally {
        dialogQueueService.dismissLoader()
      }
    }
  }

  /**
   * Runs the current device's side effects and routes the outcome: success →
   * [SignupIntent.RegisterDevice] (advance to the Ready terminal); failure →
   * [SignupIntent.ShowDeviceError] (the ERROR screen).
   */
  private suspend fun runDeviceProfile(
    productType: ProductType,
    account: Account,
    stateValue: SignupState,
  ) {
    try {
      runDeviceSideEffects(productType, account, stateValue)
      handleIntent(SignupIntent.RegisterDevice)
    } catch (e: Exception) {
      AppLog.e(TAG, "Device profile creation failed", e)
      handleIntent(SignupIntent.ShowDeviceError)
    }
  }

  /**
   * Validates every form field required for first-pass account creation.
   * Loop iterations skip this — the per-step `isCurrentStepValid` guard
   * in [com.dmdbrands.gurus.weight.features.signup.components.SignupPager]
   * ensures only valid data ever reaches Next on subsequent passes.
   */
  private fun validateAllFields(stateValue: SignupState, productType: ProductType): Boolean {
    val controls = stateValue.form.controls
    val commonValid = listOf(
      controls.firstName.validate(),
      controls.lastName.validate(),
      controls.email.validate(),
      controls.password.validate(),
      controls.confirmPassword.validate(),
      controls.zipcode.validate(),
      controls.birthday.validate(),
    ).all { it }

    val deviceFieldsValid = when (productType) {
      ProductType.MY_WEIGHT -> if (stateValue.goalSkipped) {
        controls.sex.validate()
      } else {
        listOf(
          controls.sex.validate(),
          controls.goalType.validate(),
          controls.goalWeight.validate(),
          controls.currentWeight.validate(),
        ).all { it }
      }
      ProductType.BLOOD_PRESSURE -> controls.sex.validate()
      ProductType.BABY -> true
    }

    return commonValid && deviceFieldsValid
  }

  private fun buildSignupRequest(stateValue: SignupState, productType: ProductType): SignupRequest {
    val signupData: SignupData = stateValue.form.getValuesAsType()
    val controls = stateValue.form.controls
    val isMetric = signupData.useMetric
    val weightUnit = if (isMetric) WeightUnit.KG else WeightUnit.LB
    // Phase 2 (MOB-377): gender + dob are required only for weight/BP accounts, height
    // only for weight. The baby-scale path skips those steps, so the form holds defaults
    // (sex = ""). Sending an empty gender makes the server reject it as a missing required
    // value (400), so omit these fields (null) when the chosen product doesn't capture them.
    val needsGenderDob = productType == ProductType.MY_WEIGHT || productType == ProductType.BLOOD_PRESSURE
    val needsHeight = productType == ProductType.MY_WEIGHT
    return SignupRequest(
      email = signupData.email.trim(),
      firstName = signupData.firstName.trim(),
      lastName = signupData.lastName.trim(),
      gender = signupData.sex.takeIf { needsGenderDob && it.isNotBlank() },
      zipcode = signupData.zipcode.trim(),
      password = signupData.password,
      dob = if (needsGenderDob) DateTimeValue.getDateFormatFromMilliseconds(controls.birthday.value.getTimestamp()) else null,
      height = if (needsHeight) controls.height.value.toStoredHeight() else null,
      weightUnit = weightUnit.value,
      // Phase 2 (MOB-377): the account owns the first device picked; additional devices in the
      // multi-device loop are added via account update. measurementUnits derives from the unit choice.
      productTypes = listOf(productType.apiValue),
      measurementUnits = MeasurementUnits.fromWeightUnit(weightUnit).value,
    )
  }

  private suspend fun runDeviceSideEffects(
    productType: ProductType,
    account: Account,
    stateValue: SignupState,
  ) {
    val signupData: SignupData = stateValue.form.getValuesAsType()

    // For additional devices (account already existed before this pass), the original signup
    // request only carried the first product, so sync the newly added product here.
    if (stateValue.accountCreated) {
      // Order matters: the products endpoint (§2.19) rejects adding weight/blood_pressure with
      // "gender, dob must be set before adding the requested product(s)" if the account is missing
      // them (e.g. a previously baby-only account). The signup request only sent gender/dob/height
      // for the FIRST device, so PATCH the profile FIRST, then add the product. (MOB-598 #9)
      syncProfileForAdditionalDevice(productType, account, stateValue, signupData)
      syncDeviceToServer(productType, signupData)
    }

    when (productType) {
      ProductType.MY_WEIGHT -> if (!stateValue.goalSkipped) {
        AppLog.d(TAG, "Creating goal for Weight Scale account")
        // A null result means goal creation failed (the service swallows the cause);
        // throw so runDeviceProfile routes to the terminal ERROR screen.
        goalService.createGoalForSignup(
          account = account,
          goalType = signupData.goalType,
          startingWeight = signupData.currentWeight.toDoubleOrNull() ?: 0.0,
          goalWeight = signupData.goalWeight.toDoubleOrNull() ?: 0.0,
        ) ?: throw IllegalStateException("Goal creation failed during signup")
      }
      ProductType.BABY -> persistBabies(account, stateValue)
      ProductType.BLOOD_PRESSURE -> AppLog.d(TAG, "Blood Pressure pass — no additional setup")
    }
  }

  /**
   * Persists the baby profiles collected during signup to the server (spec §2.8 POST /v3/baby/,
   * which also auto-adds "baby" to the account's productTypes). Each save also mirrors the
   * created profile into the local cache via [IBabyProfileService.save].
   *
   * Best-effort per baby: the server assigns its own id on each POST, so a blanket retry would
   * create duplicates. A single baby failing is logged and skipped rather than aborting signup
   * or re-posting the ones that already succeeded — the user can re-add a missing baby from
   * My Kids.
   */
  private suspend fun persistBabies(account: Account, stateValue: SignupState) {
    val babies = stateValue.babyState?.babies.orEmpty()
    if (babies.isEmpty()) {
      AppLog.d(TAG, "Baby Scale pass — no baby profiles to persist")
      return
    }
    // Any save failure propagates so runDeviceProfile routes to the terminal ERROR
    // screen rather than silently advancing to the success screen.
    var lastSavedBabyId: String? = null
    babies.forEach { baby ->
      // save() returns the persisted profile with the server-assigned id.
      val saved = babyProfileService.save(baby.toDomain(account.id))
      lastSavedBabyId = saved.id
    }
    AppLog.d(TAG, "Persisted ${babies.size} baby profiles to server")

    // The last baby added during signup becomes the active baby (the one the dashboard
    // shows). The Loading screen reloads available products next, so this surfaces without
    // an app restart.
    lastSavedBabyId?.let { accountRepository.setActiveBabyId(account.id, it) }
  }

  /** Maps a signup-flow [SignupBabyProfile] to the domain [DomainBabyProfile] the API layer expects. */
  private fun SignupBabyProfile.toDomain(accountId: String): DomainBabyProfile =
    DomainBabyProfile(
      id = id,
      accountId = accountId,
      name = name,
      birthdate = birthday?.getTimestamp()?.let { DateTimeValue.getDateFormatFromMilliseconds(it) },
      // biologicalSex is a BabySex (male/female/private), matching the API value directly.
      sex = biologicalSex?.value,
      birthWeightDecigrams = birthWeightDecigrams(),
      birthLengthMillimeters = birthLengthMillimeters(),
    )

  /** Converts the entered birth weight to decigrams using the unit selected on the baby form. */
  private fun SignupBabyProfile.birthWeightDecigrams(): Int? {
    val weight = birthWeight.toDoubleOrNull() ?: return null
    return when (weightUnit) {
      BabyWeightUnit.KG -> ConversionTools.convertKgToDecigrams(weight)
      BabyWeightUnit.LBS -> ConversionTools.convertLbToDecigrams(weight)
      BabyWeightUnit.LBS_OZ ->
        ConversionTools.convertLbOzToDecigrams(weight.toInt(), birthWeightOz.toDoubleOrNull() ?: 0.0)
    }
  }

  /** Converts the entered birth length to millimeters (cm when the unit is metric, else inches). */
  private fun SignupBabyProfile.birthLengthMillimeters(): Int? {
    val length = birthLength.toDoubleOrNull() ?: return null
    return if (weightUnit == BabyWeightUnit.KG) ConversionTools.convertCmToMm(length)
    else ConversionTools.convertInchesToMm(length)
  }

  /**
   * Registers the just-added product (spec §2.19 PATCH /v3/account/products) and syncs the
   * measurement system (spec §2.1 PATCH /v3/account/measurement-units) so the server reflects
   * the multi-device account.
   *
   * Best-effort: a missing/failing endpoint must not block the signup loop. Product membership
   * is also auto-managed server-side when the device is paired or an entry is created, and the
   * next account sync reconciles. Mirrors ProductSelectionManager.persistProductForSetup.
   */
  /**
   * Syncs the gender/dob (and height for weight) entered for a device added on a loop pass.
   *
   * [buildSignupRequest] only sends these for the first device; a device added later collects
   * them again but they were never PATCHed, so the account kept its original (often null) values
   * — e.g. a baby-only account stayed gender-less after a weight scale was added. We merge the
   * freshly entered values onto the existing account and PATCH the profile (spec §2.5).
   *
   * Best-effort: a failure must not block the signup loop; the next account sync reconciles.
   */
  private suspend fun syncProfileForAdditionalDevice(
    productType: ProductType,
    account: Account,
    stateValue: SignupState,
    signupData: SignupData,
  ) {
    val needsGenderDob = productType == ProductType.MY_WEIGHT || productType == ProductType.BLOOD_PRESSURE
    if (!needsGenderDob) return

    val controls = stateValue.form.controls
    val gender = signupData.sex.takeIf { it.isNotBlank() } ?: account.gender
    val dob = controls.birthday.value.getTimestamp()
      .takeIf { it > 0 }
      ?.let { DateTimeValue.getDateFormatFromMilliseconds(it) }
      ?: account.dob
    // Height is only collected for weight scales; reuse the existing height otherwise.
    val height = if (productType == ProductType.MY_WEIGHT) {
      controls.height.value.toStoredHeight().toDouble()
    } else {
      account.height?.toDouble()
    }

    val request = ProfileUpdateRequest(
      id = account.id,
      email = account.email,
      firstName = account.firstName,
      lastName = account.lastName,
      gender = gender,
      zipcode = account.zipcode,
      dob = dob,
      height = height,
    )
    AppLog.d(TAG, "Syncing profile (gender/dob/height) for additional device=${productType.apiValue}")
    runCatching { accountService.updateProfile(request, isFromProfile = false, showToast = false) }
      .onFailure { AppLog.e(TAG, "Profile sync for additional device failed (continuing signup)", it) }
  }

  private suspend fun syncDeviceToServer(productType: ProductType, signupData: SignupData) {
    AppLog.d(TAG, "Syncing product=${productType.apiValue} to server")
    // Product registration is required for the device to actually exist on the account. If it
    // fails (e.g. §2.19 rejects the request) we must NOT complete signup silently with the product
    // missing — let the failure propagate so runDeviceProfile routes to the device-error screen
    // (with retry). (MOB-598 #9)
    accountService.addProduct(productType)
    // Measurement units are best-effort: a failure here must not block the loop, and the next
    // account sync reconciles.
    runCatching {
      val weightUnit = if (signupData.useMetric) WeightUnit.KG else WeightUnit.LB
      accountService.updateMeasurementUnits(MeasurementUnits.fromWeightUnit(weightUnit))
    }.onFailure { AppLog.e(TAG, "Measurement-units sync failed (continuing signup)", it) }
  }

  /**
   * Skip on a loop pass (GOAL or baby steps) is handled entirely by the reducer, which registers
   * the device locally and jumps to the terminal — that path never runs the device side effects.
   * Mirror the loop-pass server sync here so the skipped device's product still reaches the server,
   * matching the non-skip submit path. First-pass skip needs nothing: the product is carried in the
   * signup request and registered at account submit.
   */
  private fun onSkip() {
    val stateValue = state.value
    if (!stateValue.accountCreated) return
    val productType = ProductType.fromId(stateValue.form.controls.device.value) ?: return
    val signupData: SignupData = stateValue.form.getValuesAsType()
    AppLog.d(TAG, "Skip on loop pass — syncing product=${productType.apiValue}")
    viewModelScope.launch {
      val account = accountService.getCurrentAccount()
      if (account == null) {
        AppLog.e(TAG, "Skip-pass sync: no current account")
        handleIntent(SignupIntent.ShowDeviceError)
        return@launch
      }
      // Any sync failure routes to the terminal ERROR screen, matching the submit path.
      try {
        syncProfileForAdditionalDevice(productType, account, stateValue, signupData)
        syncDeviceToServer(productType, signupData)
      } catch (e: Exception) {
        AppLog.e(TAG, "Skip-pass device sync failed", e)
        handleIntent(SignupIntent.ShowDeviceError)
      }
    }
  }

  private fun finishSignup() {
    AppLog.i(TAG, "Finish tapped — replacing stack with Loading → Dashboard")
    viewModelScope.launch {
      try {
        // When more than one device was added during signup, open the dashboard in snapshot
        // (multi-product) mode instead of pinning the first device's single-product view (MOB #14).
        // Clearing the saved selection makes loadAvailableProducts derive snapshot mode = true.
        if (state.value.registeredDevices.size > 1) {
          AppLog.d(TAG, "Multiple devices added — clearing selection to open snapshot view")
          productSelectionRepository.clearSelectedProduct()
        }
        navigationService.replaceStack(AppRoute.Init.Loading)
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to navigate to Dashboard from FinishSignup", e)
      }
    }
  }

  /**
   * Opens the baby Biological Sex picker using the same queued radio modal as Settings
   * ([showRadioGroupModal]), so the dropdown matches the settings option pickers. The chosen
   * value is written straight back to the baby form control.
   */
  private fun openBabySexPicker() {
    val babyForm = state.value.babyState?.babyForm ?: return
    showRadioGroupModal(
      dialogService = dialogQueueService,
      title = BiologicalSexOptions.Title,
      options = BiologicalSexOptions.options(),
      selectedItem = babyForm.biologicalSex.value.ifEmpty { null },
      onConfirm = { selected -> selected?.let { babyForm.biologicalSex.onValueChange(it) } },
      onCancel = {},
    )
  }

  private fun openHelpModal() {
    dialogQueueService.enqueue(
      DialogModel.Custom(
        contentKey = DialogType.HelpPopup,
      ),
    )
  }

  private fun onRequestBack() {
    dialogQueueService.enqueue(
      DialogModel.Confirm(
        title = AppPopupStrings.UnsavedChanges.Title,
        message = AppPopupStrings.UnsavedChanges.Message,
        confirmText = AppPopupStrings.UnsavedChanges.Exit,
        cancelText = AppPopupStrings.UnsavedChanges.Return,
        onConfirm = {
          navigateBack()
          dialogQueueService.dismissCurrent()
        },
        onCancel = {
          dialogQueueService.dismissCurrent()
        },
      ),
    )
  }

  private fun navigateBack() {
    viewModelScope.launch {
      try {
        navigationService.navigateBack(topLevel = null)
        AppLog.d(TAG, "Successfully navigated back from signup")
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to navigate back from signup", e)
      }
    }
  }
}
