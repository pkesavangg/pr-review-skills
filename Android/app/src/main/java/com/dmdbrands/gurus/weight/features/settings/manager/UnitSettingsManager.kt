package com.dmdbrands.gurus.weight.features.settings.manager

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.api.user.BodyCompUpdateRequest
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.services.BodyCompUpdateType
import com.dmdbrands.gurus.weight.domain.services.IBodyCompositionService
import com.dmdbrands.gurus.weight.features.common.components.RadioButtonOption
import com.dmdbrands.gurus.weight.features.common.components.showRadioGroupModal
import com.dmdbrands.gurus.weight.features.settings.strings.RadioGroupModalStrings
import com.dmdbrands.gurus.weight.features.settings.viewmodel.SettingsState
import com.dmdbrands.library.ggbluetooth.enums.GGUserActionResponseType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

interface IUnitSettingsManager {
  fun onUnitTypeClick(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
  )
}

class UnitSettingsManager
@Inject
constructor(
  private val bodyCompositionService: IBodyCompositionService,
  private val dialogQueueService: IDialogQueueService,
  private val scaleSettingsManager: IScaleSettingsManager,
) : IUnitSettingsManager {
  companion object {
    private const val TAG = "UnitSettingsManager"
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
    showRadioGroupModal(
      dialogService = dialogQueueService,
      title = RadioGroupModalStrings.Titles.UnitType,
      options =
        listOf(
          RadioButtonOption(WeightUnit.LB.value, RadioGroupModalStrings.UnitType.Imperial),
          RadioButtonOption(WeightUnit.KG.value, RadioGroupModalStrings.UnitType.Metric),
        ),
      selectedItem = stateProvider().account?.weightUnit?.value,
      confirmText = RadioGroupModalStrings.Button.Save,
      onConfirm = { selectedUnitType ->
        selectedUnitType?.let { unitType ->
          onUnitTypeUpdate(scope, stateProvider, unitType.toString())
        }
      },
      onCancel = {
        AppLog.d(TAG, "Unit type selection cancelled")
      },
    )
  }

  private fun onUnitTypeUpdate(
    scope: CoroutineScope,
    stateProvider: () -> SettingsState,
    unitTypeValue: String,
  ) {
    val currentAccount = stateProvider().account
    if (currentAccount == null) {
      AppLog.e(TAG, "No active account found for unit type update")
      return
    }

    val newWeightUnit =
      when (unitTypeValue) {
        WeightUnit.KG.value -> WeightUnit.KG
        WeightUnit.LB.value -> WeightUnit.LB
        else -> {
          return
        }
      }

    if (currentAccount.weightUnit == newWeightUnit) {
      return
    }

    dialogQueueService.showLoader("Updating unit type...")

    scope.launch {
      try {
        val bodyComposition =
          BodyCompUpdateRequest(
            height = currentAccount.height ?: 1700,
            activityLevel = currentAccount.activityLevel ?: "normal",
            weightUnit = newWeightUnit.value,
          )

        val updatedProfile = currentAccount.toGGBTUserProfile().copy(unit = newWeightUnit.value)
        val scaleResult = scaleSettingsManager.updateR4Profile(updatedProfile)
        AppLog.d(TAG, "Scale result: $scaleResult")
        scaleSettingsManager.handleScaleUpdateResult(scaleResult)

        bodyCompositionService.updateBodyComposition(BodyCompUpdateType.WEIGHT_UNIT, bodyComposition)
        AppLog.i(TAG, "Successfully updated unit type")
      } catch (e: Exception) {
        AppLog.e(TAG, "Error updating unit type", e)
      } finally {
        dialogQueueService.dismissLoader()
      }
    }
  }

}
