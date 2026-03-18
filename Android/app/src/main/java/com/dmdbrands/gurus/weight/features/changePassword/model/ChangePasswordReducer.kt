package com.dmdbrands.gurus.weight.features.changePassword.model

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.features.changePassword.strings.ChangePasswordStrings
import com.dmdbrands.gurus.weight.features.common.helper.form.AppValidatorConfig
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations
import com.dmdbrands.gurus.weight.features.common.helper.form.ValidationError
import com.dmdbrands.gurus.weight.features.common.helper.form.ValidationMessages
import com.dmdbrands.gurus.weight.features.common.helper.form.ValidationType
import com.dmdbrands.gurus.weight.features.common.helper.form.Validator
import androidx.compose.runtime.Stable

/**
 * Controls for Change Password form.
 */
data class ChangePasswordFormControls(
    val currentPassword: FormControl<String>,
    val newPassword: FormControl<String>,
    val confirmPassword: FormControl<String>,
) {
    companion object {
        /**
         * Creates confirm password validator for password matching
         * Only the confirm password field should show mismatch errors
         */
        private fun createConfirmPasswordValidator(
            formGroup: () -> FormGroup<ChangePasswordFormControls>,
        ): Validator<String> =
            { confirmPasswordValue ->
                val form = formGroup()
                val newPasswordValue = form.controls.newPassword.value

                // Only show mismatch error if both fields have values and they don't match
                if (confirmPasswordValue.isNotEmpty() &&
                    newPasswordValue.isNotEmpty() &&
                    confirmPasswordValue != newPasswordValue
                ) {
                    ValidationError(
                        ValidationType.MATCH_PASSWORD,
                        ValidationMessages.PASSWORD_MISMATCH,
                    )
                } else {
                    null
                }
            }

        /**
         * Creates a validator to ensure new password is different from current password
         */
        private fun createNewPasswordValidator(
            formGroup: () -> FormGroup<ChangePasswordFormControls>,
        ): Validator<String> =
            { value ->
                val currentPassword = formGroup().controls.currentPassword.value
                if (value.isNotEmpty() && currentPassword.isNotEmpty() && value == currentPassword) {
                    ValidationError(
                        ValidationType.NOT_SAME,
                        "New password must be different from old password",
                    )
                } else {
                    null
                }
            }

        /**
         * Creates a new instance of ChangePasswordFormControls with default values and validations.
         */
        fun create(): ChangePasswordFormControls {
            val controls =
                ChangePasswordFormControls(
                    currentPassword =
                        FormControl.create(
                            initialValue = "",
                            validators =
                                listOf(
                                    FormValidations.required(ChangePasswordStrings.PasswordMin),
                                    FormValidations.minLength(
                                        AppValidatorConfig.Password.MIN_LENGTH,
                                        customMessage = ChangePasswordStrings.PasswordMin,
                                        allowSpaces = true,
                                    ),
                                    FormValidations.maxLength(
                                        AppValidatorConfig.Password.MAX_LENGTH,
                                        customMessage = ChangePasswordStrings.PasswordMax,
                                        allowSpaces = true,
                                    ),
                                ),
                        ),
                    newPassword =
                        FormControl.create(
                            initialValue = "",
                            validators =
                                listOf(
                                    FormValidations.required(ChangePasswordStrings.PasswordMin),
                                    FormValidations.minLength(
                                      AppValidatorConfig.Password.MIN_LENGTH,
                                      customMessage = ChangePasswordStrings.PasswordMin,
                                      allowSpaces = true,
                                    ),
                                    FormValidations.maxLength(
                                      AppValidatorConfig.Password.MAX_LENGTH,
                                      customMessage = ChangePasswordStrings.PasswordMax,
                                      allowSpaces = true,
                                    ),
                                ),
                        ),
                    confirmPassword =
                        FormControl.create(
                            initialValue = "",
                            validators =
                                listOf(
                                    FormValidations.required(ChangePasswordStrings.PasswordMin),
                                    FormValidations.minLength(
                                      AppValidatorConfig.Password.MIN_LENGTH,
                                      customMessage = ChangePasswordStrings.PasswordMin,
                                      allowSpaces = true,
                                    ),
                                    FormValidations.maxLength(
                                      AppValidatorConfig.Password.MAX_LENGTH,
                                      customMessage = ChangePasswordStrings.PasswordMax,
                                      allowSpaces = true,
                                    ),
                                ),
                        ),
                )

            val formGroup = FormGroup(controls)

            // Add password matching validation only to confirm password field
            controls.confirmPassword.addValidator(createConfirmPasswordValidator { formGroup })

            // Add validation to ensure new password is different from current password
            controls.newPassword.addValidator(createNewPasswordValidator { formGroup })

            // Set up validation trigger - when new password changes, validate confirm password
            controls.newPassword.onValueChangeListener { _, _ ->
                // When new password changes, trigger validation on confirm password if it has a value
                if (controls.confirmPassword.value.isNotEmpty()) {
                    controls.confirmPassword.validate()
                }
            }

            // Set up validation trigger - when current password changes, validate new password
            controls.currentPassword.onValueChangeListener { _, _ ->
                // When current password changes, trigger validation on new password if it has a value
                if (controls.newPassword.value.isNotEmpty()) {
                    controls.newPassword.validate()
                }
            }

            return controls
        }
    }
}

/**
 * State for Change Password screen, including form group and UI state.
 * @property form The form group containing change password controls.
 * @property isLoading Whether the change password process is ongoing.
 * @property error Error message to display, if any.
 */
@Stable
data class ChangePasswordState(
    val form: FormGroup<ChangePasswordFormControls>,
    val isLoading: Boolean = false,
    val error: String? = null,
) : IReducer.State

/**
 * Intents for Change Password screen actions.
 */
sealed class ChangePasswordIntent : IReducer.Intent {
    /** Trigger change password submission. */
    object Submit : ChangePasswordIntent()

    /** Open forgot password modal. */
    object OpenForgotPasswordModal : ChangePasswordIntent()

    /** Open help modal. */
    object OpenHelpModal : ChangePasswordIntent()

    /** Request to go back with confirmation if form has changes. */
    object OnRequestBack : ChangePasswordIntent()

    /** Show an error message. */
    data class Error(
        val message: String,
    ) : ChangePasswordIntent()

    /** Change password was successful. */
    object Success : ChangePasswordIntent()
}

/**
 * Reducer for Change Password screen state transitions.
 */
class ChangePasswordReducer : IReducer<ChangePasswordState, ChangePasswordIntent> {
    /**
     * Reduces the current state and intent to a new state.
     * @param state The current state.
     * @param intent The intent/action to handle.
     * @return The new state after applying the intent.
     */
    override fun reduce(
        state: ChangePasswordState,
        intent: ChangePasswordIntent,
    ): ChangePasswordState =
        when (intent) {
            is ChangePasswordIntent.Submit -> {
                state.copy(isLoading = true, error = null)
            }

            is ChangePasswordIntent.OpenForgotPasswordModal -> {
                state.copy(isLoading = false, error = null)
            }

            is ChangePasswordIntent.OpenHelpModal -> {
                state.copy(isLoading = false, error = null)
            }

            is ChangePasswordIntent.OnRequestBack -> {
                state.copy(isLoading = false, error = null)
            }

            is ChangePasswordIntent.Error -> {
                state.copy(isLoading = false, error = intent.message)
            }

            is ChangePasswordIntent.Success -> {
                state.copy(isLoading = false, error = null)
            }
        }
}
