package com.greatergoods.meapp.features.profile.model

import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.features.common.components.DateTimeValue
import com.greatergoods.meapp.features.common.helper.form.AppValidatorConfig
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.common.helper.form.FormGroup
import com.greatergoods.meapp.features.common.helper.form.FormValidations
import com.greatergoods.meapp.features.profile.strings.ProfileStrings

/**
 * Controls for Profile form.
 */
data class ProfileFormControls(
    val firstName: FormControl<String>,
    val lastName: FormControl<String>,
    val email: FormControl<String>,
    val zipcode: FormControl<String>,
    val birthday: FormControl<DateTimeValue>,
) {
    companion object {
        fun create(
            firstName: String = "",
            lastName: String = "",
            email: String = "",
            zipcode: String = "",
            birthday: DateTimeValue = DateTimeValue.Date(
                DateTimeValue.getEpochMillisFromDateString(AppValidatorConfig.DateOfBirth.DEFAULT_VALUE)
            )
        ) = ProfileFormControls(
            firstName = FormControl.create(
                initialValue = firstName,
                validators = listOf(
                    FormValidations.required(),
                    FormValidations.maxLength(50, ProfileStrings.FirstNameLabel),
                ),
            ),
            lastName = FormControl.create(
                initialValue = lastName,
                validators = listOf(
                    FormValidations.required(),
                    FormValidations.maxLength(50, ProfileStrings.LastNameLabel),
                ),
            ),
            email = FormControl.create(
                initialValue = email,
                validators = listOf(
                    FormValidations.required(),
                    FormValidations.maxLength(100, ProfileStrings.EmailLabel),
                    FormValidations.email(),
                ),
            ),
            zipcode = FormControl.create(
                initialValue = zipcode,
                validators = listOf(
                    FormValidations.required(),
                    FormValidations.maxLength(10, ProfileStrings.ZipcodeLabel),
                ),
            ),
            birthday =
                FormControl.create(
                    birthday,
                    listOf(),
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
data class ProfileState(
    val form: FormGroup<ProfileFormControls>,
    val isLoading: Boolean = false,
    val error: String? = null,
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
        val birthday: DateTimeValue
    ) : ProfileIntent()

    /** Show an error message. */
    data class Error(val message: String) : ProfileIntent()

    /** Update the form state. */
    data class UpdateForm(val form: FormGroup<ProfileFormControls>) : ProfileIntent()

    /** Profile update was successful. */
    object Success : ProfileIntent()

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
                        birthday = intent.birthday
                    )
                )
                state.copy(
                    form = updatedForm,
                    isLoading = false,
                    error = null
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

            is ProfileIntent.OnRequestBack -> {
                // No state change needed for back navigation
                state
            }
        }
}
