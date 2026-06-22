package com.dmdbrands.gurus.weight.features.settings.manager

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.api.user.BodyCompUpdateRequest
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.services.BodyCompUpdateType
import com.dmdbrands.gurus.weight.domain.services.IBodyCompositionService
import com.dmdbrands.gurus.weight.features.common.components.RadioButtonOption
import com.dmdbrands.gurus.weight.features.common.components.RadioGroupSection
import com.dmdbrands.gurus.weight.features.common.components.showSectionedRadioGroupModal
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
  private val scaleSettingsManager: IScaleSettingsManager,
  private val userDataStore: UserDataStore,
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

    // Legacy accounts can still carry weightUnit = "lb_oz" (a baby scale used to
    // overwrite the adult unit). lbs & oz is no longer a My Weight option, so
    // fall back to LB to keep a radio selected rather than rendering blank.
    val adultSelection = if (adultUnit in MY_WEIGHT_OPTIONS) adultUnit.value else WeightUnit.LB.value

    val sections = buildList {
      add(
        RadioGroupSection(
          key = SECTION_MY_WEIGHT,
          label = RadioGroupModalStrings.UnitType.MyWeightSection,
          options = MY_WEIGHT_OPTIONS.map { it.toRadioOption(it.unit) },
          selectedItem = adultSelection,
        ),
      )
      if (state.isBabyProduct) {
        add(
          RadioGroupSection(
            key = SECTION_MY_KIDS,
            label = RadioGroupModalStrings.UnitType.MyKidsSection,
            options = MY_KIDS_OPTIONS.map { it.toRadioOption(it.babyUnit) },
            selectedItem = babyUnit.value,
          ),
        )
      }
    }

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

    val newAdultUnit = adultSelection?.let { WeightUnit.from(it) }
      ?.takeIf { it != currentAccount.weightUnit && it in MY_WEIGHT_OPTIONS }
    val newBabyUnit = babySelection?.let { WeightUnit.from(it) }
      ?.takeIf { it != state.babyWeightUnit && it in MY_KIDS_OPTIONS }

    if (newAdultUnit == null && newBabyUnit == null) {
      AppLog.d(TAG, "No unit type changes to persist")
      return
    }

    if (newAdultUnit != null) {
      dialogQueueService.showLoader(SettingsScreenStrings.UpdatingUnitType)
    }

    scope.launch {
      try {
        if (newAdultUnit != null) {
          updateAdultUnit(currentAccount, newAdultUnit)
        }
        if (newBabyUnit != null) {
          userDataStore.setBabyWeightUnit(currentAccount.id, newBabyUnit)
          AppLog.i(TAG, "Persisted baby weight unit: ${newBabyUnit.value}")
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error updating unit type", e)
      } finally {
        if (newAdultUnit != null) {
          dialogQueueService.dismissLoader()
        }
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

  // [label] is supplied per section: My Weight uses [WeightUnit.unit] (adult
  // height "/ ft"), My Kids uses [WeightUnit.babyUnit] (baby height "/ in").
  private fun WeightUnit.toRadioOption(label: String): RadioButtonOption<String> =
    RadioButtonOption(id = this.value, label = label)
}
