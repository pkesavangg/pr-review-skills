package com.dmdbrands.gurus.weight.features.addScale.reducer

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.features.common.helper.ScaleDataHelper.toScaleInfo
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations
import com.dmdbrands.gurus.weight.features.common.model.ScaleInfo

/**
 * State for AddScaleScreen.
 */
data class AddScaleState(
  val form: FormGroup<AddScaleFormControls>,
  val isSubmitting: Boolean = false,
  val selectedSku: String? = null,
  val savedScales: List<ScaleInfo> = emptyList<ScaleInfo>(),
  val scaleId: String? = null,
) : IReducer.State

/**
 * Form controls for AddScaleScreen.
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
 * Intents for AddScaleScreen actions.
 */
sealed interface AddScaleIntent : IReducer.Intent {
  object ShowHelp : AddScaleIntent

  object OpenScaleChooser : AddScaleIntent

  object Submit : AddScaleIntent

  object ResetForm : AddScaleIntent

  data class SetSavedScales(
    val scales: List<Device>,
  ) : AddScaleIntent

  data class OpenSelectedScaleSetup(
    val sku: String,
  ) : AddScaleIntent

  data class OpenScaleSettings(
    val scaleId: String,
  ) : AddScaleIntent
}

/**
 * Reducer for AddScaleScreen.
 */
class AddScaleReducer : IReducer<AddScaleState, AddScaleIntent> {
  override fun reduce(
    state: AddScaleState,
    intent: AddScaleIntent,
  ): AddScaleState? =
    when (intent) {
      AddScaleIntent.ShowHelp -> state.copy()
      AddScaleIntent.Submit -> state.copy(isSubmitting = true)
      AddScaleIntent.OpenScaleChooser -> state.copy()
      is AddScaleIntent.SetSavedScales -> state.copy(savedScales = intent.scales.map { it.toScaleInfo() })
      is AddScaleIntent.OpenSelectedScaleSetup -> state.copy(selectedSku = intent.sku)
      is AddScaleIntent.OpenScaleSettings -> state.copy(scaleId = intent.scaleId)
      else -> state
    }
}
