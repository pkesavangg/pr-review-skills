package com.greatergoods.meapp.features.weightless.model

import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.common.helper.form.FormGroup
import com.greatergoods.meapp.features.common.helper.form.FormValidations

/**
 * Controls for Weightless form.
 */
data class WeightlessFormControls(
    val weightlessWeight: FormControl<String>,
) {
    companion object {
        fun create() = WeightlessFormControls(
            weightlessWeight = FormControl.create(
                initialValue = "0.0",
                validators = listOf(
                    FormValidations.required(),
                    FormValidations.weightValidator(),
                ),
            ),
        )
    }
}

/**
 * State for Weightless screen, including form group and UI state.
 * @property form The form group containing weightless controls.
 * @property isWeightlessOn The toggle state for weightless mode (managed separately from form).
 * @property hasToggleChanged Whether the toggle state has changed from initial value.
 * @property isLoading Whether the weightless update process is ongoing.
 * @property error Error message to display, if any.
 * @property weightUnit The weight unit to display (kg or lbs).
 * @property isMetric Whether the user prefers metric units.
 */
data class WeightlessState(
    val form: FormGroup<WeightlessFormControls>,
    val isWeightlessOn: Boolean = false,
    val hasToggleChanged: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val weightUnit: String = "lbs",
    val isMetric: Boolean = false,
) : IReducer.State

/**
 * Intents for Weightless screen actions.
 */
sealed class WeightlessIntent : IReducer.Intent {
    /** Trigger weightless form submission. */
    object Submit : WeightlessIntent()

    /** Toggle weightless mode on/off. */
    object ToggleWeightless : WeightlessIntent()

    /** Open help modal. */
    object OpenHelpModal : WeightlessIntent()

    /** Navigate back. */
    object OnBack : WeightlessIntent()

    /** Show an error message. */
    data class Error(
        val message: String,
    ) : WeightlessIntent()

    /** Update the form state. */
    data class UpdateForm(
        val form: FormGroup<WeightlessFormControls>,
    ) : WeightlessIntent()

    /** Weightless update was successful. */
    object Success : WeightlessIntent()
}

/**
 * Reducer for Weightless screen state transitions.
 */
class WeightlessReducer : IReducer<WeightlessState, WeightlessIntent> {
    /**
     * Reduces the current state and intent to a new state.
     * @param state The current state.
     * @param intent The intent/action to handle.
     * @return The new state after applying the intent.
     */
    override fun reduce(
        state: WeightlessState,
        intent: WeightlessIntent,
    ): WeightlessState =
        when (intent) {
            is WeightlessIntent.Submit -> {
                state.copy(isLoading = true, error = null)
            }

            is WeightlessIntent.ToggleWeightless -> {
                val newToggleValue = !state.isWeightlessOn

                // Update weight control validators based on toggle state
                val weightValidators = if (newToggleValue) {
                    listOf(FormValidations.required(), FormValidations.weightValidator())
                } else {
                    listOf(FormValidations.weightValidator())
                }
                val updatedWeightControl = FormControl.create(
                    initialValue = state.form.controls.weightlessWeight.value,
                    validators = weightValidators,
                )

                val updatedControls = state.form.controls.copy(
                    weightlessWeight = updatedWeightControl,
                )
                val updatedForm = FormGroup(updatedControls)
                state.copy(
                    form = updatedForm,
                    isWeightlessOn = newToggleValue,
                    hasToggleChanged = true,
                )
            }

            is WeightlessIntent.OpenHelpModal -> {
                state.copy(isLoading = false, error = null)
            }

            is WeightlessIntent.Error -> {
                state.copy(isLoading = false, error = intent.message)
            }

            is WeightlessIntent.UpdateForm -> {
                state.copy(form = intent.form)
            }

            is WeightlessIntent.Success -> {
                state.copy(isLoading = false, error = null)
            }

            is WeightlessIntent.OnBack -> {
                state.copy(isLoading = false, error = null)
            }
        }
}
