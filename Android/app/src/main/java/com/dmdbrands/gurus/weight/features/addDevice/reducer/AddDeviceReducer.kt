package com.dmdbrands.gurus.weight.features.addDevice.reducer

import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.features.common.helper.DeviceDataHelper.toScaleInfo
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations
import com.dmdbrands.gurus.weight.features.common.model.DeviceModelInfo
import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * State for AddDeviceScreen.
 */
@Stable
data class AddScaleState(
  val form: FormGroup<AddScaleFormControls>,
  val isSubmitting: Boolean = false,
  val selectedSku: String? = null,
  val savedScales: ImmutableList<DeviceModelInfo> = persistentListOf(),
  val scaleId: String? = null,
) : IReducer.State

/**
 * Form controls for AddDeviceScreen.
 */
data class AddScaleFormControls(
  val modelNumber: FormControl<String>,
) {
  companion object {
    fun create() =
      AddScaleFormControls(
        modelNumber =
          FormControl.create(
            initialValue = "",
            validators =
              listOf(
                FormValidations.skuValidator(),
              ),
          ),
      )
  }
}

/**
 * Intents for AddDeviceScreen actions.
 */
sealed interface AddDeviceIntent : IReducer.Intent {
  object ShowHelp : AddDeviceIntent

  object OpenScaleChooser : AddDeviceIntent

  object Submit : AddDeviceIntent

  object ResetForm : AddDeviceIntent

  data class SetSavedScales(
    val scales: List<Device>,
  ) : AddDeviceIntent

  data class OpenSelectedScaleSetup(
    val sku: String,
  ) : AddDeviceIntent

  data class OpenDeviceSettings(
    val scaleId: String,
  ) : AddDeviceIntent
}

/**
 * Reducer for AddDeviceScreen.
 */
class AddDeviceReducer : IReducer<AddScaleState, AddDeviceIntent> {
  override fun reduce(
    state: AddScaleState,
    intent: AddDeviceIntent,
  ): AddScaleState? =
    when (intent) {
      AddDeviceIntent.ShowHelp -> state.copy()
      AddDeviceIntent.Submit -> state.copy(isSubmitting = true)
      AddDeviceIntent.OpenScaleChooser -> state.copy()
      is AddDeviceIntent.SetSavedScales -> state.copy(
        savedScales = intent.scales
          .map { it.toScaleInfo() }
          .sortedByDescending { scaleInfo ->
            DateTimeConverter.isoToTimestamp(scaleInfo.createdAt)
          }
          .toImmutableList(),
      )
      is AddDeviceIntent.OpenSelectedScaleSetup -> state.copy(selectedSku = intent.sku)
      is AddDeviceIntent.OpenDeviceSettings -> state.copy(scaleId = intent.scaleId)
      else -> state
    }
}
