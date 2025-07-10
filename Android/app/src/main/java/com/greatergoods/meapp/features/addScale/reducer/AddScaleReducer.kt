package com.greatergoods.meapp.features.addScale.reducer

import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.domain.model.storage.Device
import com.greatergoods.meapp.features.common.helper.ScaleDataHelper.toScaleInfo
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.common.helper.form.FormGroup
import com.greatergoods.meapp.features.common.helper.form.FormValidations
import com.greatergoods.meapp.features.common.model.ScaleInfo

/**
 * State for AddScaleScreen.
 */
data class AddScaleState(
    val form: FormGroup<AddScaleFormControls>,
    val isSubmitting: Boolean = false,
    val selectedSku: String? = null,
    val savedScales: List<ScaleInfo> = emptyList<ScaleInfo>(),
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

    data class SetSavedScales(
        val scales: List<Device>,
    ) : AddScaleIntent

    data class ScaleSelected(
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
            is AddScaleIntent.ScaleSelected -> state.copy(selectedSku = intent.sku)
            is AddScaleIntent.OpenScaleSettings -> state.copy(selectedSku = intent.scaleId)
        }
}
