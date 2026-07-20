package com.dmdbrands.gurus.weight.features.settings.manager

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.api.user.BodyCompUpdateRequest
import com.dmdbrands.gurus.weight.domain.model.common.MeasurementUnits
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.services.BodyCompUpdateType
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IBodyCompositionService
import com.dmdbrands.gurus.weight.features.common.components.RadioButtonOption
import com.dmdbrands.gurus.weight.features.common.components.RadioGroupSection
import com.dmdbrands.gurus.weight.features.common.components.showSectionedRadioGroupModal
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.settings.strings.RadioGroupModalStrings
import com.dmdbrands.gurus.weight.features.settings.strings.SettingsScreenStrings
import com.dmdbrands.gurus.weight.features.settings.viewmodel.SettingsIntent
import com.dmdbrands.gurus.weight.features.settings.viewmodel.SettingsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

interface IUnitSettingsManager {
  fun onUnitTypeClick(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
  )

  /**
   * Streams the persisted My Kids unit into the settings state via
   * [SettingsIntent.SetBabyWeightUnit].
   */
  fun observeBabyWeightUnit(
    scope: CoroutineScope,
    dispatchIntent: (SettingsIntent) -> Unit,
  )
}

class UnitSettingsManager
@Inject
constructor(
  private val bodyCompositionService: IBodyCompositionService,
  private val dialogQueueService: IDialogQueueService,
  private val scaleSettingsManager: IDeviceSettingsManager,
  private val userDataStore: UserDataStore,
  private val accountService: IAccountService,
) : IUnitSettingsManager {
  companion object {
    private const val TAG = "UnitSettingsManager"
    internal const val SECTION_MY_WEIGHT = "myWeight"
    internal const val SECTION_MY_KIDS = "myKids"

    // My Weight allows the adult units only; lbs & oz is exclusive to baby.
    internal val MY_WEIGHT_OPTIONS: List<WeightUnit> = listOf(WeightUnit.LB, WeightUnit.KG)

    // My Kids order matches the design: lbs & oz default first, then lbs, kg.
    internal val MY_KIDS_OPTIONS: List<WeightUnit> = listOf(WeightUnit.LB_OZ, WeightUnit.LB, WeightUnit.KG)
  }

  override fun observeBabyWeightUnit(
    scope: CoroutineScope,
    dispatchIntent: (SettingsIntent) -> Unit,
  ) {
    scope.launch {
      userDataStore.babyWeightUnitForCurrentAccountFlow
        .distinctUntilChanged()
        .collect { unit -> dispatchIntent(SettingsIntent.SetBabyWeightUnit(unit)) }
    }
  }

  override fun onUnitTypeClick(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
  ) {
    AppLog.d(TAG, "Unit type clicked")
    showUnitTypeModal(scope, stateProvider)
  }

  private fun showUnitTypeModal(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
  ) {
    val state = stateProvider()
    val adultUnit = state.account?.weightUnit ?: WeightUnit.LB
    val babyUnit = state.babyWeightUnit

    // Per MOB-1175 both sections always render; each is editable only when the account
    // owns the relevant product, otherwise it's locked to the system default with an
    // unlock message. My Weight ⟺ weight scale, My Kids ⟺ baby scale or baby profile.
    val myWeightEnabled = state.isMyWeightEnabled
    // Unit editability is gated on baby product ownership, NOT the always-on My Kids row.
    val myKidsEnabled = state.isMyKidsUnitEnabled

    // Legacy accounts can still carry weightUnit = "lb_oz" (a baby scale used to
    // overwrite the adult unit). lbs & oz is no longer a My Weight option, so
    // fall back to LB to keep a radio selected rather than rendering blank. A locked
    // section is likewise pinned to the default (DISPLAY_DEFAULT = LB, baby = LB_OZ).
    val adultSelection = when {
      !myWeightEnabled -> WeightUnit.DISPLAY_DEFAULT.value
      adultUnit in MY_WEIGHT_OPTIONS -> adultUnit.value
      else -> WeightUnit.LB.value
    }
    val babySelection = if (myKidsEnabled) babyUnit.value else WeightUnit.LB_OZ.value

    val sections = listOf(
      RadioGroupSection(
        key = SECTION_MY_WEIGHT,
        label = RadioGroupModalStrings.UnitType.MyWeightSection,
        options = MY_WEIGHT_OPTIONS.map { it.toRadioOption(it.unit) },
        selectedItem = adultSelection,
        enabled = myWeightEnabled,
        lockedMessage = RadioGroupModalStrings.UnitType.MyWeightLockedMessage.takeUnless { myWeightEnabled },
      ),
      RadioGroupSection(
        key = SECTION_MY_KIDS,
        label = RadioGroupModalStrings.UnitType.MyKidsSection,
        options = MY_KIDS_OPTIONS.map { it.toRadioOption(it.babyUnit) },
        selectedItem = babySelection,
        enabled = myKidsEnabled,
        lockedMessage = RadioGroupModalStrings.UnitType.MyKidsLockedMessage.takeUnless { myKidsEnabled },
      ),
    )

    showSectionedRadioGroupModal(
      dialogService = dialogQueueService,
      title = RadioGroupModalStrings.Titles.UnitType,
      sections = sections,
      confirmText = RadioGroupModalStrings.Button.Save,
      onConfirm = { selections ->
        handleSave(
          scope = scope,
          stateProvider = stateProvider,
          adultSelection = selections[SECTION_MY_WEIGHT],
          babySelection = selections[SECTION_MY_KIDS],
        )
      },
      onCancel = { AppLog.d(TAG, "Unit type selection cancelled") },
    )
  }

  private fun handleSave(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
    adultSelection: String?,
    babySelection: String?,
  ) {
    val state = stateProvider()
    val currentAccount = state.account
    if (currentAccount == null) {
      AppLog.e(TAG, "No active account found for unit type update")
      return
    }

    // Locked sections are pinned to the default and must never persist — saving one
    // would overwrite the stored unit with the default. Only enabled sections commit. (MOB-1175)
    val newAdultUnit = adultSelection?.let { WeightUnit.from(it) }
      ?.takeIf { state.isMyWeightEnabled && it != currentAccount.weightUnit && it in MY_WEIGHT_OPTIONS }
    val newBabyUnit = babySelection?.let { WeightUnit.from(it) }
      ?.takeIf { state.isMyKidsUnitEnabled && it != state.babyWeightUnit && it in MY_KIDS_OPTIONS }

    if (newAdultUnit == null && newBabyUnit == null) {
      AppLog.d(TAG, "No unit type changes to persist")
      return
    }

    // Both adult and baby changes now hit the network (bodycomp + measurement-units
    // APIs respectively), so show the loader whenever either is persisting.
    dialogQueueService.showLoader(SettingsScreenStrings.UpdatingUnitType)

    scope.launch {
      try {
        if (newAdultUnit != null) {
          updateAdultUnit(currentAccount, newAdultUnit)
        }
        if (newBabyUnit != null) {
          updateBabyUnit(currentAccount.id, newBabyUnit)
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error updating unit type", e)
        dialogQueueService.showToast(
          Toast.Simple(
            title = null,
            message = SettingsScreenStrings.Error.MessageGeneric,
            action = null,
          ),
        )
      } finally {
        dialogQueueService.dismissLoader()
      }
    }
  }

  private suspend fun updateAdultUnit(
    currentAccount: com.dmdbrands.gurus.weight.domain.model.storage.Account.Account,
    newWeightUnit: WeightUnit,
  ) {
    val bodyComposition = BodyCompUpdateRequest(
      height = currentAccount.height ?: BodyCompUpdateRequest.DEFAULT_HEIGHT,
      activityLevel = currentAccount.activityLevel ?: BodyCompUpdateRequest.DEFAULT_ACTIVITY_LEVEL,
      weightUnit = newWeightUnit.value,
    )
    val updatedProfile = currentAccount.toGGBTUserProfile().copy(unit = newWeightUnit.value)
    val scaleResult = scaleSettingsManager.updateR4Profile(updatedProfile)
    AppLog.d(TAG, "Scale result: $scaleResult")
    scaleSettingsManager.handleScaleUpdateResult(scaleResult)
    bodyCompositionService.updateBodyComposition(BodyCompUpdateType.WEIGHT_UNIT, bodyComposition)
    AppLog.i(TAG, "Successfully updated adult unit type: ${newWeightUnit.value}")
  }

  /**
   * Persists a My Kids (baby) unit change. The unit is kept device-local for
   * display ([UserDataStore.setBabyWeightUnit]) AND synced to the account-level
   * measurement system on the server via `PATCH /v3/account/measurement-units`
   * (Me App 2.0 API spec §2.1, MOB-377). `measurementUnits` is the baby unit
   * format — `imperialLbOz` / `imperialLbDecimal` / `metric` — so without this
   * call the choice would never leave the device (lost on reinstall / account
   * switch / other devices). Mirrors how signup sets it.
   */
  private suspend fun updateBabyUnit(
    accountId: String,
    newBabyUnit: WeightUnit,
  ) {
    // Sync to the server FIRST, then persist the device-local display unit only once the
    // PATCH succeeds. updateMeasurementUnits throws when offline (requireNetworkAvailable)
    // and on HTTP failure, so guarding the local write behind it keeps the two in lockstep:
    // writing locally first left the device showing a unit the server never received, with
    // no rollback in the failure path — a local/server divergence (PR #2109 review).
    accountService.updateMeasurementUnits(MeasurementUnits.fromWeightUnit(newBabyUnit))
    userDataStore.setBabyWeightUnit(accountId, newBabyUnit)
    AppLog.i(TAG, "Persisted baby weight unit: ${newBabyUnit.value} (measurement-units API + local)")
  }

  // [label] is supplied per section: My Weight uses [WeightUnit.unit] (adult
  // height "& feet"), My Kids uses [WeightUnit.babyUnit] (baby length "& in").
  private fun WeightUnit.toRadioOption(label: String): RadioButtonOption<String> =
    RadioButtonOption(id = this.value, label = label)
}
