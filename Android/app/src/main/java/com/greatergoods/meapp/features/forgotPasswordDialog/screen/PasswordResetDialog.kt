package com.greatergoods.meapp.features.forgotPasswordDialog.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import com.greatergoods.meapp.features.common.components.AppInput
import com.greatergoods.meapp.features.common.components.AppInputType
import com.greatergoods.meapp.features.common.components.BaseModal
import com.greatergoods.meapp.features.common.model.ActionButton
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.login.strings.LoginStrings
import com.greatergoods.meapp.theme.MeAppTheme
import androidx.compose.ui.tooling.preview.Preview
import android.content.res.Configuration
import com.greatergoods.meapp.features.common.helper.form.FormValidations
import com.greatergoods.meapp.features.common.components.PreviewTheme

/**
 * Password Reset Dialog composable using BaseModal for consistent dialog styling.
 * @param emailControl The FormControl for the email input.
 * @param onEmailChange Callback for email value change.
 * @param onSubmit Called when submit is pressed or IME action is triggered.
 * @param onCancel Called when cancel is pressed.
 * @param isSubmitEnabled Whether the submit button is enabled.
 * @param modifier Modifier for the dialog.
 */
@Composable
fun PasswordResetModal(
    emailControl: FormControl<String>,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    isSubmitEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    BaseModal(
        title = LoginStrings.ForgotPasswordDialogStrings.Title,
        body = LoginStrings.ForgotPasswordDialogStrings.Subtitle,
        primaryAction = ActionButton(
            text = LoginStrings.ForgotPasswordDialogStrings.SubmitButton,
            action = onSubmit,
            enabled = isSubmitEnabled
        ),
        secondaryAction = ActionButton(
            text = LoginStrings.ForgotPasswordDialogStrings.CancelButton,
            action = onCancel
        ),
        onDismiss = onCancel,
        modifier = modifier
    ) {
        AppInput(
            formControl = emailControl,
            label = LoginStrings.ForgotPasswordDialogStrings.EmailLabel,
            type = AppInputType.EMAIL,
            imeAction = ImeAction.Done,
            onImeAction = onSubmit,
            modifier = Modifier
        )
    }
}

@PreviewTheme
@Composable
fun PasswordResetModalPreview() {
    MeAppTheme {
        val emailControl = remember {
            FormControl.create(
                initialValue = "",
                validators = listOf(
                    com.greatergoods.meapp.features.common.helper.form.FormValidations.required(),
                    com.greatergoods.meapp.features.common.helper.form.FormValidations.email()
                )
            )
        }
        PasswordResetModal(
            emailControl = emailControl,
            onSubmit = {},
            onCancel = {},
            isSubmitEnabled = false
        )
    }
} 