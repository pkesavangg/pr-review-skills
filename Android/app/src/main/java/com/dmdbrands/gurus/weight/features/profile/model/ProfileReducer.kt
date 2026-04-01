package com.dmdbrands.gurus.weight.features.profile.model

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.features.common.components.DateTimeValue
import com.dmdbrands.gurus.weight.features.common.helper.form.AppValidatorConfig
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations
import com.dmdbrands.gurus.weight.features.login.strings.LoginStrings
import com.dmdbrands.gurus.weight.features.signup.strings.SignupStrings
import androidx.compose.runtime.Stable

/**
 * Controls for Profile form.
 */
data class ProfileFormControls(
    val firstName: FormControl<String>,
    val lastName: FormControl<String>,
    val email: FormControl<String>,
    val zipcode: FormControl<String>,
    val birthday: FormControl<DateTimeValue>,
    val gender: FormControl<String>,
    val height: FormControl<Int>,
) {
    companion object {
        fun create(
            firstName: String = "",
            lastName: String = "",
            email: String = "",
            zipcode: String = "",
            birthday: DateTimeValue = DateTimeValue.Date(
                DateTimeValue.getEpochMillisFromDateString(AppValidatorConfig.DateOfBirth.DEFAULT_VALUE)
            ),
            gender: String = "",
            height: Int = 0,
        ) = ProfileFormControls(
            firstName = FormControl.create(
              initialValue = firstName,
              validators = listOf(
                    FormValidations.required(),
                    FormValidations.noWhiteSpace(),
              FormValidations.maxLength(AppValidatorConfig.Name.MAX_LENGTH, customMessage = SignupStrings.Error.maxName),
                ),
            ),
            lastName = FormControl.create(
                initialValue = lastName,
                validators = listOf(
                    FormValidations.required(),
                    FormValidations.noWhiteSpace(),
                    FormValidations.maxLength(AppValidatorConfig.Name.MAX_LENGTH, customMessage = SignupStrings.Error.maxName),
                ),
            ),
            email = FormControl.create(
                initialValue = email,
                validators = listOf(
                    FormValidations.required(LoginStrings.Errors.emailBlank),
                    FormValidations.maxLength(AppValidatorConfig.Email.MAX_LENGTH, customMessage = LoginStrings.Errors.maxLengthEmail),
                    FormValidations.email(),
                ),
            ),
            zipcode = FormControl.create(
                initialValue = zipcode,
                validators = listOf(
                    FormValidations.required(),
                    FormValidations.noWhiteSpace(),
                    FormValidations.maxLength(AppValidatorConfig.ZipCode.MAX_LENGTH,customMessage = SignupStrings.Error.maxZipcode),
                ),
            ),
            birthday =
                FormControl.create(
                    birthday,
                    listOf(),
                ),
            gender = FormControl.create(
                initialValue = gender,
                validators = listOf(),
            ),
            height = FormControl.create(
                initialValue = height,
                validators = listOf(),
            ),
        )
    }
}

/**
 * State for Profile screen, including form group and UI state.
 * @property form The form group containing profile controls.
 * @property isLoading Whether the profile update process is ongoing.
 * @property error Error message to display, if any.
 */
@Stable
data class ProfileState(
    val form: FormGroup<ProfileFormControls>,
    val isLoading: Boolean = false,
    val error: String? = null,
    val weightUnit: WeightUnit? = null,
) : IReducer.State

/**
 * Intents for Profile screen actions.
 */
sealed class ProfileIntent : IReducer.Intent {
    /** Trigger profile update submission. */
    object Submit : ProfileIntent()

    /** Load profile data. */
    object LoadProfile : ProfileIntent()

    /** Profile data loaded successfully. */
    data class ProfileLoaded(
        val firstName: String,
        val lastName: String,
        val email: String,
        val zipcode: String,
        val birthday: DateTimeValue,
        val gender: String = "",
        val height: Int = 0,
        val weightUnit: WeightUnit? = null,
    ) : ProfileIntent()

    /** Show an error message. */
    data class Error(val message: String) : ProfileIntent()

    /** Update the form state. */
    data class UpdateForm(val form: FormGroup<ProfileFormControls>) : ProfileIntent()

    /** Profile update was successful. */
    object Success : ProfileIntent()

    /** Show biological sex selection modal. */
    object ShowBiologicalSexModal : ProfileIntent()

    /** Show height picker modal. */
    object ShowHeightModal : ProfileIntent()

    /** Request to exit/go back from profile screen. */
    object OnRequestBack : ProfileIntent()
}

/**
 * Reducer for Profile screen state transitions.
 */
class ProfileReducer : IReducer<ProfileState, ProfileIntent> {
    /**
     * Reduces the current state and intent to a new state.
     * @param state The current state.
     * @param intent The intent/action to handle.
     * @return The new state after applying the intent.
     */
    override fun reduce(state: ProfileState, intent: ProfileIntent): ProfileState =
        when (intent) {
            is ProfileIntent.Submit -> {
                state.copy(isLoading = true, error = null)
            }

            is ProfileIntent.LoadProfile -> {
                state.copy(isLoading = true, error = null)
            }

            is ProfileIntent.ProfileLoaded -> {
                val updatedForm = FormGroup(
                    ProfileFormControls.create(
                        firstName = intent.firstName,
                        lastName = intent.lastName,
                        email = intent.email,
                        zipcode = intent.zipcode,
                        birthday = intent.birthday,
                        gender = intent.gender,
                        height = intent.height,
                    )
                )
                updatedForm.validate()
                state.copy(
                    form = updatedForm,
                    isLoading = false,
                    error = null,
                    weightUnit = intent.weightUnit,
                )
            }

            is ProfileIntent.Error -> {
                state.copy(isLoading = false, error = intent.message)
            }

            is ProfileIntent.UpdateForm -> {
                state.copy(form = intent.form)
            }

            is ProfileIntent.Success -> {
                state.copy(isLoading = false, error = null)
            }

            is ProfileIntent.ShowBiologicalSexModal,
            is ProfileIntent.ShowHeightModal,
            is ProfileIntent.OnRequestBack -> {
                // No state change needed — handled as side effects in ViewModel
                state
            }
        }
}
